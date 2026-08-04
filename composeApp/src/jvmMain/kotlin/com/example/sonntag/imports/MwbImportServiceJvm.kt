package com.example.sonntag.imports

import java.io.File

class MwbImportServiceJvm : MwbImportService {

    override suspend fun pickPdfText(dialogTitle: String, pdfFilterLabel: String): String? {
        val path = chooseOpenPath(dialogTitle, pdfFilterLabel, "pdf") ?: return null
        return MwbTextExtractor.extract(File(path).readBytes())
    }
}

actual fun createMwbImportService(): MwbImportService = MwbImportServiceJvm()
