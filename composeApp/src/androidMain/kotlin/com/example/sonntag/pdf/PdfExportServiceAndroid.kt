package com.example.sonntag.pdf

import android.graphics.pdf.PdfDocument
import com.example.sonntag.pdf.render.AndroidPdfCanvas
import com.example.sonntag.pdf.render.AndroidBitmapCanvas
import com.example.sonntag.pdf.render.AssignmentSlipsLayout
import com.example.sonntag.pdf.render.CleaningPngLayout
import com.example.sonntag.pdf.render.PNG_WIDTH
import com.example.sonntag.pdf.render.PngColors
import com.example.sonntag.pdf.render.TopDownCanvas
import com.example.sonntag.pdf.render.WeekendMeetingPngLayout
import com.example.sonntag.pdf.render.WeekendMonthlyPngLayout
import com.example.sonntag.pdf.render.AvScheduleLayout
import com.example.sonntag.pdf.render.CleaningLayout
import com.example.sonntag.pdf.render.MidweekProgramLayout
import com.example.sonntag.pdf.render.WeekendMeetingLayout
import com.example.sonntag.pdf.render.WeekendMonthlyLayout
import com.example.sonntag.platform.AndroidApp
import com.example.sonntag.platform.FilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Tamanhos de folha em pontos, iguais aos do desktop. O S-140 e o S-89 usam A4;
// os demais seguem o padrao do PDFBox, que e Letter.
private const val A4_WIDTH = 595f
private const val A4_HEIGHT = 842f
private const val LETTER_WIDTH = 612f
private const val LETTER_HEIGHT = 792f

class PdfExportServiceAndroid : PdfExportService {

    override suspend fun exportCleaningSchedule(data: CleaningSchedulePdfData): Boolean {
        val bytes = withContext(Dispatchers.IO) {
            renderPdf(LETTER_WIDTH, LETTER_HEIGHT) { canvas ->
                CleaningLayout(
                    data = data,
                    geradoEm = timestamp(),
                    iconBytes = asset("icons/limpeza.png"),
                ).draw(canvas)
            }
        }
        return save("${data.fileSlug}.pdf", bytes)
    }

    override suspend fun exportMeetingProgram(data: MeetingProgramPdfData): Boolean {
        val bytes = withContext(Dispatchers.IO) {
            renderPdf(LETTER_WIDTH, LETTER_HEIGHT) { canvas ->
                WeekendMeetingLayout(data, timestamp(), asset("icons/conferencia.png")).draw(canvas)
            }
        }
        return save("${data.fileSlug}.pdf", bytes)
    }

    override suspend fun exportMonthlyProgram(data: MonthlyProgramPdfData): Boolean {
        val bytes = withContext(Dispatchers.IO) {
            renderPdf(LETTER_WIDTH, LETTER_HEIGHT) { canvas ->
                WeekendMonthlyLayout(data, timestamp(), asset("icons/conferencia.png")).draw(canvas)
            }
        }
        return save("${data.fileSlug}.pdf", bytes)
    }

    override suspend fun exportMidweekProgram(data: MidweekProgramPdfData): Boolean {
        val bytes = withContext(Dispatchers.IO) {
            renderPdf(A4_WIDTH, A4_HEIGHT) { canvas -> MidweekProgramLayout(data).draw(canvas) }
        }
        return save("${data.fileSlug}.pdf", bytes)
    }

    override suspend fun exportMidweekAssignments(data: MidweekAssignmentsPdfData): Boolean {
        val bytes = withContext(Dispatchers.IO) {
            renderPdf(A4_WIDTH, A4_HEIGHT) { canvas -> AssignmentSlipsLayout(data).draw(canvas) }
        }
        return save("${data.fileSlug}.pdf", bytes)
    }

    override suspend fun exportAvSchedule(data: AvSchedulePdfData): Boolean {
        val bytes = withContext(Dispatchers.IO) {
            renderPdf(LETTER_WIDTH, LETTER_HEIGHT) { canvas ->
                AvScheduleLayout(data, timestamp()).draw(canvas)
            }
        }
        return save("${data.fileSlug}.pdf", bytes)
    }

    override suspend fun exportCleaningSchedulePng(data: CleaningSchedulePdfData): Boolean {
        val layout = CleaningPngLayout(data, timestamp(), asset("icons/limpeza.png"))
        val bytes = withContext(Dispatchers.IO) { renderPng(layout.measureHeight()) { layout.draw(it) } }
        return save("${data.fileSlug}.png", bytes)
    }

    override suspend fun exportMonthlyProgramPng(data: MonthlyProgramPdfData): Boolean {
        val layout = WeekendMonthlyPngLayout(data, timestamp(), asset("icons/conferencia.png"))
        val bytes = withContext(Dispatchers.IO) {
            // Superficie descartavel so para medir o texto antes da altura final.
            val altura = layout.measureBlocks(AndroidBitmapCanvas(1f, 1f, PngColors.PageBg))
            renderPng(altura) { layout.draw(it) }
        }
        return save("${data.fileSlug}.png", bytes)
    }

    override suspend fun exportMeetingProgramPng(data: MeetingProgramPdfData): Boolean {
        val layout = WeekendMeetingPngLayout(data, timestamp(), asset("icons/conferencia.png"))
        val bytes = withContext(Dispatchers.IO) { renderPng(layout.measureHeight()) { layout.draw(it) } }
        return save("${data.fileSlug}.png", bytes)
    }

    // ─── Sem uso na interface ────────────────────────────────────────────────
    // O programa semanal nao e exportado por nenhuma tela; no desktop tambem
    // devolve false.
    override suspend fun exportWeeklyProgram(data: WeeklyProgramPdfData): Boolean = false
    override suspend fun exportWeeklyProgramPng(data: WeeklyProgramPdfData): Boolean = false

    // ─── Infra ───────────────────────────────────────────────────────────────

    private fun renderPng(height: Int, draw: (TopDownCanvas) -> Unit): ByteArray {
        val canvas = AndroidBitmapCanvas(PNG_WIDTH.toFloat(), height.toFloat(), PngColors.PageBg)
        draw(TopDownCanvas(canvas))
        return canvas.toPngBytes()
    }

    private fun renderPdf(width: Float, height: Float, draw: (AndroidPdfCanvas) -> Unit): ByteArray {
        val document = PdfDocument()
        AndroidPdfCanvas(document, width, height).use { draw(it) }
        return ByteArrayOutputStream().use { out ->
            document.writeTo(out)
            document.close()
            out.toByteArray()
        }
    }

    /** O usuario escolhe onde salvar; dali ele imprime ou compartilha pelo sistema. */
    private suspend fun save(defaultName: String, bytes: ByteArray): Boolean {
        val uri = FilePicker.create(defaultName) ?: return false
        return withContext(Dispatchers.IO) {
            AndroidApp.context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
        }
    }

    private fun asset(path: String): ByteArray? =
        runCatching { AndroidApp.context.assets.open(path).use { it.readBytes() } }.getOrNull()

    private fun timestamp(): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
}

actual fun createPdfExportService(): PdfExportService = PdfExportServiceAndroid()
