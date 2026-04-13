package com.fieldtripops.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.fieldtripops.databinding.FragmentDeletionRequestsBinding
import com.fieldtripops.domain.model.DeletionScope
import com.fieldtripops.domain.model.Role
import com.fieldtripops.ui.util.AuthorizedFragment
import com.fieldtripops.ui.util.textString
import org.koin.androidx.viewmodel.ext.android.viewModel

class DeletionRequestsFragment : AuthorizedFragment() {

    override val requiredRoles: Set<Role> = setOf(Role.Administrator)

    private var _binding: FragmentDeletionRequestsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DeletionRequestsViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeletionRequestsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!enforceAuthorization(view)) return

        binding.queueAnonButton.setOnClickListener {
            viewModel.requestOnBehalf(
                targetUserId = binding.targetUserInput.textString(),
                reason = binding.reasonInput.textString().ifBlank { null },
                scope = DeletionScope.ANONYMIZE
            )
        }
        binding.queueHardButton.setOnClickListener {
            viewModel.requestOnBehalf(
                targetUserId = binding.targetUserInput.textString(),
                reason = binding.reasonInput.textString().ifBlank { null },
                scope = DeletionScope.HARD_DELETE
            )
        }
        binding.approveButton.setOnClickListener {
            val id = binding.requestIdInput.textString()
            if (id.isNotBlank()) viewModel.approveAndExecute(id)
        }
        binding.rejectButton.setOnClickListener {
            val id = binding.requestIdInput.textString()
            val reason = binding.reasonInput.textString().ifBlank { "rejected by admin" }
            if (id.isNotBlank()) viewModel.reject(id, reason)
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is DeletionRequestsViewModel.State.Loaded -> {
                    binding.listText.text = state.items.joinToString("\n") {
                        "${it.id} target=${it.targetUserId} state=${it.state.name} scope=${it.scope.name}"
                    }.ifBlank { "(none)" }
                }
                is DeletionRequestsViewModel.State.Message ->
                    binding.messageText.text = state.text
                is DeletionRequestsViewModel.State.Error ->
                    binding.messageText.text = "Error: ${state.text}"
                null -> {}
            }
        }
        viewModel.loadAll()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
