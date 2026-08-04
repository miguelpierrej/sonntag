package com.example.sonntag.sync

import com.example.sonntag.imports.chooseOpenPath
import com.example.sonntag.imports.chooseSavePath
import java.io.File

class SyncFileServiceJvm : SyncFileService {

    override fun savePackage(
        defaultName: String,
        dialogTitle: String,
        filterLabel: String,
        bytes: ByteArray,
    ): String? {
        val path = chooseSavePath(defaultName, dialogTitle, filterLabel, PACKAGE_EXTENSION) ?: return null
        val file = if (path.endsWith(".$PACKAGE_EXTENSION")) File(path) else File("$path.$PACKAGE_EXTENSION")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    override fun openPackage(dialogTitle: String, filterLabel: String): ByteArray? {
        val path = chooseOpenPath(dialogTitle, filterLabel, PACKAGE_EXTENSION) ?: return null
        return File(path).readBytes()
    }
}

actual fun createSyncFileService(): SyncFileService = SyncFileServiceJvm()
