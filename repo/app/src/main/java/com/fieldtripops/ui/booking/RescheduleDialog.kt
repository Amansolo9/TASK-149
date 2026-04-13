package com.fieldtripops.ui.booking

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.fieldtripops.databinding.DialogRescheduleBinding
import com.fieldtripops.domain.booking.ItineraryValidator
import com.fieldtripops.domain.usecase.RequestRescheduleUseCase
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Reschedule entry point. Opened by long-pressing a Booked order in the shell
 * list. Hands off to `RequestRescheduleUseCase`, which enforces the 24-hour
 * lead time, role rules, and transactional persistence.
 */
class RescheduleDialog : DialogFragment() {

    private val rescheduleUseCase: RequestRescheduleUseCase by inject()

    companion object {
        private const val ARG_ORDER_ID = "orderId"
        fun newInstance(orderId: String) = RescheduleDialog().apply {
            arguments = Bundle().apply { putString(ARG_ORDER_ID, orderId) }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val orderId = requireArguments().getString(ARG_ORDER_ID)!!
        val binding = DialogRescheduleBinding.inflate(layoutInflater)
        binding.subtitleText.text = "Booking ${orderId.take(8)}…"

        return AlertDialog.Builder(requireContext())
            .setTitle("Reschedule Booking")
            .setView(binding.root)
            .setPositiveButton("Submit", null)
            .setNegativeButton("Cancel", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val newStart = ItineraryValidator.parseInputDate(
                            binding.newStartInput.text.toString()
                        )
                        val newEnd = ItineraryValidator.parseInputDate(
                            binding.newEndInput.text.toString()
                        )
                        if (newStart == null || newEnd == null) {
                            toast("Dates must be MM/DD/YYYY")
                            return@setOnClickListener
                        }
                        if (newEnd.isBefore(newStart)) {
                            toast("End must be on or after start")
                            return@setOnClickListener
                        }
                        val reason = binding.exceptionInput.text?.toString()?.trim()?.ifBlank { null }
                        lifecycleScope.launch {
                            try {
                                val r = rescheduleUseCase.execute(
                                    bookingOrderId = orderId,
                                    newStart = newStart, newEnd = newEnd,
                                    exceptionReason = reason
                                )
                                when (r) {
                                    is RequestRescheduleUseCase.Result.Requested -> {
                                        toast("Reschedule submitted")
                                        dismiss()
                                    }
                                    is RequestRescheduleUseCase.Result.BookingNotFound ->
                                        toast("Booking not found")
                                    is RequestRescheduleUseCase.Result.InvalidBookingState ->
                                        toast("Only booked orders can be rescheduled")
                                    is RequestRescheduleUseCase.Result.ExceptionReasonRequired ->
                                        toast("Under 24h — exception reason required")
                                    is RequestRescheduleUseCase.Result.ValidationFailed ->
                                        toast(r.errors.joinToString("\n"))
                                }
                            } catch (e: SecurityException) {
                                toast("Not authorized: ${e.message}")
                            }
                        }
                    }
                }
            }
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    }
}
