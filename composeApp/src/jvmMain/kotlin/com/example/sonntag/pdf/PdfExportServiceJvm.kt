package com.example.sonntag.pdf

import com.example.sonntag.pdf.render.AssignmentSlipsLayout
import com.example.sonntag.pdf.render.AvScheduleLayout
import com.example.sonntag.pdf.render.WeekendMeetingLayout
import com.example.sonntag.pdf.render.WeekendMonthlyLayout
import com.example.sonntag.pdf.render.CleaningLayout
import com.example.sonntag.pdf.render.CleaningPngLayout
import com.example.sonntag.pdf.render.WeekendMeetingPngLayout
import com.example.sonntag.pdf.render.WeekendMonthlyPngLayout
import com.example.sonntag.pdf.render.Java2DCanvas
import com.example.sonntag.pdf.render.PNG_WIDTH
import com.example.sonntag.pdf.render.PngColors
import com.example.sonntag.pdf.render.TopDownCanvas
import com.example.sonntag.pdf.render.MidweekProgramLayout
import com.example.sonntag.pdf.render.PdfBoxCanvas
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.awt.Color
import java.awt.Desktop
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Font
import java.awt.Frame
import java.awt.Graphics2D
import java.awt.KeyboardFocusManager
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.FutureTask
import javax.imageio.ImageIO

/** Lado do icone de cabecalho nas exportacoes PNG (canvas de 1080px). */
private const val PngHeaderIconSize = 140

private val ColorTitle = Color(26, 29, 41)
private val ColorMuted = Color(120, 124, 138)
private val ColorBorder = Color(212, 216, 224)
private val ColorSeparator = Color(232, 234, 240)
private val ColorBlockHeader = Color(232, 235, 240)
private val ColorPageBg = Color(245, 246, 248)

// Cores do formulario S-140 (Nossa Vida e Ministerio Cristao)
private val ColorMaroon = Color(0x56, 0x17, 0x45)
private val ColorTeal = Color(0x2E, 0x7A, 0x88)
private val ColorGold = Color(0xA8, 0x6C, 0x00)
private val ColorGrayBox = Color(0xEB, 0xE6, 0xEB)
private val ColorTableHeader = Color(0xE5, 0xC4, 0xC6)
private val ColorInk = Color(0x22, 0x22, 0x28)

private class PdfExportServiceJvm : PdfExportService {

    override suspend fun exportMeetingProgram(data: MeetingProgramPdfData): Boolean {
        val path = chooseSavePath("${data.fileSlug}.pdf", data.labels.common.dialogTitle) ?: return false
        return runCatching { writeMeetingProgramPdf(path, data) }.isSuccess
    }

    override suspend fun exportMonthlyProgram(data: MonthlyProgramPdfData): Boolean {
        val path = chooseSavePath("${data.fileSlug}.pdf", data.labels.common.dialogTitle) ?: return false
        return runCatching { writeMonthlyProgramPdf(path, data) }.isSuccess
    }

    override suspend fun exportMeetingProgramPng(data: MeetingProgramPdfData): Boolean {
        val path = chooseSavePath("${data.fileSlug}.png", data.labels.common.dialogTitle) ?: return false
        return runCatching { writeMeetingProgramPng(path, data) }.isSuccess
    }

    override suspend fun exportMonthlyProgramPng(data: MonthlyProgramPdfData): Boolean {
        val path = chooseSavePath("${data.fileSlug}.png", data.labels.common.dialogTitle) ?: return false
        return runCatching { writeMonthlyProgramPng(path, data) }.isSuccess
    }

    // Weekly export is unused from the UI but kept for the interface; do not invest more in it.
    override suspend fun exportWeeklyProgram(data: WeeklyProgramPdfData): Boolean = false
    override suspend fun exportWeeklyProgramPng(data: WeeklyProgramPdfData): Boolean = false

    override suspend fun exportCleaningSchedule(data: CleaningSchedulePdfData): Boolean {
        val path = chooseSavePath("${data.fileSlug}.pdf", data.labels.common.dialogTitle) ?: return false
        return runCatching { writeCleaningPdf(path, data) }.isSuccess
    }

    override suspend fun exportCleaningSchedulePng(data: CleaningSchedulePdfData): Boolean {
        val path = chooseSavePath("${data.fileSlug}.png", data.labels.common.dialogTitle) ?: return false
        return runCatching { writeCleaningPng(path, data) }.isSuccess
    }

    override suspend fun exportMidweekProgram(data: MidweekProgramPdfData): Boolean {
        val path = chooseSavePath("${data.fileSlug}.pdf", data.labels.dialogTitle) ?: return false
        return runCatching { writeMidweekProgramPdf(path, data) }.isSuccess
    }

    override suspend fun exportMidweekAssignments(data: MidweekAssignmentsPdfData): Boolean {
        val path = chooseSavePath("${data.fileSlug}.pdf", data.labels.dialogTitle) ?: return false
        return runCatching { writeMidweekAssignmentsPdf(path, data) }.isSuccess
    }

    override suspend fun exportAvSchedule(data: AvSchedulePdfData): Boolean {
        val path = chooseSavePath("${data.fileSlug}.pdf", data.labels.dialogTitle) ?: return false
        return runCatching { writeAvSchedulePdf(path, data) }.isSuccess
    }

    // ─── Monthly program PDF ─────────────────────────────────────────────────

    /** Layout compartilhado com o Android: ver [WeekendMonthlyLayout]. */
    private fun writeMonthlyProgramPdf(path: String, data: MonthlyProgramPdfData) {
        val document = PDDocument()
        PdfBoxCanvas(document).use { canvas ->
            WeekendMonthlyLayout(
                data = data,
                geradoEm = currentTimestamp(),
                iconBytes = resourceBytes("icons/conferencia.png"),
            ).draw(canvas)
        }
        document.save(path)
        document.close()
        openInDesktop(File(path))
    }

    // ─── Individual meeting PDF ──────────────────────────────────────────────

    /** Layout compartilhado com o Android: ver [WeekendMeetingLayout]. */
    private fun writeMeetingProgramPdf(path: String, data: MeetingProgramPdfData) {
        val document = PDDocument()
        PdfBoxCanvas(document).use { canvas ->
            WeekendMeetingLayout(
                data = data,
                geradoEm = currentTimestamp(),
                iconBytes = resourceBytes("icons/conferencia.png"),
            ).draw(canvas)
        }
        document.save(path)
        document.close()
        openInDesktop(File(path))
    }

    // ─── Header helpers ──────────────────────────────────────────────────────

    private fun drawPngHeaderIcon(
        g: Graphics2D,
        iconResource: String,
        width: Int,
        padding: Int,
        size: Int = PngHeaderIconSize,
    ) {
        val icon = PdfExportServiceJvm::class.java.classLoader
            .getResourceAsStream(iconResource)?.use { ImageIO.read(it) } ?: return
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        g.drawImage(icon, width - padding - size, padding, size, size, null)
    }

    /** Ainda usada pelo renderizador PNG, que nao foi migrado para commonMain. */
    private fun monthlyBlockRows(
        line: PdfMeetingLine,
        labels: WeekendPdfStrings,
    ): List<Pair<String, Pair<String, Boolean>>> {
        val vazio = labels.common.aDefinir
        return listOf(
            labels.titulo to ((line.tituloDiscurso ?: labels.common.discursoADefinir) to (line.tituloDiscurso == null)),
            labels.orador to ((line.orador ?: vazio) to (line.orador == null)),
            labels.presidente to ((line.presidente ?: vazio) to (line.presidente == null)),
            labels.dirigente to ((line.dirigenteEstudo ?: vazio) to (line.dirigenteEstudo == null)),
            labels.leitor to ((line.leitor ?: vazio) to (line.leitor == null)),
        )
    }

    /** Layout compartilhado com o Android: ver [WeekendMonthlyPngLayout]. */
    private fun writeMonthlyProgramPng(path: String, data: MonthlyProgramPdfData) {
        val layout = WeekendMonthlyPngLayout(
            data = data,
            geradoEm = currentTimestamp(),
            iconBytes = resourceBytes("icons/conferencia.png"),
        )
        // Superficie descartavel so para medir o texto antes de saber a altura final.
        val altura = layout.measureBlocks(Java2DCanvas(1, 1, PngColors.PageBg))
        val canvas = Java2DCanvas(PNG_WIDTH, altura, PngColors.PageBg)
        layout.draw(TopDownCanvas(canvas))
        val outFile = File(path)
        canvas.writeTo(outFile)
        openInDesktop(outFile)
    }

    /** Layout compartilhado com o Android: ver [WeekendMeetingPngLayout]. */
    private fun writeMeetingProgramPng(path: String, data: MeetingProgramPdfData) {
        val layout = WeekendMeetingPngLayout(
            data = data,
            geradoEm = currentTimestamp(),
            iconBytes = resourceBytes("icons/conferencia.png"),
        )
        val canvas = Java2DCanvas(PNG_WIDTH, layout.measureHeight(), PngColors.PageBg)
        layout.draw(TopDownCanvas(canvas))
        val outFile = File(path)
        canvas.writeTo(outFile)
        openInDesktop(outFile)
    }

    // ─── Cleaning PDF (unchanged) ────────────────────────────────────────────

    /** Layout compartilhado com o Android: ver [CleaningLayout]. */
    private fun writeCleaningPdf(path: String, data: CleaningSchedulePdfData) {
        val document = PDDocument()
        PdfBoxCanvas(document).use { canvas ->
            CleaningLayout(
                data = data,
                geradoEm = currentTimestamp(),
                iconBytes = resourceBytes("icons/limpeza.png"),
            ).draw(canvas)
        }
        document.save(path)
        document.close()
        openInDesktop(File(path))
    }

    private fun resourceBytes(name: String): ByteArray? =
        PdfExportServiceJvm::class.java.classLoader.getResourceAsStream(name)?.use { it.readBytes() }

    /** Layout compartilhado com o Android: ver [CleaningPngLayout]. */
    private fun writeCleaningPng(path: String, data: CleaningSchedulePdfData) {
        val layout = CleaningPngLayout(
            data = data,
            geradoEm = currentTimestamp(),
            iconBytes = resourceBytes("icons/limpeza.png"),
        )
        val canvas = Java2DCanvas(PNG_WIDTH, layout.measureHeight(), PngColors.PageBg)
        layout.draw(TopDownCanvas(canvas))
        val outFile = File(path)
        canvas.writeTo(outFile)
        openInDesktop(outFile)
    }

    // ─── Meio de semana: programa S-140 ──────────────────────────────────────

    /** Layout compartilhado com o Android: ver [MidweekProgramLayout]. */
    private fun writeMidweekProgramPdf(path: String, data: MidweekProgramPdfData) {
        val document = PDDocument()
        PdfBoxCanvas(document, PDRectangle.A4).use { canvas ->
            MidweekProgramLayout(data).draw(canvas)
        }
        document.save(path)
        document.close()
        openInDesktop(File(path))
    }

    private fun writeMidweekAssignmentsPdf(path: String, data: MidweekAssignmentsPdfData) {
        val document = PDDocument()
        // O S-89 e desenhado em A4, nao no Letter padrao do PDFBox.
        PdfBoxCanvas(document, PDRectangle.A4).use { canvas ->
            AssignmentSlipsLayout(data).draw(canvas)
        }
        document.save(path)
        document.close()
        openInDesktop(File(path))
    }

    private fun drawCentered(
        c: PDPageContentStream,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        font: PDFont,
        size: Float,
    ) {
        val tw = textWidth(text, font, size)
        drawText(c, text, x + (width - tw) / 2f, y)
    }

    private fun fitText(text: String, font: PDFont, size: Float, maxWidth: Float): String {
        if (text.isEmpty() || textWidth(text, font, size) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && textWidth("$t…", font, size) > maxWidth) {
            t = t.dropLast(1)
        }
        return "$t…"
    }

    // ─── Audio/video e acomodadores ──────────────────────────────────────────

    /** Layout compartilhado com o Android: ver [AvScheduleLayout]. */
    private fun writeAvSchedulePdf(path: String, data: AvSchedulePdfData) {
        val document = PDDocument()
        PdfBoxCanvas(document).use { canvas ->
            AvScheduleLayout(data, currentTimestamp()).draw(canvas)
        }
        document.save(path)
        document.close()
        openInDesktop(File(path))
    }

    // ─── Common helpers ──────────────────────────────────────────────────────

    private fun drawText(c: PDPageContentStream, text: String, x: Float, y: Float) {
        c.beginText()
        c.newLineAtOffset(x, y)
        c.showText(text)
        c.endText()
    }

    private fun textWidth(text: String, font: PDFont, size: Float): Float {
        return font.getStringWidth(text) / 1000f * size
    }

    private fun wrapText(text: String, font: PDFont, size: Float, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            val widthPt = font.getStringWidth(candidate) / 1000f * size
            if (widthPt <= maxWidth || current.isEmpty()) {
                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            } else {
                lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    private fun currentTimestamp(): String {
        val now = java.time.LocalDateTime.now()
        val formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        return now.format(formatter)
    }

    private fun openInDesktop(file: File) {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(file)
        }
    }

    private fun chooseSavePath(defaultName: String, title: String = "Salvar como"): String? {
        return runOnEdt {
            val owner = KeyboardFocusManager
                .getCurrentKeyboardFocusManager()
                .activeWindow as? Frame
            val dialog = FileDialog(owner, title, FileDialog.SAVE)
            dialog.file = defaultName
            dialog.isVisible = true
            val dir = dialog.directory ?: return@runOnEdt null
            val file = dialog.file ?: return@runOnEdt null
            File(dir, file).absolutePath
        }
    }

    private fun <T> runOnEdt(block: () -> T): T {
        if (EventQueue.isDispatchThread()) return block()
        val task = FutureTask { block() }
        EventQueue.invokeAndWait(task)
        return task.get()
    }
}

actual fun createPdfExportService(): PdfExportService = PdfExportServiceJvm()
