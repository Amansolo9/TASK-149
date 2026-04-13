package com.fieldtripops.attachment

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalFileAttachmentStorage(context: Context) : AttachmentStorage {

    private val attachmentDir: File = File(context.filesDir, "attachments").also {
        it.mkdirs()
    }

    override suspend fun store(id: String, data: ByteArray): String = withContext(Dispatchers.IO) {
        val file = File(attachmentDir, id)
        file.writeBytes(data)
        file.absolutePath
    }

    override suspend fun retrieve(path: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.exists()) file.readBytes() else null
    }

    override suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        file.exists() && file.delete()
    }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        File(path).exists()
    }

    override fun pathFor(id: String): String = File(attachmentDir, id).absolutePath
}
