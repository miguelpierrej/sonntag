package com.example.sonntag.imports

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition

actual fun extractPdfWords(bytes: ByteArray): List<List<PdfWord>> =
    Loader.loadPDF(bytes).use { document ->
        (1..document.numberOfPages).map { page -> wordsOf(document, page) }
    }

private fun wordsOf(document: PDDocument, page: Int): List<PdfWord> {
    val words = mutableListOf<PdfWord>()
    val stripper = object : PDFTextStripper() {
        override fun writeString(text: String, textPositions: List<TextPosition>) {
            val limpo = text.trim()
            if (limpo.isEmpty() || textPositions.isEmpty()) return
            words += PdfWord(
                text = limpo,
                x0 = textPositions.minOf { it.xDirAdj },
                x1 = textPositions.maxOf { it.xDirAdj + it.widthDirAdj },
                y = textPositions[0].yDirAdj,
                spaceWidth = textPositions[0].widthOfSpace.takeIf { it > 0f } ?: 2.5f,
            )
        }
    }
    stripper.sortByPosition = true
    stripper.startPage = page
    stripper.endPage = page
    stripper.getText(document)
    return words
}
