package com.fieldtripops.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.fieldtripops.databinding.FragmentSlaConfigBinding
import com.fieldtripops.domain.model.Role
import com.fieldtripops.ui.util.AuthorizedFragment
import com.fieldtripops.ui.util.textString
import org.koin.androidx.viewmodel.ext.android.viewModel

class SlaConfigFragment : AuthorizedFragment() {

    override val requiredRoles: Set<Role> = setOf(Role.Administrator)

    private var _binding: FragmentSlaConfigBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SlaConfigViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSlaConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!enforceAuthorization(view)) return

        binding.saveButton.setOnClickListener {
            val first = binding.firstResponseInput.textString().toIntOrNull() ?: 0
            val resolution = binding.resolutionInput.textString().toIntOrNull() ?: 0
            val noResp = binding.noResponseInput.textString().toIntOrNull() ?: 0
            val workStart = binding.workDayStartInput.textString().toIntOrNull() ?: 9
            val workEnd = binding.workDayEndInput.textString().toIntOrNull() ?: 17
            val excludeWeekends = binding.excludeWeekendsSwitch.isChecked
            viewModel.save(first, resolution, noResp, workStart, workEnd, excludeWeekends)
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is SlaConfigViewModel.State.Loaded -> {
                    binding.firstResponseInput.setText(state.config.firstResponseMinutes.toString())
                    binding.resolutionInput.setText(state.config.resolutionMinutes.toString())
                    binding.noResponseInput.setText(state.config.travelerNoResponseHours.toString())
                    binding.workDayStartInput.setText(state.config.workDayStartHour.toString())
                    binding.workDayEndInput.setText(state.config.workDayEndHour.toString())
                    binding.excludeWeekendsSwitch.isChecked = state.config.excludeWeekends
                    binding.messageText.text = "Loaded current SLA"
                }
                is SlaConfigViewModel.State.Saved -> {
                    binding.messageText.text = "Saved at ${state.config.updatedAt}"
                }
                is SlaConfigViewModel.State.Error -> {
                    binding.messageText.text = state.message
                }
                null -> {}
            }
        }

        viewModel.load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
