package com.example.sonntag.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font

class PdfFonts private constructor(
    val regular: PDFont,
    val bold: PDFont,
    val italic: PDFont,
) {
    companion object {
        fun load(document: PDDocument): PdfFonts {
            val loader = PdfFonts::class.java.classLoader
            fun fontStream(name: String) =
                loader.getResourceAsStream(name)
                    ?: error("Font resource not found on classpath: $name")

            val regular = fontStream("fonts/NotoSans-Regular.ttf").use {
                PDType0Font.load(document, it, true)
            }
            val bold = fontStream("fonts/NotoSans-Bold.ttf").use {
                PDType0Font.load(document, it, true)
            }
            val italic = fontStream("fonts/NotoSans-Italic.ttf").use {
                PDType0Font.load(document, it, true)
            }
            return PdfFonts(regular, bold, italic)
        }
    }
}
