package com.fieldtripops.data.repository

import com.fieldtripops.data.dao.RefundDecisionDao
import com.fieldtripops.data.entity.RefundDecisionEntity
import com.fieldtripops.domain.model.RefundDecision
import com.fieldtripops.domain.repository.RefundDecisionRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

class RefundDecisionRepositoryImpl(
    private val dao: RefundDecisionDao
) : RefundDecisionRepository {

    override suspend fun record(decision: RefundDecision) {
        dao.insert(decision.toEntity())
    }

    override suspend fun getByBooking(bookingOrderId: String): List<RefundDecision> =
        dao.getByBooking(bookingOrderId).map { it.toDomain() }

    override suspend fun getAll(): List<RefundDecision> = dao.getAll().map { it.toDomain() }

    private fun RefundDecisionEntity.toDomain() = RefundDecision(
        id = id, bookingOrderId = bookingOrderId,
        paidTotal = BigDecimal(paidTotalCents).movePointLeft(2).setScale(2, RoundingMode.HALF_EVEN),
        refundAmount = BigDecimal(refundAmountCents).movePointLeft(2).setScale(2, RoundingMode.HALF_EVEN),
        refundPercent = refundPercent, ruleUsed = ruleUsed,
        approverUserId = approverUserId, approverName = approverName,
        decidedAt = Instant.ofEpochMilli(decidedAt),
        manualOverrideReason = manualOverrideReason
    )

    private fun RefundDecision.toEntity() = RefundDecisionEntity(
        id = id, bookingOrderId = bookingOrderId,
        paidTotalCents = paidTotal.setScale(2, RoundingMode.HALF_EVEN).movePointRight(2).toLong(),
        refundAmountCents = refundAmount.setScale(2, RoundingMode.HALF_EVEN).movePointRight(2).toLong(),
        refundPercent = refundPercent, ruleUsed = ruleUsed,
        approverUserId = approverUserId, approverName = approverName,
        decidedAt = decidedAt.toEpochMilli(),
        manualOverrideReason = manualOverrideReason
    )
}
