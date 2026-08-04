package com.example.sonntag.pdf.render

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.io.ByteArrayInputStream
import java.io.File

/**
 * [DocumentCanvas] sobre Graphics2D, para as exportacoes em PNG do desktop.
 *
 * Usa a fonte SansSerif do sistema, e nao a Noto empacotada dos PDFs, para manter a
 * saida identica a que o app ja gerava.
 */
class Java2DCanvas(
    private val widthPx: Int,
    private val heightPx: Int,
    background: DocColor,
) : DocumentCanvas {

    val image: BufferedImage = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB)
    private val g: Graphics2D = image.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        color = background.awt()
        fillRect(0, 0, widthPx, heightPx)
    }

    override val pageWidth: Float get() = widthPx.toFloat()
    override val pageHeight: Float get() = heightPx.toFloat()

    private fun DocColor.awt() = Color(red, green, blue)

    private fun flip(y: Float): Int = (heightPx - y).toInt()

    private fun font(style: TextStyle): Font = Font(
        "SansSerif",
        when (style.font) {
            FontStyle.REGULAR -> Font.PLAIN
            FontStyle.BOLD -> Font.BOLD
            FontStyle.ITALIC -> Font.ITALIC
        },
        style.size.toInt(),
    )

    override fun text(text: String, x: Float, y: Float, style: TextStyle) {
        g.color = style.color.awt()
        g.font = font(style)
        g.drawString(text, x.toInt(), flip(y))
    }

    override fun measure(text: String, style: TextStyle): Float {
        g.font = font(style)
        return g.fontMetrics.stringWidth(text).toFloat()
    }

    override fun fillRect(x: Float, y: Float, width: Float, height: Float, color: DocColor, radius: Float) {
        g.color = color.awt()
        val top = flip(y + height)
        if (radius > 0f) {
            g.fillRoundRect(x.toInt(), top, width.toInt(), height.toInt(), radius.toInt() * 2, radius.toInt() * 2)
        } else {
            g.fillRect(x.toInt(), top, width.toInt(), height.toInt())
        }
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
        g.color = color.awt()
        g.stroke = BasicStroke(lineWidth)
        val top = flip(y + height)
        if (radius > 0f) {
            g.drawRoundRect(x.toInt(), top, width.toInt(), height.toInt(), radius.toInt() * 2, radius.toInt() * 2)
        } else {
            g.drawRect(x.toInt(), top, width.toInt(), height.toInt())
        }
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
        g.color = color.awt()
        g.stroke = if (dashed) {
            BasicStroke(lineWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(3f), 0f)
        } else {
            BasicStroke(lineWidth)
        }
        g.drawLine(x1.toInt(), flip(y1), x2.toInt(), flip(y2))
    }

    override fun image(bytes: ByteArray, x: Float, y: Float, width: Float, height: Float) {
        val img = ImageIO.read(ByteArrayInputStream(bytes)) ?: return
        g.drawImage(img, x.toInt(), flip(y + height), width.toInt(), height.toInt(), null)
    }

    /** Uma imagem nao tem paginas; os layouts de PNG nunca chamam. */
    override fun newPage() = Unit

    fun writeTo(file: File) {
        g.dispose()
        ImageIO.write(image, "png", file)
    }
}
