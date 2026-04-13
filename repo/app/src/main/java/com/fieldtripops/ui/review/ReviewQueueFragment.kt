package com.fieldtripops.ui.review

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.fieldtripops.databinding.FragmentReviewQueueBinding
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.model.TicketState
import com.fieldtripops.ui.claims.ClaimListAdapter
import com.fieldtripops.ui.util.AuthorizedFragment
import com.fieldtripops.ui.util.gone
import com.fieldtripops.ui.util.visible
import org.koin.androidx.viewmodel.ext.android.viewModel

class ReviewQueueFragment : AuthorizedFragment() {

    override val requiredRoles: Set<Role> = setOf(Role.Reviewer, Role.Administrator)

    private var _binding: FragmentReviewQueueBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReviewQueueViewModel by viewModel()
    private lateinit var adapter: ClaimListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReviewQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Route-level guard. Unauthorized users are navigated back BEFORE we
        // attach adapters or ask the viewmodel to load data.
        if (!enforceAuthorization(view)) return

        adapter = ClaimListAdapter { ticket ->
            val targets = TicketState.allowedNextStates(ticket.state)
                .filter { it != TicketState.Cancelled }
            if (targets.isEmpty()) {
                Toast.makeText(requireContext(), "No transitions available", Toast.LENGTH_SHORT).show()
                return@ClaimListAdapter
            }
            val labels = targets.map { it.name }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Move ticket from ${ticket.state.name}")
                .setItems(labels) { _, which ->
                    viewModel.moveTo(ticket.id, targets[which], "Reviewer action")
                }
                .show()
        }
        binding.queueRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.queueRecycler.adapter = adapter

        viewModel.queue.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.summaryText.text = "${list.size} ticket(s) in queue"
            if (list.isEmpty()) binding.emptyText.visible() else binding.emptyText.gone()
        }
        viewModel.message.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAuthorized()) viewModel.load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
