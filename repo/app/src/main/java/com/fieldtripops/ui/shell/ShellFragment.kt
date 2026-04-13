package com.fieldtripops.ui.shell

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fieldtripops.R
import com.fieldtripops.databinding.FragmentShellBinding
import com.fieldtripops.domain.model.BookingState
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.domain.usecase.RequestUserDeletionUseCase
import com.fieldtripops.security.auth.SessionManager
import com.fieldtripops.ui.booking.BookingConfirmFragment
import com.fieldtripops.ui.util.gone
import com.fieldtripops.ui.util.visible
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Role-aware dashboard. Cards for Traveler / Agent / Reviewer / Administrator are
 * conditionally shown based on the authenticated session's roles. Each card
 * navigates to the relevant flow. Defense-in-depth: navigation guards here are
 * UX, but the destination use cases enforce role/ownership independently.
 */
class ShellFragment : Fragment() {

    private var _binding: FragmentShellBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ShellViewModel by viewModel()
    private val bookingRepository: BookingRepository by inject()
    private val sessionManager: SessionManager by inject()
    private val requestDeletionUseCase: RequestUserDeletionUseCase by inject()

    private lateinit var adapter: BookingListAdapter
    private var agentAdapter: BookingListAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShellBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val session = sessionManager.current()
        if (session == null) {
            navigateToLogin()
            return
        }
        viewModel.initialize()

        adapter = BookingListAdapter(
            onClick = { order ->
                if (order.state == BookingState.PendingConfirmation) {
                    val args = Bundle().apply {
                        putString(BookingConfirmFragment.ARG_ORDER_ID, order.id)
                    }
                    findNavController().navigate(R.id.action_shell_to_bookingConfirm, args)
                }
            },
            onLongClick = { order ->
                if (order.state == BookingState.Booked) {
                    com.fieldtripops.ui.booking.RescheduleDialog
                        .newInstance(order.id)
                        .show(childFragmentManager, "reschedule")
                    true
                } else false
            }
        )
        binding.bookingsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.bookingsRecycler.adapter = adapter

        // Role-gated card visibility
        binding.travelerCard.visibility = if (session.hasRole(Role.Traveler)) View.VISIBLE else View.GONE
        binding.reviewerCard.visibility = if (session.hasRole(Role.Reviewer)) View.VISIBLE else View.GONE
        binding.adminCard.visibility = if (session.hasRole(Role.Administrator)) View.VISIBLE else View.GONE
        binding.reportsCard.visibility = if (
            session.hasAnyRole(Role.Reviewer, Role.Administrator)
        ) View.VISIBLE else View.GONE

        // Agent card: visible for Agent or Administrator roles
        val isAgentOrAdmin = session.hasAnyRole(Role.Agent, Role.Administrator)
        binding.agentCard.visibility = if (isAgentOrAdmin) View.VISIBLE else View.GONE

        if (isAgentOrAdmin) {
            agentAdapter = BookingListAdapter(
                onClick = { order ->
                    val args = Bundle().apply {
                        putString(BookingConfirmFragment.ARG_ORDER_ID, order.id)
                    }
                    findNavController().navigate(R.id.action_shell_to_bookingConfirm, args)
                },
                onLongClick = { false }
            )
            binding.agentQueueRecycler.layoutManager = LinearLayoutManager(requireContext())
            binding.agentQueueRecycler.adapter = agentAdapter
        }

        binding.newItineraryButton.setOnClickListener {
            findNavController().navigate(R.id.action_shell_to_wizard)
        }
        binding.myClaimsButton.setOnClickListener {
            findNavController().navigate(R.id.action_shell_to_myClaims)
        }
        binding.reviewQueueButton.setOnClickListener {
            findNavController().navigate(R.id.action_shell_to_reviewQueue)
        }
        binding.quarantineButton.setOnClickListener {
            findNavController().navigate(R.id.action_shell_to_quarantine)
        }
        binding.consentButton.setOnClickListener {
            findNavController().navigate(R.id.action_shell_to_consent)
        }
        binding.slaConfigButton.setOnClickListener {
            findNavController().navigate(R.id.action_shell_to_slaConfig)
        }
        binding.deletionQueueButton.setOnClickListener {
            findNavController().navigate(R.id.action_shell_to_deletionRequests)
        }
        binding.requestDeletionButton.setOnClickListener {
            val userId = sessionManager.current()?.userId ?: return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val result = requestDeletionUseCase.execute(
                        targetUserId = userId,
                        reason = "self-request via dashboard",
                        scope = com.fieldtripops.domain.model.DeletionScope.ANONYMIZE
                    )
                    val msg = when (result) {
                        is RequestUserDeletionUseCase.Result.Queued ->
                            "Deletion request queued (${result.request.id}). Pending admin approval."
                        is RequestUserDeletionUseCase.Result.AlreadyPending ->
                            "A deletion request is already pending for you."
                        is RequestUserDeletionUseCase.Result.Invalid ->
                            "Invalid: ${result.reason}"
                    }
                    binding.welcomeText.append("  |  $msg")
                } catch (e: SecurityException) {
                    binding.welcomeText.append("  |  Not authorized")
                }
            }
        }
        binding.reportsButton.setOnClickListener {
            findNavController().navigate(R.id.action_shell_to_reports)
        }
        binding.logoutButton.setOnClickListener { viewModel.logout() }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ShellState.Loading -> binding.welcomeText.text = getString(R.string.loading)
                is ShellState.Active -> {
                    val u = state.user
                    binding.welcomeText.text = getString(R.string.welcome_message, u.displayName)
                    binding.roleText.text = getString(
                        R.string.role_label, u.roles.joinToString(", ") { it.name }
                    )
                    loadBookings(u.id)
                    if (isAgentOrAdmin) loadAgentQueue()
                }
                is ShellState.SessionExpired -> navigateToLogin()
                is ShellState.LoggedOut -> navigateToLogin()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val session = sessionManager.current() ?: return
        loadBookings(session.userId)
        if (session.hasAnyRole(Role.Agent, Role.Administrator)) {
            loadAgentQueue()
        }
    }

    private fun loadBookings(userId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val orders = bookingRepository.findByTraveler(userId)
            adapter.submitList(orders)
            if (orders.isEmpty()) binding.emptyStateText.visible() else binding.emptyStateText.gone()
        }
    }

    private fun loadAgentQueue() {
        viewLifecycleOwner.lifecycleScope.launch {
            val pending = bookingRepository.findByState(BookingState.PendingConfirmation)
            agentAdapter?.submitList(pending)
            if (pending.isEmpty()) {
                binding.agentEmptyText.visible()
            } else {
                binding.agentEmptyText.gone()
            }
        }
    }

    private fun navigateToLogin() {
        findNavController().navigate(R.id.action_shell_to_login)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
