package com.example.sonntag.pdf.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import com.example.sonntag.platform.AndroidApp

/** Traco de 3pt: mesmo padrao do divisor do S-140 no desktop. */
private const val DASH_LENGTH = 3f

/**
 * [DocumentCanvas] sobre o Canvas do Android. Serve tanto ao PDF (PdfDocument)
 * quanto ao PNG (Bitmap): as duas superficies expoem o mesmo Canvas.
 *
 * O Canvas tem origem no canto superior esquerdo e Y para baixo, enquanto o canvas
 * do documento e Y para cima — a conversao acontece em [flip].
 */
abstract class AndroidDocumentCanvas(
    final override val pageWidth: Float,
    final override val pageHeight: Float,
) : DocumentCanvas {

    protected abstract val canvas: Canvas

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Y do documento (de baixo para cima) para Y do Canvas (de cima para baixo). */
    private fun flip(y: Float): Float = pageHeight - y

    private fun typeface(style: FontStyle): Typeface = when (style) {
        FontStyle.REGULAR -> notoRegular
        FontStyle.BOLD -> notoBold
        FontStyle.ITALIC -> notoItalic
    }

    private fun applyText(style: TextStyle) {
        paint.reset()
        paint.isAntiAlias = true
        paint.typeface = typeface(style.font)
        paint.textSize = style.size
        paint.color = style.color.toArgb()
        paint.style = Paint.Style.FILL
    }

    override fun text(text: String, x: Float, y: Float, style: TextStyle) {
        applyText(style)
        // O baseline do PDF e o mesmo conceito do Canvas, entao basta inverter Y.
        canvas.drawText(text, x, flip(y), paint)
    }

    override fun measure(text: String, style: TextStyle): Float {
        applyText(style)
        return paint.measureText(text)
    }

    override fun fillRect(x: Float, y: Float, width: Float, height: Float, color: DocColor, radius: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = color.toArgb()
        paint.style = Paint.Style.FILL
        // No documento (x, y) e o canto inferior; no Canvas o topo fica em y+height.
        val rect = RectF(x, flip(y + height), x + width, flip(y))
        if (radius > 0f) canvas.drawRoundRect(rect, radius, radius, paint)
        else canvas.drawRect(rect, paint)
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
        paint.reset()
        paint.isAntiAlias = true
        paint.color = color.toArgb()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = lineWidth
        val rect = RectF(x, flip(y + height), x + width, flip(y))
        if (radius > 0f) canvas.drawRoundRect(rect, radius, radius, paint)
        else canvas.drawRect(rect, paint)
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
        paint.reset()
        paint.isAntiAlias = true
        paint.color = color.toArgb()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = lineWidth
        paint.pathEffect = if (dashed) DashPathEffect(floatArrayOf(DASH_LENGTH, DASH_LENGTH), 0f) else null
        canvas.drawLine(x1, flip(y1), x2, flip(y2), paint)
    }

    override fun image(bytes: ByteArray, x: Float, y: Float, width: Float, height: Float) {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        val destino = RectF(x, flip(y + height), x + width, flip(y))
        canvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), destino, null)
        bitmap.recycle()
    }

    private fun DocColor.toArgb(): Int =
        (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

    companion object {
        // As mesmas fontes empacotadas usadas no desktop, para o documento sair igual.
        private val notoRegular: Typeface by lazy { asset("fonts/NotoSans-Regular.ttf") }
        private val notoBold: Typeface by lazy { asset("fonts/NotoSans-Bold.ttf") }
        private val notoItalic: Typeface by lazy { asset("fonts/NotoSans-Italic.ttf") }

        private fun asset(path: String): Typeface =
            Typeface.createFromAsset(AndroidApp.context.assets, path)
    }
}

/** Superficie de PDF: cada pagina e um Canvas do PdfDocument. */
class AndroidPdfCanvas(
    private val document: PdfDocument,
    pageWidth: Float,
    pageHeight: Float,
) : AndroidDocumentCanvas(pageWidth, pageHeight), AutoCloseable {

    private var pageNumber = 1
    private var currentPage = startPage()
    override val canvas: Canvas get() = currentPage.canvas

    private fun startPage(): PdfDocument.Page {
        val info = PdfDocument.PageInfo.Builder(
            pageWidth.toInt(),
            pageHeight.toInt(),
            pageNumber,
        ).create()
        return document.startPage(info)
    }

    override fun newPage() {
        document.finishPage(currentPage)
        pageNumber++
        currentPage = startPage()
    }

    override fun close() {
        document.finishPage(currentPage)
    }
}

/** Superficie de imagem: uma pagina unica, alta, exportada como PNG. */
class AndroidBitmapCanvas(
    pageWidth: Float,
    pageHeight: Float,
    background: DocColor,
) : AndroidDocumentCanvas(pageWidth, pageHeight) {

    val bitmap: Bitmap = Bitmap.createBitmap(
        pageWidth.toInt(),
        pageHeight.toInt(),
        Bitmap.Config.ARGB_8888,
    )
    override val canvas: Canvas = Canvas(bitmap).apply {
        drawColor((0xFF shl 24) or (background.red shl 16) or (background.green shl 8) or background.blue)
    }

    /** Uma imagem nao tem paginas; o layout de PNG nunca chama. */
    override fun newPage() = Unit

    fun toPngBytes(): ByteArray = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        out.toByteArray()
    }
}
