package com.fieldtripops.ui.itinerary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.fieldtripops.databinding.FragmentItineraryWizardBinding
import com.fieldtripops.domain.model.InventorySlot
import com.fieldtripops.ui.util.gone
import com.fieldtripops.ui.util.textString
import com.fieldtripops.ui.util.visible
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.time.format.DateTimeFormatter

class ItineraryWizardFragment : Fragment() {

    private var _binding: FragmentItineraryWizardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ItineraryWizardViewModel by viewModel()

    private val dateFmt = DateTimeFormatter.ofPattern("MM/dd/yyyy")
    private var availableSlots: List<InventorySlot> = emptyList()
    private var selectedSlotId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentItineraryWizardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.backButton.setOnClickListener {
            syncFormFromUi()
            if (!viewModel.goBack()) findNavController().popBackStack()
        }

        binding.nextButton.setOnClickListener {
            syncFormFromUi()
            val form = viewModel.form.value ?: return@setOnClickListener
            if (form.step < form.totalSteps) viewModel.goToNext()
        }

        binding.saveDraftButton.setOnClickListener {
            syncFormFromUi()
            viewModel.saveDraft()
        }

        binding.submitBookingButton.setOnClickListener {
            syncFormFromUi()
            val slotId = selectedSlotId
            if (slotId == null) {
                binding.errorsText.text = "Pick an available slot to submit."
                binding.errorsText.visible()
                return@setOnClickListener
            }
            viewModel.submitBooking(slotId)
        }

        viewModel.form.observe(viewLifecycleOwner) { renderStep(it) }

        viewModel.availability.observe(viewLifecycleOwner) { slots ->
            availableSlots = slots
            renderAvailability(slots)
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ItineraryWizardState.Editing -> binding.errorsText.gone()
                is ItineraryWizardState.ValidationError -> {
                    binding.errorsText.text = state.errors.joinToString("\n• ", prefix = "• ")
                    binding.errorsText.visible()
                }
                is ItineraryWizardState.Saved -> findNavController().popBackStack()
                is ItineraryWizardState.BookingSubmitted -> findNavController().popBackStack()
                is ItineraryWizardState.SubmissionError -> {
                    binding.errorsText.text = state.message
                    binding.errorsText.visible()
                    // Refresh availability — the sold-out path may now show a
                    // lower seat count or none remaining.
                    viewModel.loadAvailability()
                }
            }
        }
    }

    private fun renderStep(form: ItineraryFormState) {
        binding.stepIndicatorText.text = "Step ${form.step} of ${form.totalSteps}"
        binding.step1Container.gone()
        binding.step2Container.gone()
        binding.step3Container.gone()

        when (form.step) {
            1 -> {
                binding.stepTitleText.text = "Itinerary Basics"
                binding.step1Container.visible()
                binding.initialsInput.setText(form.initials)
                binding.partySizeInput.setText(form.partySize)
                binding.typeInput.setText(form.itineraryType)
                binding.nextButton.visibility = View.VISIBLE
                binding.nextButton.text = "Next"
            }
            2 -> {
                binding.stepTitleText.text = "Travel Dates"
                binding.step2Container.visible()
                binding.startDateInput.setText(form.startDateInput)
                binding.endDateInput.setText(form.endDateInput)
                binding.nextButton.visibility = View.VISIBLE
                binding.nextButton.text = "Next"
            }
            3 -> {
                binding.stepTitleText.text = "Review & Book"
                binding.step3Container.visible()
                binding.notesInput.setText(form.notes)
                binding.reviewSummaryText.text = buildString {
                    appendLine("Initials: ${form.initials}")
                    appendLine("Party: ${form.partySize}")
                    appendLine("Type: ${form.itineraryType}")
                    appendLine("Start: ${form.startDateInput}")
                    append("End: ${form.endDateInput}")
                }
                // Hide the inline Next; the review step uses Submit Booking / Save Draft buttons.
                binding.nextButton.visibility = View.GONE
            }
        }
    }

    private fun renderAvailability(slots: List<InventorySlot>) {
        binding.availabilityGroup.removeAllViews()
        selectedSlotId = null
        if (slots.isEmpty()) {
            binding.availabilityEmpty.visible()
            binding.submitBookingButton.isEnabled = false
            return
        }
        binding.availabilityEmpty.gone()
        binding.submitBookingButton.isEnabled = true

        slots.forEachIndexed { index, slot ->
            val rb = RadioButton(requireContext()).apply {
                id = View.generateViewId()
                text = buildString {
                    append(slot.serviceName)
                    append(" · ")
                    append(dateFmt.format(slot.startDate))
                    append(" → ")
                    append(dateFmt.format(slot.endDate))
                    append(" · ")
                    val remaining = slot.availableCount
                    if (remaining == 0) append("SOLD OUT")
                    else append("$remaining seat${if (remaining == 1) "" else "s"} remaining")
                }
                isEnabled = slot.availableCount > 0
            }
            binding.availabilityGroup.addView(rb)
            rb.setOnClickListener { selectedSlotId = slot.id }
            if (index == 0 && slot.availableCount > 0) {
                rb.isChecked = true
                selectedSlotId = slot.id
            }
        }
    }

    private fun syncFormFromUi() {
        viewModel.updateForm {
            when (step) {
                1 -> copy(
                    initials = binding.initialsInput.textString(),
                    partySize = binding.partySizeInput.textString(),
                    itineraryType = binding.typeInput.textString()
                )
                2 -> copy(
                    startDateInput = binding.startDateInput.textString(),
                    endDateInput = binding.endDateInput.textString()
                )
                3 -> copy(notes = binding.notesInput.textString())
                else -> this
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
