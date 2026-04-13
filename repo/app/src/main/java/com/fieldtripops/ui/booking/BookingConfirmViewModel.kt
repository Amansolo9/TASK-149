package com.fieldtripops.ui.booking

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtripops.domain.booking.FeeCalculator
import com.fieldtripops.domain.model.FeeCategory
import com.fieldtripops.domain.model.FeeItem
import com.fieldtripops.domain.model.Role
import com.fieldtripops.domain.repository.BookingRepository
import com.fieldtripops.domain.usecase.CancelBookingUseCase
import com.fieldtripops.domain.usecase.ConfirmBookingUseCase
import com.fieldtripops.security.auth.AccessControl
import com.fieldtripops.security.auth.SessionManager
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

class BookingConfirmViewModel(
    private val bookingRepository: BookingRepository,
    private val confirmBookingUseCase: ConfirmBookingUseCase,
    private val cancelBookingUseCase: CancelBookingUseCase,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableLiveData(BookingConfirmUiState())
    val uiState: LiveData<BookingConfirmUiState> = _uiState

    private val _result = MutableLiveData<BookingConfirmResult>(BookingConfirmResult.Idle)
    val result: LiveData<BookingConfirmResult> = _result

    private var orderId: String = ""

    fun load(orderId: String) {
        this.orderId = orderId
        viewModelScope.launch {
            try {
                val session = sessionManager.requireSession()
                val order = bookingRepository.findById(orderId)
                if (order == null) {
                    _result.value = BookingConfirmResult.Error(listOf("Booking order not found"))
                    return@launch
                }
                // Object-level read authorization: owner, Agent, or Admin
                AccessControl.requireOwnerOrRole(
                    session, order.travelerId, "BookingOrder", orderId,
                    Role.Agent, Role.Administrator
                )
                _uiState.value = _uiState.value?.copy(order = order)
            } catch (e: SecurityException) {
                _result.value = BookingConfirmResult.Error(listOf("Not authorized: ${e.message}"))
            }
        }
    }

    fun addFeeItem(description: String, amount: String, category: FeeCategory) {
        val trimmedDesc = description.trim()
        if (trimmedDesc.isEmpty()) {
            _result.value = BookingConfirmResult.Error(listOf("Description is required"))
            return
        }
        val amt = amount.trim().toBigDecimalOrNull()
        if (amt == null) {
            _result.value = BookingConfirmResult.Error(listOf("Amount must be a valid number"))
            return
        }
        if (amt.scale() > 2) {
            _result.value = BookingConfirmResult.Error(listOf("Amount can have at most 2 decimal places"))
            return
        }

        val current = _uiState.value ?: return
        val newItem = FeeItem(
            id = UUID.randomUUID().toString(),
            bookingOrderId = orderId,
            category = category,
            description = trimmedDesc,
            amountUsd = amt.setScale(2, java.math.RoundingMode.HALF_EVEN),
            sortOrder = current.feeItems.size
        )
        val updated = current.feeItems + newItem
        val breakdown = FeeCalculator.calculate(updated)
        _uiState.value = current.copy(feeItems = updated, total = breakdown.grandTotal)
        _result.value = BookingConfirmResult.Idle
    }

    fun removeFeeItem(id: String) {
        val current = _uiState.value ?: return
        val updated = current.feeItems.filterNot { it.id == id }
        val breakdown = FeeCalculator.calculate(updated)
        _uiState.value = current.copy(feeItems = updated, total = breakdown.grandTotal)
    }

    fun confirm() {
        val items = _uiState.value?.feeItems.orEmpty()
        viewModelScope.launch {
            try {
                when (val r = confirmBookingUseCase.execute(orderId, items)) {
                    is ConfirmBookingUseCase.Result.Confirmed ->
                        _result.value = BookingConfirmResult.Confirmed(r.total)
                    is ConfirmBookingUseCase.Result.FeeValidationFailed ->
                        _result.value = BookingConfirmResult.Error(r.errors)
                    is ConfirmBookingUseCase.Result.InvalidState ->
                        _result.value = BookingConfirmResult.Error(
                            listOf("Cannot confirm: booking is in state ${r.currentState.name}")
                        )
                    is ConfirmBookingUseCase.Result.OrderNotFound ->
                        _result.value = BookingConfirmResult.Error(listOf("Booking order not found"))
                }
            } catch (e: SecurityException) {
                _result.value = BookingConfirmResult.Error(listOf("Not authorized: ${e.message}"))
            }
        }
    }

    fun cancel(reason: String) {
        viewModelScope.launch {
            try {
                when (cancelBookingUseCase.execute(orderId, reason)) {
                    is CancelBookingUseCase.Result.Cancelled ->
                        _result.value = BookingConfirmResult.Cancelled
                    is CancelBookingUseCase.Result.OrderNotFound ->
                        _result.value = BookingConfirmResult.Error(listOf("Booking order not found"))
                    is CancelBookingUseCase.Result.InvalidState ->
                        _result.value = BookingConfirmResult.Error(listOf("Cannot cancel in current state"))
                }
            } catch (e: SecurityException) {
                _result.value = BookingConfirmResult.Error(listOf("Not authorized: ${e.message}"))
            }
        }
    }

    fun currentTotal(): BigDecimal = _uiState.value?.total ?: BigDecimal.ZERO.setScale(2)
}
