package com.fieldtripops.domain.model

import java.time.Instant

data class AttachmentRef(
    val id: String,
    val ownerEntityType: String,
    val ownerEntityId: String,
    val fileName: String,
    val mimeType: String,
    val storagePath: String,
    val sizeBytes: Long,
    val createdAt: Instant
)
