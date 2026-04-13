package com.fieldtripops.domain.repository

import com.fieldtripops.domain.model.RefundRule

interface RefundRuleRepository {
    suspend fun listActive(): List<RefundRule>
    suspend fun listAll(): List<RefundRule>
    suspend fun findByCode(code: String): RefundRule?
    suspend fun upsert(rule: RefundRule)
    suspend fun seedDefaultsIfEmpty()
}
