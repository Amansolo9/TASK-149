package com.fieldtripops.ui.booking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.fieldtripops.R
import com.fieldtripops.databinding.FragmentBookingConfirmBinding
import com.fieldtripops.databinding.ItemFeeRowBinding
import com.fieldtripops.domain.model.FeeCategory
import com.fieldtripops.domain.model.FeeItem
import com.fieldtripops.ui.util.gone
import com.fieldtripops.ui.util.textString
import com.fieldtripops.ui.util.visible
import org.koin.androidx.viewmodel.ext.android.viewModel

class BookingConfirmFragment : Fragment() {

    private var _binding: FragmentBookingConfirmBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BookingConfirmViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookingConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val orderId = arguments?.getString(ARG_ORDER_ID) ?: run {
            findNavController().popBackStack()
            return
        }
        viewModel.load(orderId)

        binding.addFeeButton.setOnClickListener {
            val category = when (binding.feeCategoryGroup.checkedRadioButtonId) {
                R.id.radioTax -> FeeCategory.TAX_FEE
                R.id.radioAdjust -> FeeCategory.ADJUSTMENT
                else -> FeeCategory.BASE_FARE
            }
            viewModel.addFeeItem(
                binding.feeDescInput.textString(),
                binding.feeAmountInput.textString(),
                category
            )
            binding.feeDescInput.setText("")
            binding.feeAmountInput.setText("")
        }

        binding.confirmButton.setOnClickListener {
            viewModel.confirm()
        }

        binding.cancelButton.setOnClickListener {
            viewModel.cancel("Cancelled by agent prior to confirmation")
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            state.order?.let { o ->
                binding.orderSummaryText.text = buildString {
                    appendLine("Order: ${o.id.take(8)}…")
                    appendLine("State: ${o.state.name}")
                    appendLine("Party: ${o.partySize}")
                    append("Slot: ${o.inventorySlotId}")
                }
            }
            renderFeeRows(state.feeItems)
            binding.totalText.text = "Total: $${state.total}"
        }

        viewModel.result.observe(viewLifecycleOwner) { result ->
            when (result) {
                is BookingConfirmResult.Confirmed,
                is BookingConfirmResult.Cancelled -> findNavController().popBackStack()
                is BookingConfirmResult.Error -> {
                    binding.errorsText.text = result.errors.joinToString("\n")
                    binding.errorsText.visible()
                }
                BookingConfirmResult.Idle -> binding.errorsText.gone()
            }
        }
    }

    private fun renderFeeRows(items: List<FeeItem>) {
        binding.feeRowsContainer.removeAllViews()
        items.forEach { item ->
            val row = ItemFeeRowBinding.inflate(layoutInflater, binding.feeRowsContainer, false)
            row.feeCategoryText.text = when (item.category) {
                FeeCategory.BASE_FARE -> "Base"
                FeeCategory.TAX_FEE -> "Tax"
                FeeCategory.ADJUSTMENT -> "Adj"
            }
            row.feeDescriptionText.text = item.description
            row.feeAmountText.text = "$${item.amountUsd}"
            row.root.setOnLongClickListener {
                viewModel.removeFeeItem(item.id)
                true
            }
            binding.feeRowsContainer.addView(row.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_ORDER_ID = "orderId"
    }
}
