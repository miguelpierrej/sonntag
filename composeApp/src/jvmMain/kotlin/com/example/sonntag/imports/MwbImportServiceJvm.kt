package com.example.sonntag.imports

import org.apache.pdfbox.Loader
import java.io.File

class MwbImportServiceJvm : MwbImportService {

    override fun pickPdfText(dialogTitle: String, pdfFilterLabel: String): String? {
        val path = chooseOpenPath(dialogTitle, pdfFilterLabel, "pdf") ?: return null
        return Loader.loadPDF(File(path)).use { doc -> MwbTextExtractor.extract(doc) }
    }
}

actual fun createMwbImportService(): MwbImportService = MwbImportServiceJvm()
