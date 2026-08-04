package com.example.sonntag.sync

import com.example.sonntag.platform.AndroidApp
import com.example.sonntag.platform.FilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Extensao propria nao tem MIME registrado; o seletor filtra por qualquer arquivo. */
private val PACKAGE_MIME = arrayOf("*/*")

class SyncFileServiceAndroid : SyncFileService {

    override suspend fun savePackage(
        defaultName: String,
        dialogTitle: String,
        filterLabel: String,
        bytes: ByteArray,
    ): String? {
        val uri = FilePicker.create(defaultName) ?: return null
        return withContext(Dispatchers.IO) {
            AndroidApp.context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            uri.toString()
        }
    }

    override suspend fun openPackage(dialogTitle: String, filterLabel: String): ByteArray? {
        val uri = FilePicker.open(PACKAGE_MIME) ?: return null
        return withContext(Dispatchers.IO) {
            AndroidApp.context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
    }
}

actual fun createSyncFileService(): SyncFileService = SyncFileServiceAndroid()
