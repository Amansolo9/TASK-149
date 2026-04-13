package com.fieldtripops.attachment

interface AttachmentStorage {
    suspend fun store(id: String, data: ByteArray): String
    suspend fun retrieve(path: String): ByteArray?
    suspend fun delete(path: String): Boolean
    suspend fun exists(path: String): Boolean

    /**
     * Returns the canonical storage path for a given attachment id without
     * writing any bytes. Used by the staged-then-committed atomic flow so the
     * AttachmentRef row can record the final path before bytes are flushed.
     */
    fun pathFor(id: String): String
}
