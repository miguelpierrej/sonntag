package com.example.sonntag.sync

import com.example.sonntag.imports.chooseOpenPath
import com.example.sonntag.imports.chooseSavePath
import java.io.File

class SyncFileServiceJvm : SyncFileService {

    override suspend fun savePackage(
        defaultName: String,
        dialogTitle: String,
        filterLabel: String,
        bytes: ByteArray,
        extension: String,
    ): String? {
        val path = chooseSavePath(defaultName, dialogTitle, filterLabel, extension) ?: return null
        val file = if (path.endsWith(".$extension")) File(path) else File("$path.$extension")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    override suspend fun openPackage(dialogTitle: String, filterLabel: String, extension: String): ByteArray? {
        val path = chooseOpenPath(dialogTitle, filterLabel, extension) ?: return null
        return File(path).readBytes()
    }
}

actual fun createSyncFileService(): SyncFileService = SyncFileServiceJvm()
