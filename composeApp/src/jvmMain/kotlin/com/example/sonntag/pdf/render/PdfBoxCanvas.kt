package com.example.sonntag.pdf.render

import com.example.sonntag.pdf.PdfFonts
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.awt.Color

/**
 * [DocumentCanvas] sobre o PDFBox. A convencao de coordenadas do canvas ja e a do
 * PDF (origem embaixo, Y para cima), entao aqui nao ha inversao.
 */
private const val DASH_LENGTH = 3f

class PdfBoxCanvas(
    private val document: PDDocument,
    /** Tamanho da folha. O padrao do PDFBox e Letter, nao A4 — passe explicitamente
     *  quando o documento exigir outro. */
    private val pageSize: PDRectangle = PDPage().mediaBox,
) : DocumentCanvas, AutoCloseable {

    private val fonts = PdfFonts.load(document)
    private var page = PDPage(pageSize).also { document.addPage(it) }
    private var stream = PDPageContentStream(document, page)

    override val pageWidth: Float get() = page.mediaBox.width
    override val pageHeight: Float get() = page.mediaBox.height

    private fun font(style: FontStyle): PDFont = when (style) {
        FontStyle.REGULAR -> fonts.regular
        FontStyle.BOLD -> fonts.bold
        FontStyle.ITALIC -> fonts.italic
    }

    private fun DocColor.awt() = Color(red, green, blue)

    override fun text(text: String, x: Float, y: Float, style: TextStyle) {
        stream.setNonStrokingColor(style.color.awt())
        stream.setFont(font(style.font), style.size)
        stream.beginText()
        stream.newLineAtOffset(x, y)
        stream.showText(text)
        stream.endText()
    }

    override fun measure(text: String, style: TextStyle): Float =
        font(style.font).getStringWidth(text) / 1000f * style.size

    override fun fillRect(x: Float, y: Float, width: Float, height: Float, color: DocColor, radius: Float) {
        // O PDFBox nao tem retangulo arredondado nativo; o raio e ignorado, como no
        // layout original, que so usa cantos retos no PDF.
        stream.setNonStrokingColor(color.awt())
        stream.addRect(x, y, width, height)
        stream.fill()
    }

    override fun strokeRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: DocColor,
        lineWidth: Float,
        radius: Float,
    ) {
        stream.setStrokingColor(color.awt())
        stream.setLineWidth(lineWidth)
        stream.addRect(x, y, width, height)
        stream.stroke()
    }

    override fun line(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: DocColor,
        lineWidth: Float,
        dashed: Boolean,
    ) {
        stream.setStrokingColor(color.awt())
        stream.setLineWidth(lineWidth)
        if (dashed) stream.setLineDashPattern(floatArrayOf(DASH_LENGTH), 0f)
        stream.moveTo(x1, y1)
        stream.lineTo(x2, y2)
        stream.stroke()
        if (dashed) stream.setLineDashPattern(floatArrayOf(), 0f)
    }

    override fun image(bytes: ByteArray, x: Float, y: Float, width: Float, height: Float) {
        val image = PDImageXObject.createFromByteArray(document, bytes, "img")
        stream.drawImage(image, x, y, width, height)
    }

    override fun newPage() {
        stream.close()
        page = PDPage(pageSize).also { document.addPage(it) }
        stream = PDPageContentStream(document, page)
    }

    override fun close() {
        stream.close()
    }
}
