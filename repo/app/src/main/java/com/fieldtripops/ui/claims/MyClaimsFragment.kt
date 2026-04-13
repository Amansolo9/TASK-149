package com.fieldtripops.ui.claims

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fieldtripops.R
import com.fieldtripops.databinding.FragmentMyClaimsBinding
import com.fieldtripops.domain.model.TicketState
import com.fieldtripops.ui.util.gone
import com.fieldtripops.ui.util.visible
import org.koin.androidx.viewmodel.ext.android.viewModel

class MyClaimsFragment : Fragment() {

    private var _binding: FragmentMyClaimsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MyClaimsViewModel by viewModel()
    private lateinit var adapter: ClaimListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyClaimsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ClaimListAdapter { ticket ->
            // Offer appeal from terminal non-closed states (Resolved / Rejected).
            if (ticket.state == TicketState.Resolved || ticket.state == TicketState.Rejected) {
                promptAppeal(ticket.id, ticket.state.name)
            }
        }
        binding.claimsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.claimsRecycler.adapter = adapter

        binding.fileClaimButton.setOnClickListener {
            findNavController().navigate(R.id.action_myClaims_to_fileClaim)
        }

        viewModel.tickets.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            if (list.isEmpty()) binding.emptyText.visible() else binding.emptyText.gone()
        }
        viewModel.message.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                viewModel.clearMessage()
            }
        }
    }

    private fun promptAppeal(ticketId: String, fromState: String) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            hint = "Reason for appeal"
            minLines = 2
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Appeal ticket from $fromState")
            .setView(input)
            .setPositiveButton("File Appeal") { _, _ ->
                val reason = input.text.toString().trim()
                viewModel.fileAppeal(ticketId, reason)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
