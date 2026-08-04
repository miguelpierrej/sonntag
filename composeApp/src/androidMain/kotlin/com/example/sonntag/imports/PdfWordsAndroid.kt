package com.example.sonntag.imports

import com.example.sonntag.platform.AndroidApp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

/**
 * Mesma extracao do desktop, sobre o pdfbox-android (port do PDFBox 2). A API de
 * posicionamento e a mesma; muda o pacote e o carregamento do documento.
 */
actual fun extractPdfWords(bytes: ByteArray): List<List<PdfWord>> {
    // Carrega os recursos de fonte do port; sem isto a extracao vem vazia.
    PDFBoxResourceLoader.init(AndroidApp.context)
    return PDDocument.load(bytes).use { document ->
        (1..document.numberOfPages).map { page -> wordsOf(document, page) }
    }
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
