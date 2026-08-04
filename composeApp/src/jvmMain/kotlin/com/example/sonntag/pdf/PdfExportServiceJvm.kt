package com.example.sonntag.pdf

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

    override fun exportMeetingProgram(data: MeetingProgramPdfData): Boolean {
        val path = chooseSavePath("${data.fileSlug}.pdf", data.labels.common.dialogTitle) ?: return false
        return runCatching { writeMeetingProgramPdf(path, data) }.isSuccess
    }

    override fun exportMonthlyProgram(data: MonthlyProgramPdfData): Boolean {
        val path = chooseSavePath("${data.fileSlug}.pdf", data.labels.common.dialogTitle) ?: return false
        return runCatching { writeMonthlyProgramPdf(path, data) }.isSuccess
    }

    override fun exportMeetingProgramPng(data: MeetingProgramPdfData): Boolean {
        val path = chooseSavePath("${data.fileSlug}.png", data.labels.common.dialogTitle) ?: return false
        return runCatching { writeMeetingProgramPng(path, data) }.isSuccess
    }

    override fun exportMonthlyProgramPng(data: MonthlyProgramPdfData): Boolean {
        val path = chooseSavePath("${data.fileSlug}.png", data.labels.common.dialogTitle) ?: return false
        return runCatching { writeMonthlyProgramPng(path, data) }.isSuccess
    }

    // Weekly export is unused from the UI but kept for the interface; do not invest more in it.
    override fun exportWeeklyProgram(data: WeeklyProgramPdfData): Boolean = false
    override fun exportWeeklyProgramPng(data: WeeklyProgramPdfData): Boolean = false

    override fun exportCleaningSchedule(data: CleaningSchedulePdfData): Boolean {
        val path = chooseSavePath("${data.fileSlug}.pdf", data.labels.common.dialogTitle) ?: return false
        return runCatching { writeCleaningPdf(path, data) }.isSuccess
    }

    override fun exportCleaningSchedulePng(data: CleaningSchedulePdfData): Boolean {
        val path = chooseSavePath("${data.fileSlug}.png", data.labels.common.dialogTitle) ?: return false
        return runCatching { writeCleaningPng(path, data) }.isSuccess
    }

    override fun exportMidweekProgram(data: MidweekProgramPdfData): Boolean {
        val path = chooseSavePath("${data.fileSlug}.pdf", data.labels.dialogTitle) ?: return false
        return runCatching { writeMidweekProgramPdf(path, data) }.isSuccess
    }

    override fun exportMidweekAssignments(data: MidweekAssignmentsPdfData): Boolean {
        val path = chooseSavePath("${data.fileSlug}.pdf", data.labels.dialogTitle) ?: return false
        return runCatching { writeMidweekAssignmentsPdf(path, data) }.isSuccess
    }

    override fun exportAvSchedule(data: AvSchedulePdfData): Boolean {
        val path = chooseSavePath("${data.fileSlug}.pdf", data.labels.dialogTitle) ?: return false
        return runCatching { writeAvSchedulePdf(path, data) }.isSuccess
    }

    // ─── Monthly program PDF ─────────────────────────────────────────────────

    private fun writeMonthlyProgramPdf(path: String, data: MonthlyProgramPdfData) {
        val document = PDDocument()
        val fonts = PdfFonts.load(document)
        val pageSize = PDPage().mediaBox
        val pageWidth = pageSize.width
        val pageHeight = pageSize.height
        val marginLeft = 50f
        val marginRight = 50f
        val marginBottom = 60f
        val contentWidth = pageWidth - marginLeft - marginRight
        val blockGap = 16f

        val pages = mutableListOf<PDPage>()
        val streams = mutableListOf<PDPageContentStream>()

        fun startPage(isFirst: Boolean): Float {
            val page = PDPage()
            document.addPage(page)
            pages.add(page)
            val stream = PDPageContentStream(document, page)
            streams.add(stream)
            return if (isFirst) {
                drawHeaderBand(
                    c = stream,
                    document = document,
                    pageHeight = pageHeight,
                    marginLeft = marginLeft,
                    contentWidth = contentWidth,
                    fonts = fonts,
                    congregacao = data.congregacao,
                    title = data.labels.tituloMensal,
                    subtitle = data.mesLabel,
                    iconResource = "icons/conferencia.png",
                )
            } else {
                drawCompactHeader(
                    c = stream,
                    pageHeight = pageHeight,
                    marginLeft = marginLeft,
                    contentWidth = contentWidth,
                    fonts = fonts,
                    text = "${data.labels.tituloMensal} — ${data.mesLabel}",
                )
            }
        }

        var y = startPage(isFirst = true)
        val initialY = y

        if (data.reunioes.isEmpty()) {
            streams.last().setNonStrokingColor(ColorMuted)
            streams.last().setFont(fonts.italic, 12f)
            drawText(streams.last(), data.labels.vazio, marginLeft, y - 14f)
        } else {
            data.reunioes.forEach { line ->
                val blockHeight = measureMonthlyBlockHeight(line, fonts, contentWidth, data.labels)
                if (y - blockHeight < marginBottom + 24f) {
                    streams.last().close()
                    y = startPage(isFirst = false)
                }
                drawMonthlyBlock(
                    c = streams.last(),
                    fonts = fonts,
                    line = line,
                    x = marginLeft,
                    y = y,
                    width = contentWidth,
                    labels = data.labels,
                )
                y -= blockHeight + blockGap
            }
        }

        streams.last().close()

        // Footers (right-aligned timestamp + left-aligned page numbers) once we know total
        val totalPages = pages.size
        val timestamp = data.labels.common.geradoEm(currentTimestamp())
        pages.forEachIndexed { idx, page ->
            PDPageContentStream(
                document, page,
                PDPageContentStream.AppendMode.APPEND, true, true,
            ).use { c ->
                c.setNonStrokingColor(ColorMuted)
                c.setFont(fonts.regular, 8f)
                drawText(c, data.labels.common.pagina(idx + 1, totalPages), marginLeft, marginBottom - 14f)
                val ts = textWidth(timestamp, fonts.regular, 8f)
                drawText(c, timestamp, marginLeft + contentWidth - ts, marginBottom - 14f)
            }
        }

        // Suppress unused variable warning while keeping the intent obvious
        @Suppress("UNUSED_VARIABLE") val _firstPageStart = initialY

        document.save(path)
        document.close()
        openInDesktop(File(path))
    }

    // ─── Individual meeting PDF ──────────────────────────────────────────────

    private fun writeMeetingProgramPdf(path: String, data: MeetingProgramPdfData) {
        val document = PDDocument()
        val fonts = PdfFonts.load(document)
        val page = PDPage()
        document.addPage(page)
        val pageWidth = page.mediaBox.width
        val pageHeight = page.mediaBox.height
        val marginLeft = 70f
        val marginRight = 70f
        val marginBottom = 60f
        val contentWidth = pageWidth - marginLeft - marginRight

        PDPageContentStream(document, page).use { c ->
            var y = drawHeaderBand(
                c = c,
                document = document,
                pageHeight = pageHeight,
                marginLeft = marginLeft,
                contentWidth = contentWidth,
                fonts = fonts,
                congregacao = data.congregacao,
                title = data.labels.tituloReuniao,
                subtitle = data.dateLabel,
                iconResource = "icons/conferencia.png",
            )

            y -= 30f

            // Single large block centered horizontally — fontes maiores
            val labelFontSize = 12f
            val valueFontSize = 14f
            val rowVertPad = 10f
            val rowGap = 1f // border line
            val labelColWidth = 170f
            val labelValueGap = 16f
            val valueColWidth = contentWidth - labelColWidth - labelValueGap
            val blockHeaderHeight = 36f

            // Block header (shaded title row)
            c.setNonStrokingColor(ColorBlockHeader)
            c.addRect(marginLeft, y - blockHeaderHeight, contentWidth, blockHeaderHeight)
            c.fill()
            c.setNonStrokingColor(ColorTitle)
            c.setFont(fonts.bold, 14f)
            drawText(c, "${data.dateLabel} — ${data.hora}", marginLeft + 14f, y - 22f)
            y -= blockHeaderHeight

            val rows = listOf(
                data.labels.titulo to (data.tituloDiscurso ?: data.labels.common.discursoADefinir),
                data.labels.orador to (data.orador ?: data.labels.common.aDefinir),
                data.labels.presidente to (data.presidente ?: data.labels.common.aDefinir),
                data.labels.dirigente to (data.dirigenteEstudo ?: data.labels.common.aDefinir),
                data.labels.leitor to (data.leitor ?: data.labels.common.aDefinir),
            )
            val placeholders = listOf(
                data.tituloDiscurso == null,
                data.orador == null,
                data.presidente == null,
                data.dirigenteEstudo == null,
                data.leitor == null,
            )

            rows.forEachIndexed { idx, (label, value) ->
                val isPlaceholder = placeholders[idx]
                val valueFont: PDFont = if (isPlaceholder) fonts.italic else fonts.regular
                val valueLines = wrapText(value, valueFont, valueFontSize, valueColWidth)
                val rowHeight = rowVertPad * 2f + valueLines.size * (valueFontSize + 4f)
                val rowTop = y
                val rowBottom = y - rowHeight

                // Label (right-aligned)
                c.setNonStrokingColor(ColorMuted)
                c.setFont(fonts.regular, labelFontSize)
                val labelWidth = textWidth(label, fonts.regular, labelFontSize)
                val labelBaseline = rowTop - rowVertPad - labelFontSize
                drawText(c, label, marginLeft + labelColWidth - labelWidth, labelBaseline)

                // Value
                if (isPlaceholder) c.setNonStrokingColor(ColorMuted) else c.setNonStrokingColor(ColorTitle)
                c.setFont(valueFont, valueFontSize)
                valueLines.forEachIndexed { i, vline ->
                    val baseline = rowTop - rowVertPad - valueFontSize - i * (valueFontSize + 4f)
                    drawText(c, vline, marginLeft + labelColWidth + labelValueGap, baseline)
                }

                // Bottom border
                c.setStrokingColor(ColorSeparator)
                c.setLineWidth(0.5f)
                c.moveTo(marginLeft, rowBottom); c.lineTo(marginLeft + contentWidth, rowBottom); c.stroke()

                y = rowBottom - rowGap
            }

            // Footer
            val timestamp = data.labels.common.geradoEm(currentTimestamp())
            c.setNonStrokingColor(ColorMuted)
            c.setFont(fonts.regular, 8f)
            val ts = textWidth(timestamp, fonts.regular, 8f)
            drawText(c, timestamp, marginLeft + contentWidth - ts, marginBottom - 14f)
        }

        document.save(path)
        document.close()
        openInDesktop(File(path))
    }

    // ─── Header helpers ──────────────────────────────────────────────────────

    private fun drawHeaderBand(
        c: PDPageContentStream,
        document: PDDocument,
        pageHeight: Float,
        marginLeft: Float,
        contentWidth: Float,
        fonts: PdfFonts,
        congregacao: String,
        title: String,
        subtitle: String,
        iconResource: String,
    ): Float {
        val headerBandTop = pageHeight - 50f
        val headerBandHeight = 80f
        val headerBandBottom = headerBandTop - headerBandHeight

        // Icone grande na borda direita da faixa: identifica o tipo de documento de
        // relance, sem disputar espaco horizontal com o nome da congregacao.
        val iconSize = 56f
        val iconBytes = PdfExportServiceJvm::class.java.classLoader
            .getResourceAsStream(iconResource)?.use { it.readBytes() }
        if (iconBytes != null) {
            val image = PDImageXObject.createFromByteArray(document, iconBytes, "header-icon")
            c.drawImage(
                image,
                marginLeft + contentWidth - iconSize,
                headerBandBottom + (headerBandHeight - iconSize) / 2f,
                iconSize,
                iconSize,
            )
        }

        val line1Baseline = headerBandTop - 14f
        c.setNonStrokingColor(ColorTitle)
        c.setFont(fonts.bold, 16f)
        drawText(c, congregacao, marginLeft, line1Baseline)

        c.setFont(fonts.bold, 20f)
        drawText(c, title, marginLeft, headerBandTop - 44f)

        c.setNonStrokingColor(ColorMuted)
        c.setFont(fonts.regular, 14f)
        drawText(c, subtitle, marginLeft, headerBandTop - 66f)

        c.setStrokingColor(ColorBorder)
        c.setLineWidth(0.5f)
        c.moveTo(marginLeft, headerBandBottom); c.lineTo(marginLeft + contentWidth, headerBandBottom); c.stroke()

        return headerBandBottom - 16f
    }

    /**
     * Icone grande no canto superior direito do PNG — mesmo papel do icone da faixa
     * do PDF: dizer de relance de que documento se trata.
     */
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

    private fun drawCompactHeader(
        c: PDPageContentStream,
        pageHeight: Float,
        marginLeft: Float,
        contentWidth: Float,
        fonts: PdfFonts,
        text: String,
    ): Float {
        val top = pageHeight - 40f
        c.setNonStrokingColor(ColorMuted)
        c.setFont(fonts.regular, 11f)
        drawText(c, text, marginLeft, top)
        c.setStrokingColor(ColorBorder)
        c.setLineWidth(0.5f)
        c.moveTo(marginLeft, top - 8f); c.lineTo(marginLeft + contentWidth, top - 8f); c.stroke()
        return top - 24f
    }

    // ─── Monthly block helpers ───────────────────────────────────────────────

    private fun monthlyBlockRows(
        line: PdfMeetingLine,
        labels: WeekendPdfStrings,
    ): List<Pair<String, Pair<String, Boolean>>> {
        // Returns (label, (value, isPlaceholder))
        val vazio = labels.common.aDefinir
        return listOf(
            labels.titulo to ((line.tituloDiscurso ?: labels.common.discursoADefinir) to (line.tituloDiscurso == null)),
            labels.orador to ((line.orador ?: vazio) to (line.orador == null)),
            labels.presidente to ((line.presidente ?: vazio) to (line.presidente == null)),
            labels.dirigente to ((line.dirigenteEstudo ?: vazio) to (line.dirigenteEstudo == null)),
            labels.leitor to ((line.leitor ?: vazio) to (line.leitor == null)),
        )
    }

    private fun measureMonthlyBlockHeight(
        line: PdfMeetingLine,
        fonts: PdfFonts,
        contentWidth: Float,
        labels: WeekendPdfStrings,
    ): Float {
        val blockHeaderHeight = 28f
        val labelColWidth = 140f
        val labelValueGap = 12f
        val valueColWidth = contentWidth - labelColWidth - labelValueGap
        val valueFontSize = 11f
        val rowVertPad = 6f
        val lineGap = 4f
        var h = blockHeaderHeight
        monthlyBlockRows(line, labels).forEach { (_, valueAndPlaceholder) ->
            val (value, isPlaceholder) = valueAndPlaceholder
            val font: PDFont = if (isPlaceholder) fonts.italic else fonts.regular
            val lines = wrapText(value, font, valueFontSize, valueColWidth)
            h += rowVertPad * 2f + lines.size * (valueFontSize + lineGap) + 0.5f
        }
        return h
    }

    private fun drawMonthlyBlock(
        c: PDPageContentStream,
        fonts: PdfFonts,
        line: PdfMeetingLine,
        x: Float,
        y: Float,
        width: Float,
        labels: WeekendPdfStrings,
    ) {
        val blockHeaderHeight = 28f
        val labelFontSize = 10f
        val valueFontSize = 11f
        val labelColWidth = 140f
        val labelValueGap = 12f
        val valueColWidth = width - labelColWidth - labelValueGap
        val rowVertPad = 6f
        val lineGap = 4f

        // Shaded title row
        c.setNonStrokingColor(ColorBlockHeader)
        c.addRect(x, y - blockHeaderHeight, width, blockHeaderHeight)
        c.fill()
        c.setNonStrokingColor(ColorTitle)
        c.setFont(fonts.bold, 13f)
        drawText(c, "${line.dateLabel} — ${line.hora}", x + 12f, y - 19f)
        var cursor = y - blockHeaderHeight

        monthlyBlockRows(line, labels).forEach { (label, valueAndPlaceholder) ->
            val (value, isPlaceholder) = valueAndPlaceholder
            val valueFont: PDFont = if (isPlaceholder) fonts.italic else fonts.regular
            val valueLines = wrapText(value, valueFont, valueFontSize, valueColWidth)
            val rowHeight = rowVertPad * 2f + valueLines.size * (valueFontSize + lineGap)
            val rowTop = cursor
            val rowBottom = cursor - rowHeight

            // Label (right-aligned within the label column)
            c.setNonStrokingColor(ColorMuted)
            c.setFont(fonts.regular, labelFontSize)
            val labelWidth = textWidth(label, fonts.regular, labelFontSize)
            val labelBaseline = rowTop - rowVertPad - labelFontSize
            drawText(c, label, x + labelColWidth - labelWidth, labelBaseline)

            // Value
            if (isPlaceholder) c.setNonStrokingColor(ColorMuted) else c.setNonStrokingColor(ColorTitle)
            c.setFont(valueFont, valueFontSize)
            valueLines.forEachIndexed { i, vline ->
                val baseline = rowTop - rowVertPad - valueFontSize - i * (valueFontSize + lineGap)
                drawText(c, vline, x + labelColWidth + labelValueGap, baseline)
            }

            // Separator between rows
            c.setStrokingColor(ColorSeparator)
            c.setLineWidth(0.5f)
            c.moveTo(x + 8f, rowBottom); c.lineTo(x + width - 8f, rowBottom); c.stroke()

            cursor = rowBottom
        }
    }

    // ─── PNG renderers ───────────────────────────────────────────────────────

    private fun writeMonthlyProgramPng(path: String, data: MonthlyProgramPdfData) {
        val width = 1080
        val padding = 64
        val titleFont = Font("SansSerif", Font.BOLD, 50)
        val subtitleFont = Font("SansSerif", Font.BOLD, 38)
        val monthFont = Font("SansSerif", Font.PLAIN, 28)
        val blockHeaderFont = Font("SansSerif", Font.BOLD, 32)
        val labelFont = Font("SansSerif", Font.BOLD, 26)
        val valueFont = Font("SansSerif", Font.PLAIN, 30)
        val valueFontItalic = Font("SansSerif", Font.ITALIC, 30)
        val footerFont = Font("SansSerif", Font.ITALIC, 20)

        val headerHeight = padding + titleFont.size + 30 + subtitleFont.size + 12 + monthFont.size + 36
        val blockGap = 24
        val rowVertPad = 14
        val rowLineGap = 8
        val cardInnerPad = 28
        val cardHeaderH = 64
        val cardWidth = width - padding * 2
        val labelColW = 280
        val valueColX = padding + cardInnerPad + labelColW
        val valueColW = cardWidth - cardInnerPad * 2 - labelColW

        // Pre-measure: need a Graphics2D to compute string widths
        val measureImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val mg = measureImage.createGraphics()
        mg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        fun wrapPng(text: String, font: Font, maxW: Int): List<String> {
            mg.font = font
            val fm = mg.fontMetrics
            if (fm.stringWidth(text) <= maxW) return listOf(text)
            val words = text.split(" ")
            val lines = mutableListOf<String>()
            var current = StringBuilder()
            for (w in words) {
                val candidate = if (current.isEmpty()) w else "$current $w"
                if (fm.stringWidth(candidate) <= maxW || current.isEmpty()) {
                    if (current.isNotEmpty()) current.append(' ')
                    current.append(w)
                } else {
                    lines.add(current.toString())
                    current = StringBuilder(w)
                }
            }
            if (current.isNotEmpty()) lines.add(current.toString())
            return lines
        }

        data class RenderRow(val label: String, val lines: List<String>, val placeholder: Boolean)
        data class RenderBlock(val header: String, val rows: List<RenderRow>, val height: Int)

        fun blockForLine(line: PdfMeetingLine): RenderBlock {
            val rows = monthlyBlockRows(line, data.labels).map { (label, vp) ->
                val (value, isPlaceholder) = vp
                val font = if (isPlaceholder) valueFontItalic else valueFont
                RenderRow(label, wrapPng(value, font, valueColW), isPlaceholder)
            }
            var h = cardHeaderH
            rows.forEach { r ->
                h += rowVertPad * 2 + r.lines.size * (valueFont.size + 4) + rowLineGap
            }
            return RenderBlock("${line.dateLabel} — ${line.hora}", rows, h)
        }

        val blocks = data.reunioes.map { blockForLine(it) }
        mg.dispose()

        val blocksTotalHeight = if (blocks.isEmpty()) 180
        else blocks.sumOf { it.height } + (blocks.size - 1) * blockGap

        val height = (headerHeight + blocksTotalHeight + padding + footerFont.size + 24).coerceAtLeast(1350)

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        g.color = ColorPageBg
        g.fillRect(0, 0, width, height)

        // Header
        var y = padding + titleFont.size
        g.color = ColorTitle
        drawPngHeaderIcon(g, "icons/conferencia.png", width, padding)
        g.font = titleFont
        g.drawString(data.congregacao, padding, y)

        y += 30
        g.color = Color(30, 58, 95)
        g.font = subtitleFont
        y += subtitleFont.size
        g.drawString(data.labels.tituloMensal, padding, y)

        y += 12 + monthFont.size
        g.color = ColorMuted
        g.font = monthFont
        g.drawString(data.mesLabel, padding, y)
        y += 28

        val blockX = padding

        if (blocks.isEmpty()) {
            g.color = Color(255, 255, 255)
            g.fillRoundRect(blockX, y, cardWidth, 140, 24, 24)
            g.color = ColorMuted
            g.font = valueFontItalic
            g.drawString(data.labels.vazio, blockX + 32, y + 80)
        } else {
            blocks.forEach { block ->
                val h = block.height
                g.color = Color(255, 255, 255)
                g.fillRoundRect(blockX, y, cardWidth, h, 24, 24)
                g.color = ColorBorder
                g.drawRoundRect(blockX, y, cardWidth, h, 24, 24)

                g.color = ColorBlockHeader
                g.fillRoundRect(blockX, y, cardWidth, cardHeaderH, 24, 24)
                g.color = ColorTitle
                g.font = blockHeaderFont
                g.drawString(block.header, blockX + cardInnerPad, y + cardHeaderH - 22)

                var rowY = y + cardHeaderH
                block.rows.forEach { r ->
                    val rowH = rowVertPad * 2 + r.lines.size * (valueFont.size + 4) + rowLineGap
                    val labelBaseline = rowY + rowVertPad + labelFont.size - 2
                    g.color = ColorMuted
                    g.font = labelFont
                    g.drawString(r.label, blockX + cardInnerPad, labelBaseline)

                    g.color = if (r.placeholder) ColorMuted else ColorTitle
                    g.font = if (r.placeholder) valueFontItalic else valueFont
                    r.lines.forEachIndexed { i, vline ->
                        val baseline = rowY + rowVertPad + valueFont.size + i * (valueFont.size + 4)
                        g.drawString(vline, valueColX, baseline)
                    }
                    rowY += rowH
                }
                y += h + blockGap
            }
        }

        g.color = ColorMuted
        g.font = footerFont
        val footerText = data.labels.common.geradoEm(currentTimestamp())
        val fw = g.fontMetrics.stringWidth(footerText)
        g.drawString(footerText, width - padding - fw, height - padding)

        g.dispose()
        val outFile = File(path)
        ImageIO.write(image, "png", outFile)
        openInDesktop(outFile)
    }

    private fun writeMeetingProgramPng(path: String, data: MeetingProgramPdfData) {
        // Adapt: reuse the monthly PNG by wrapping a single line, but adjust header title.
        val width = 1080
        val padding = 64
        val titleFont = Font("SansSerif", Font.BOLD, 50)
        val subtitleFont = Font("SansSerif", Font.BOLD, 38)
        val monthFont = Font("SansSerif", Font.PLAIN, 28)
        val blockHeaderFont = Font("SansSerif", Font.BOLD, 34)
        val labelFont = Font("SansSerif", Font.BOLD, 28)
        val valueFont = Font("SansSerif", Font.PLAIN, 34)
        val valueFontItalic = Font("SansSerif", Font.ITALIC, 34)
        val footerFont = Font("SansSerif", Font.ITALIC, 20)

        val headerHeight = padding + titleFont.size + 30 + subtitleFont.size + 12 + monthFont.size + 36
        val rowVertPad = 18
        val rowLineGap = 10
        val cardInnerPad = 32
        val cardHeaderH = 76
        val rowH = rowVertPad * 2 + valueFont.size + rowLineGap
        val blockH = cardHeaderH + 5 * rowH

        val height = (headerHeight + blockH + padding + footerFont.size + 24).coerceAtLeast(1350)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = ColorPageBg
        g.fillRect(0, 0, width, height)

        var y = padding + titleFont.size
        g.color = ColorTitle
        drawPngHeaderIcon(g, "icons/conferencia.png", width, padding)
        g.font = titleFont
        g.drawString(data.congregacao, padding, y)

        y += 30
        g.color = Color(30, 58, 95)
        g.font = subtitleFont
        y += subtitleFont.size
        g.drawString(data.labels.tituloReuniao, padding, y)

        y += 12 + monthFont.size
        g.color = ColorMuted
        g.font = monthFont
        g.drawString(data.dateLabel, padding, y)
        y += 28

        val blockX = padding
        val blockWidth = width - padding * 2

        g.color = Color(255, 255, 255)
        g.fillRoundRect(blockX, y, blockWidth, blockH, 24, 24)
        g.color = ColorBorder
        g.drawRoundRect(blockX, y, blockWidth, blockH, 24, 24)

        g.color = ColorBlockHeader
        g.fillRoundRect(blockX, y, blockWidth, cardHeaderH, 24, 24)
        g.color = ColorTitle
        g.font = blockHeaderFont
        g.drawString("${data.dateLabel} — ${data.hora}", blockX + cardInnerPad, y + cardHeaderH - 26)

        val rows = listOf(
            data.labels.titulo to ((data.tituloDiscurso ?: data.labels.common.discursoADefinir) to (data.tituloDiscurso == null)),
            data.labels.orador to ((data.orador ?: data.labels.common.aDefinir) to (data.orador == null)),
            data.labels.presidente to ((data.presidente ?: data.labels.common.aDefinir) to (data.presidente == null)),
            data.labels.dirigente to ((data.dirigenteEstudo ?: data.labels.common.aDefinir) to (data.dirigenteEstudo == null)),
            data.labels.leitor to ((data.leitor ?: data.labels.common.aDefinir) to (data.leitor == null)),
        )

        var rowY = y + cardHeaderH
        rows.forEach { (label, vp) ->
            val (value, isPlaceholder) = vp
            g.color = ColorMuted
            g.font = labelFont
            g.drawString(label, blockX + cardInnerPad, rowY + rowVertPad + labelFont.size - 2)
            g.color = if (isPlaceholder) ColorMuted else ColorTitle
            g.font = if (isPlaceholder) valueFontItalic else valueFont
            g.drawString(value, blockX + cardInnerPad + 320, rowY + rowVertPad + valueFont.size)
            rowY += rowH
        }

        g.color = ColorMuted
        g.font = footerFont
        val footerText = data.labels.common.geradoEm(currentTimestamp())
        val fw = g.fontMetrics.stringWidth(footerText)
        g.drawString(footerText, width - padding - fw, height - padding)

        g.dispose()
        val outFile = File(path)
        ImageIO.write(image, "png", outFile)
        openInDesktop(outFile)
    }

    // ─── Cleaning PDF (unchanged) ────────────────────────────────────────────

    private fun writeCleaningPdf(path: String, data: CleaningSchedulePdfData) {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)
        val fonts = PdfFonts.load(document)

        val pageWidth = page.mediaBox.width
        val pageHeight = page.mediaBox.height
        val marginLeft = 50f
        val marginBottom = 50f
        val contentWidth = pageWidth - marginLeft - 50f

        val colWeek = contentWidth * 0.35f
        val colMeetings = contentWidth * 0.35f
        val colGroup = contentWidth * 0.30f

        val cellPad = 8f
        val rowMinHeight = 26f
        val rowLineHeight = 14f

        val headerColor = Color(232, 235, 240)
        val zebraColor = Color(247, 248, 250)

        PDPageContentStream(document, page).use { c ->
            var y = drawHeaderBand(
                c = c,
                document = document,
                pageHeight = pageHeight,
                marginLeft = marginLeft,
                contentWidth = contentWidth,
                fonts = fonts,
                congregacao = data.congregacao,
                title = data.labels.title,
                subtitle = data.mesLabel,
                iconResource = "icons/limpeza.png",
            )
            y -= 4f

            val xWeek = marginLeft
            val xMeetings = marginLeft + colWeek
            val xGroup = marginLeft + colWeek + colMeetings

            val headerRowHeight = 24f
            c.setNonStrokingColor(headerColor)
            c.addRect(marginLeft, y - headerRowHeight, contentWidth, headerRowHeight)
            c.fill()
            c.setNonStrokingColor(ColorTitle)
            c.setFont(fonts.bold, 11f)
            val headerBaseline = y - cellPad - 9f
            drawText(c, data.labels.semana, xWeek + cellPad, headerBaseline)
            drawText(c, data.labels.reunioes, xMeetings + cellPad, headerBaseline)
            drawText(c, data.labels.grupo, xGroup + cellPad, headerBaseline)
            c.setStrokingColor(ColorBorder)
            c.setLineWidth(0.5f)
            val tableHeaderBottom = y - headerRowHeight
            c.moveTo(marginLeft, tableHeaderBottom); c.lineTo(marginLeft + contentWidth, tableHeaderBottom); c.stroke()
            y = tableHeaderBottom

            if (data.semanas.isEmpty()) {
                val rowHeight = rowMinHeight
                val rowBottom = y - rowHeight
                c.setNonStrokingColor(ColorMuted)
                c.setFont(fonts.italic, 11f)
                drawText(c, data.labels.vazio, marginLeft + cellPad, y - cellPad - 9f)
                c.setStrokingColor(ColorBorder)
                c.setLineWidth(0.3f)
                c.moveTo(marginLeft, rowBottom); c.lineTo(marginLeft + contentWidth, rowBottom); c.stroke()
                y = rowBottom
            } else {
                data.semanas.forEachIndexed { idx, row ->
                    val weekLines = wrapText(row.periodo, fonts.regular, 11f, colWeek - cellPad * 2f)
                    val meetLines = wrapText(row.diasReuniao, fonts.regular, 11f, colMeetings - cellPad * 2f)
                    val isPlaceholder = row.grupoResponsavel.isNullOrBlank()
                    val groupText = row.grupoResponsavel?.takeIf { it.isNotBlank() } ?: data.labels.common.aDefinir
                    val groupFont: PDFont = if (isPlaceholder) fonts.italic else fonts.regular
                    val groupLines = wrapText(groupText, groupFont, 11f, colGroup - cellPad * 2f)
                    val maxLines = maxOf(weekLines.size, meetLines.size, groupLines.size)
                    val rowHeight = maxOf(rowMinHeight, maxLines * rowLineHeight + cellPad * 2f)
                    val rowBottom = y - rowHeight

                    if (idx % 2 == 1) {
                        c.setNonStrokingColor(zebraColor)
                        c.addRect(marginLeft, rowBottom, contentWidth, rowHeight)
                        c.fill()
                    }

                    val textBaseline = y - cellPad - 9f
                    c.setNonStrokingColor(ColorTitle)
                    c.setFont(fonts.regular, 11f)
                    weekLines.forEachIndexed { i, line ->
                        drawText(c, line, xWeek + cellPad, textBaseline - i * rowLineHeight)
                    }
                    meetLines.forEachIndexed { i, line ->
                        drawText(c, line, xMeetings + cellPad, textBaseline - i * rowLineHeight)
                    }
                    if (isPlaceholder) c.setNonStrokingColor(ColorMuted)
                    c.setFont(groupFont, 11f)
                    groupLines.forEachIndexed { i, line ->
                        drawText(c, line, xGroup + cellPad, textBaseline - i * rowLineHeight)
                    }

                    c.setStrokingColor(ColorBorder)
                    c.setLineWidth(0.3f)
                    c.moveTo(marginLeft, rowBottom); c.lineTo(marginLeft + contentWidth, rowBottom); c.stroke()
                    y = rowBottom
                }
            }

            val footerText = data.labels.common.geradoEm(currentTimestamp())
            c.setNonStrokingColor(ColorMuted)
            c.setFont(fonts.regular, 8f)
            val footerWidth = textWidth(footerText, fonts.regular, 8f)
            drawText(c, footerText, marginLeft + contentWidth - footerWidth, marginBottom - 10f)
        }

        document.save(path)
        document.close()
        openInDesktop(File(path))
    }

    private fun writeCleaningPng(path: String, data: CleaningSchedulePdfData) {
        val width = 1080
        val padding = 64
        val titleFont = Font("SansSerif", Font.BOLD, 52)
        val subtitleFont = Font("SansSerif", Font.BOLD, 38)
        val monthFont = Font("SansSerif", Font.PLAIN, 28)
        val cardLabelFont = Font("SansSerif", Font.BOLD, 24)
        val cardValueFont = Font("SansSerif", Font.PLAIN, 30)
        val cardValueItalic = Font("SansSerif", Font.ITALIC, 30)
        val periodFont = Font("SansSerif", Font.BOLD, 34)
        val footerFont = Font("SansSerif", Font.ITALIC, 20)

        val cardInnerPad = 32
        val cardGap = 20
        val cardLineGap = 12

        val cards = data.semanas
        val approxCardHeight = cardInnerPad * 2 +
            periodFont.size + cardLineGap +
            cardLabelFont.size + 6 + cardValueFont.size + cardLineGap +
            cardLabelFont.size + 6 + cardValueFont.size

        val cardsHeight = if (cards.isEmpty()) 180 else cards.size * approxCardHeight + (cards.size - 1) * cardGap
        val headerHeight = padding + titleFont.size + 36 + subtitleFont.size + 12 + monthFont.size + 36
        val footerHeight = padding + footerFont.size
        val height = (headerHeight + cardsHeight + footerHeight).coerceAtLeast(1350)

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        g.color = ColorPageBg
        g.fillRect(0, 0, width, height)

        var y = padding + titleFont.size
        g.color = ColorTitle
        drawPngHeaderIcon(g, "icons/limpeza.png", width, padding)
        g.font = titleFont
        g.drawString(data.congregacao, padding, y)

        y += 36
        g.color = Color(30, 58, 95)
        g.font = subtitleFont
        y += subtitleFont.size
        g.drawString(data.labels.title, padding, y)

        y += 12 + monthFont.size
        g.color = ColorMuted
        g.font = monthFont
        g.drawString(data.mesLabel, padding, y)
        y += 28

        val cardX = padding
        val cardWidth = width - padding * 2

        if (cards.isEmpty()) {
            g.color = Color(255, 255, 255)
            g.fillRoundRect(cardX, y, cardWidth, 140, 24, 24)
            g.color = ColorMuted
            g.font = cardValueItalic
            g.drawString(data.labels.vazio, cardX + 32, y + 80)
            y += 140
        } else {
            cards.forEach { row ->
                val cardTop = y
                val cardBottom = y + approxCardHeight
                g.color = Color(255, 255, 255)
                g.fillRoundRect(cardX, cardTop, cardWidth, approxCardHeight, 24, 24)
                g.color = ColorBorder
                g.drawRoundRect(cardX, cardTop, cardWidth, approxCardHeight, 24, 24)

                var ty = cardTop + cardInnerPad + periodFont.size
                g.color = Color(30, 58, 95)
                g.font = periodFont
                g.drawString(row.periodo, cardX + cardInnerPad, ty)

                ty += cardLineGap + cardLabelFont.size
                g.color = ColorMuted
                g.font = cardLabelFont
                g.drawString(data.labels.diasReuniao, cardX + cardInnerPad, ty)
                ty += 6 + cardValueFont.size
                g.color = ColorTitle
                g.font = cardValueFont
                g.drawString(row.diasReuniao, cardX + cardInnerPad, ty)

                ty += cardLineGap + cardLabelFont.size
                g.color = ColorMuted
                g.font = cardLabelFont
                g.drawString(data.labels.grupoResponsavel, cardX + cardInnerPad, ty)
                ty += 6 + cardValueFont.size
                val isPlaceholder = row.grupoResponsavel.isNullOrBlank()
                g.color = if (isPlaceholder) ColorMuted else ColorTitle
                g.font = if (isPlaceholder) cardValueItalic else cardValueFont
                g.drawString(row.grupoResponsavel?.takeIf { it.isNotBlank() } ?: data.labels.common.aDefinir, cardX + cardInnerPad, ty)

                y = cardBottom + cardGap
            }
        }

        g.color = ColorMuted
        g.font = footerFont
        val footerText = data.labels.common.geradoEm(currentTimestamp())
        val fw = g.fontMetrics.stringWidth(footerText)
        g.drawString(footerText, width - padding - fw, height - padding)

        g.dispose()
        val outFile = File(path)
        ImageIO.write(image, "png", outFile)
        openInDesktop(outFile)
    }

    // ─── Meio de semana: programa S-140 ──────────────────────────────────────

    private fun writeMidweekProgramPdf(path: String, data: MidweekProgramPdfData) {
        val doc = PDDocument()
        val fonts = PdfFonts.load(doc)
        val pageW = PDRectangle.A4.width
        val pageH = PDRectangle.A4.height
        val mLeft = 40f
        val mRight = 40f
        val mBottom = 40f
        val contentW = pageW - mLeft - mRight
        val gap = 24f
        val colW = (contentW - gap) / 2f

        val chunks = data.semanas.chunked(2).ifEmpty { listOf(emptyList()) }
        chunks.forEach { pair ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            PDPageContentStream(doc, page).use { c ->
                val headerBottom = drawMidweekHeader(c, fonts, data, pageW, pageH, mLeft, mRight, contentW)
                val colTop = headerBottom - 14f
                if (pair.size == 2) {
                    val dividerX = mLeft + colW + gap / 2f
                    c.setStrokingColor(ColorBorder)
                    c.setLineWidth(0.8f)
                    c.setLineDashPattern(floatArrayOf(3f), 0f)
                    c.moveTo(dividerX, colTop)
                    c.lineTo(dividerX, mBottom)
                    c.stroke()
                    c.setLineDashPattern(floatArrayOf(), 0f)
                }
                pair.forEachIndexed { idx, wk ->
                    drawMidweekWeek(c, fonts, wk, mLeft + idx * (colW + gap), colTop, colW, data.labels)
                }
            }
        }
        doc.save(path)
        doc.close()
        openInDesktop(File(path))
    }

    private fun drawMidweekHeader(
        c: PDPageContentStream,
        fonts: PdfFonts,
        data: MidweekProgramPdfData,
        pageW: Float,
        pageH: Float,
        mLeft: Float,
        mRight: Float,
        contentW: Float,
    ): Float {
        val top = pageH - 40f
        c.setNonStrokingColor(ColorMaroon)
        c.setFont(fonts.bold, 23f)
        drawText(c, data.labels.headerTitle, mLeft, top - 20f)
        c.setFont(fonts.regular, 15f)
        drawText(c, data.labels.headerSubtitle, mLeft, top - 40f)
        c.setNonStrokingColor(ColorMuted)
        c.setFont(fonts.bold, 8f)
        drawText(c, data.labels.headerGuide, mLeft, top - 54f)

        // Caixa cinza com o nome da congregacao
        val boxW = 175f
        val boxH = 58f
        val boxX = pageW - mRight - boxW
        val boxY = top - boxH
        c.setNonStrokingColor(ColorGrayBox)
        c.addRect(boxX, boxY, boxW, boxH)
        c.fill()
        c.setNonStrokingColor(ColorMaroon)
        c.setFont(fonts.bold, 12f)
        val nameLines = wrapText(data.congregacao, fonts.bold, 12f, boxW - 20f).take(2)
        nameLines.forEachIndexed { i, ln -> drawText(c, ln, boxX + 12f, boxY + boxH - 16f - i * 14f) }
        data.subtitulo?.takeIf { it.isNotBlank() }?.let {
            c.setFont(fonts.regular, 9.5f)
            drawText(c, fitText(it, fonts.regular, 9.5f, boxW - 20f), boxX + 12f, boxY + 10f)
        }
        return pageH - 40f - 66f
    }

    private fun drawMidweekWeek(
        c: PDPageContentStream,
        fonts: PdfFonts,
        wk: MidweekWeekPdf,
        x: Float,
        yTop: Float,
        colW: Float,
        labels: MidweekPdfStrings,
    ) {
        var y = yTop

        // Faixa da semana
        val bandH = 20f
        c.setNonStrokingColor(ColorMaroon)
        c.addRect(x, y - bandH, colW, bandH)
        c.fill()
        c.setNonStrokingColor(Color.WHITE)
        c.setFont(fonts.bold, 8.5f)
        val band = listOf(wk.periodo, wk.leitura).filter { it.isNotBlank() }.joinToString("  ").uppercase()
        drawText(c, fitText(band, fonts.bold, 8.5f, colW - 12f), x + 7f, y - bandH + 6.5f)
        y -= bandH + 12f

        y = drawLabelValue(c, fonts, labels.presidente, wk.presidente, x, y, colW)
        y = drawLabelValue(c, fonts, labels.oracaoInicial, wk.oracaoInicial, x, y, colW)
        y -= 8f

        y = drawSectionBar(c, fonts, ColorTeal, labels.tesouros1, labels.tesouros2, wk.canticoInicial, labels.cancion, x, y, colW)
        y = drawPart(c, fonts, wk.tesouros, labels.mins, x, y, colW)
        y = drawPart(c, fonts, wk.joias, labels.mins, x, y, colW)
        y = drawPart(c, fonts, wk.leituraBiblia, labels.mins, x, y, colW)
        y -= 8f

        y = drawSectionBar(c, fonts, ColorGold, labels.seamos1, labels.seamos2, null, labels.cancion, x, y, colW)
        wk.ministerio.forEach { y = drawPart(c, fonts, it, labels.mins, x, y, colW) }
        y -= 8f

        y = drawSectionBar(c, fonts, ColorMaroon, labels.vida1, labels.vida2, wk.canticoMeio, labels.cancion, x, y, colW)
        wk.vida.forEach { y = drawPart(c, fonts, it, labels.mins, x, y, colW) }
        y = drawStudyPart(c, fonts, wk.estudo, labels, x, y, colW)
        y -= 10f

        val concl = "${labels.conclusion} ${wk.canticoFinal ?: "___"}"
        c.setNonStrokingColor(ColorMaroon)
        c.setFont(fonts.bold, 8.5f)
        drawCentered(c, concl, x, y, colW, fonts.bold, 8.5f)
        y -= 12f
        wk.oracaoFinal?.takeIf { it.isNotBlank() }?.let {
            c.setNonStrokingColor(ColorInk)
            c.setFont(fonts.bold, 8.5f)
            drawCentered(c, "${labels.oracaoFinal}: $it", x, y, colW, fonts.bold, 8.5f)
        }
    }

    private fun drawLabelValue(
        c: PDPageContentStream,
        fonts: PdfFonts,
        label: String,
        value: String?,
        x: Float,
        y: Float,
        colW: Float,
    ): Float {
        c.setNonStrokingColor(ColorMaroon)
        c.setFont(fonts.bold, 8.5f)
        drawText(c, label, x, y - 9f)
        val lw = textWidth(label, fonts.bold, 8.5f)
        c.setNonStrokingColor(ColorInk)
        c.setFont(fonts.bold, 8.5f)
        drawText(c, fitText(value.orEmpty(), fonts.bold, 8.5f, colW - lw - 10f), x + lw + 8f, y - 9f)
        return y - 14f
    }

    private fun drawSectionBar(
        c: PDPageContentStream,
        fonts: PdfFonts,
        color: Color,
        line1: String,
        line2: String,
        cancion: String?,
        cancionLabel: String,
        x: Float,
        y: Float,
        colW: Float,
    ): Float {
        val iconSize = 13f
        c.setNonStrokingColor(color)
        c.addRect(x, y - iconSize - 2f, iconSize, iconSize)
        c.fill()
        val tx = x + iconSize + 8f
        c.setNonStrokingColor(color)
        c.setFont(fonts.bold, 9f)
        drawText(c, line1, tx, y - 7f)
        drawText(c, line2, tx, y - 17f)
        if (cancion != null && cancion.isNotBlank()) {
            c.setFont(fonts.bold, 8.5f)
            val ct = "$cancionLabel $cancion"
            drawText(c, ct, x + colW - textWidth(ct, fonts.bold, 8.5f), y - 17f)
        }
        val underlineY = y - 23f
        c.setStrokingColor(color)
        c.setLineWidth(1f)
        c.moveTo(x, underlineY)
        c.lineTo(x + colW, underlineY)
        c.stroke()
        return underlineY - 9f
    }

    private fun drawPart(
        c: PDPageContentStream,
        fonts: PdfFonts,
        part: MidweekPartPdf,
        mins: String,
        x: Float,
        y: Float,
        colW: Float,
    ): Float {
        val titleSize = 8.5f
        val lineH = 11f
        val fullTitle = "${part.numero}. ${part.titulo}"
        val lines = wrapText(fullTitle, fonts.bold, titleSize, colW - 2f)
        c.setNonStrokingColor(ColorMaroon)
        c.setFont(fonts.bold, titleSize)
        lines.forEachIndexed { i, ln -> drawText(c, ln, x, y - 9f - i * lineH) }
        val lastLineW = textWidth(lines.last(), fonts.bold, titleSize)
        val lastY = y - 9f - (lines.size - 1) * lineH
        part.minutos?.let {
            val ms = " ($it $mins)"
            if (lastLineW + textWidth(ms, fonts.regular, 7.5f) <= colW) {
                c.setNonStrokingColor(ColorMuted)
                c.setFont(fonts.regular, 7.5f)
                drawText(c, ms, x + lastLineW, lastY)
            }
        }
        var yy = lastY - lineH
        val names = listOfNotNull(
            part.nome1?.takeIf { it.isNotBlank() },
            part.nome2?.takeIf { it.isNotBlank() },
        )
        if (names.isNotEmpty()) {
            c.setNonStrokingColor(ColorInk)
            c.setFont(fonts.bold, titleSize)
            wrapText(names.joinToString(" / "), fonts.bold, titleSize, colW - 12f).forEach {
                drawText(c, it, x + 10f, yy)
                yy -= lineH
            }
        }
        return yy - 3f
    }

    private fun drawStudyPart(
        c: PDPageContentStream,
        fonts: PdfFonts,
        part: MidweekPartPdf,
        labels: MidweekPdfStrings,
        x: Float,
        y: Float,
        colW: Float,
    ): Float {
        val titleSize = 8.5f
        val lineH = 11f
        val fullTitle = "${part.numero}. ${part.titulo}"
        val lines = wrapText(fullTitle, fonts.bold, titleSize, colW - 2f)
        c.setNonStrokingColor(ColorMaroon)
        c.setFont(fonts.bold, titleSize)
        lines.forEachIndexed { i, ln -> drawText(c, ln, x, y - 9f - i * lineH) }
        val lastLineW = textWidth(lines.last(), fonts.bold, titleSize)
        val lastY = y - 9f - (lines.size - 1) * lineH
        part.minutos?.let {
            val ms = " ($it ${labels.mins})"
            if (lastLineW + textWidth(ms, fonts.regular, 7.5f) <= colW) {
                c.setNonStrokingColor(ColorMuted)
                c.setFont(fonts.regular, 7.5f)
                drawText(c, ms, x + lastLineW, lastY)
            }
        }
        // Tabela Conductor / Lector
        val tableW = colW - 20f
        val tx = x + 10f
        var ty = lastY - 8f
        val rowH = 15f
        val half = tableW / 2f
        c.setNonStrokingColor(ColorTableHeader)
        c.addRect(tx, ty - rowH, tableW, rowH)
        c.fill()
        c.setNonStrokingColor(ColorMaroon)
        c.setFont(fonts.bold, 8f)
        drawCentered(c, labels.conductor, tx, ty - rowH + 4.5f, half, fonts.bold, 8f)
        drawCentered(c, labels.lector, tx + half, ty - rowH + 4.5f, half, fonts.bold, 8f)
        ty -= rowH
        c.setStrokingColor(ColorBorder)
        c.setLineWidth(0.7f)
        c.addRect(tx, ty - rowH, tableW, rowH)
        c.stroke()
        c.moveTo(tx + half, ty)
        c.lineTo(tx + half, ty - rowH)
        c.stroke()
        c.setNonStrokingColor(ColorInk)
        c.setFont(fonts.bold, 8f)
        drawCentered(c, fitText(part.nome1.orEmpty(), fonts.bold, 8f, half - 6f), tx, ty - 3f, half, fonts.bold, 8f)
        drawCentered(c, fitText(part.nome2.orEmpty(), fonts.bold, 8f, half - 6f), tx + half, ty - 3f, half, fonts.bold, 8f)
        return ty - rowH - 6f
    }

    // ─── Meio de semana: designacoes S-89 ────────────────────────────────────

    private fun writeMidweekAssignmentsPdf(path: String, data: MidweekAssignmentsPdfData) {
        val doc = PDDocument()
        val fonts = PdfFonts.load(doc)
        val pageW = PDRectangle.A4.width
        val pageH = PDRectangle.A4.height
        val mLeft = 36f
        val mTop = 36f
        val gap = 18f
        val cols = 2
        val rows = 4
        val slipW = (pageW - 2 * mLeft - gap) / cols
        val slipH = (pageH - 2 * mTop - gap) / rows
        val perPage = cols * rows

        val slips = data.designacoes
        if (slips.isEmpty()) {
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            PDPageContentStream(doc, page).use { c ->
                c.setNonStrokingColor(ColorMuted)
                c.setFont(fonts.italic, 12f)
                drawText(c, data.labels.vazio, mLeft, pageH - 80f)
            }
        }
        slips.chunked(perPage).forEach { pageSlips ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            PDPageContentStream(doc, page).use { c ->
                pageSlips.forEachIndexed { i, slip ->
                    val col = i % cols
                    val row = i / cols
                    val x = mLeft + col * (slipW + gap)
                    val yTop = pageH - mTop - row * (slipH + gap)
                    drawAssignmentSlip(c, fonts, slip, data.labels, x, yTop, slipW, slipH)
                }
            }
        }
        doc.save(path)
        doc.close()
        openInDesktop(File(path))
    }

    private fun drawAssignmentSlip(
        c: PDPageContentStream,
        fonts: PdfFonts,
        slip: MidweekAssignmentPdf,
        labels: AssignmentPdfStrings,
        x: Float,
        yTop: Float,
        w: Float,
        h: Float,
    ) {
        c.setStrokingColor(ColorBorder)
        c.setLineWidth(0.8f)
        c.addRect(x, yTop - h, w, h)
        c.stroke()

        val pad = 12f
        var y = yTop - 18f
        c.setNonStrokingColor(ColorMaroon)
        c.setFont(fonts.bold, 9.5f)
        drawCentered(c, labels.title1, x, y, w, fonts.bold, 9.5f)
        y -= 11f
        drawCentered(c, labels.title2, x, y, w, fonts.bold, 9.5f)
        y -= 20f

        c.setNonStrokingColor(ColorInk)
        fun field(label: String, value: String?) {
            c.setFont(fonts.bold, 9f)
            drawText(c, label, x + pad, y)
            val lw = textWidth(label, fonts.bold, 9f)
            c.setFont(fonts.regular, 9f)
            drawText(c, fitText(value.orEmpty(), fonts.regular, 9f, w - 2 * pad - lw - 4f), x + pad + lw + 4f, y)
            // linha
            c.setStrokingColor(ColorBorder)
            c.setLineWidth(0.5f)
            c.moveTo(x + pad + lw + 4f, y - 2f)
            c.lineTo(x + w - pad, y - 2f)
            c.stroke()
            y -= 17f
        }
        field(labels.nombre, slip.nome)
        field(labels.ayudante, slip.ajudante)
        field(labels.fecha, slip.data)
        field(labels.intervencion, slip.numeroParte)

        c.setNonStrokingColor(ColorInk)
        c.setFont(fonts.bold, 9f)
        drawText(c, labels.presentaraEn, x + pad, y)
        y -= 14f
        drawCheck(c, fonts, labels.salaPrincipal, !slip.salaAuxiliar, x + pad + 4f, y)
        y -= 13f
        drawCheck(c, fonts, labels.salaAuxiliar, slip.salaAuxiliar, x + pad + 4f, y)
        y -= 16f

        c.setNonStrokingColor(ColorMuted)
        c.setFont(fonts.italic, 6.8f)
        val nota = labels.nota
        wrapText(nota, fonts.italic, 6.8f, w - 2 * pad).take(3).forEach {
            drawText(c, it, x + pad, y)
            y -= 8.5f
        }
        c.setFont(fonts.regular, 6.5f)
        drawText(c, "S-89-S", x + pad, yTop - h + 8f)
    }

    private fun drawCheck(
        c: PDPageContentStream,
        fonts: PdfFonts,
        label: String,
        checked: Boolean,
        x: Float,
        y: Float,
    ) {
        val box = 8f
        c.setStrokingColor(ColorInk)
        c.setLineWidth(0.8f)
        c.addRect(x, y - box + 1f, box, box)
        c.stroke()
        if (checked) {
            c.setNonStrokingColor(ColorMaroon)
            c.addRect(x + 1.6f, y - box + 2.6f, box - 3.2f, box - 3.2f)
            c.fill()
        }
        c.setNonStrokingColor(ColorInk)
        c.setFont(fonts.regular, 8.5f)
        drawText(c, label, x + box + 5f, y - box + 2f)
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

    private fun writeAvSchedulePdf(path: String, data: AvSchedulePdfData) {
        val document = PDDocument()
        val fonts = PdfFonts.load(document)
        val pageSize = PDPage().mediaBox
        val pageWidth = pageSize.width
        val pageHeight = pageSize.height
        val marginLeft = 45f
        val marginRight = 45f
        val marginBottom = 55f
        val contentWidth = pageWidth - marginLeft - marginRight

        // Quatro colunas: audio/video, plataforma, microfones, acomodadores.
        val colX = listOf(0f, 0.29f, 0.50f, 0.75f).map { marginLeft + 12f + contentWidth * it }
        val colWidth = listOf(0.27f, 0.19f, 0.23f, 0.25f).map { contentWidth * it }

        val dateSize = 10f
        val headerSize = 8.5f
        val nameSize = 9f
        val lineHeight = 13.5f
        val dateToHeaderGap = 16f
        val headerToNamesGap = 13.5f
        val blockGap = 14f

        val pages = mutableListOf<PDPage>()
        val streams = mutableListOf<PDPageContentStream>()

        fun startPage(isFirst: Boolean): Float {
            val page = PDPage()
            document.addPage(page)
            pages.add(page)
            val c = PDPageContentStream(document, page)
            streams.add(c)
            var y = pageHeight - 55f
            if (isFirst) {
                c.setNonStrokingColor(ColorTitle)
                c.setFont(fonts.bold, 12f)
                val left = fitText(data.congregacao, fonts.bold, 12f, contentWidth * 0.55f)
                drawText(c, left, marginLeft, y)
                val right = data.labels.title
                val rightWidth = textWidth(right, fonts.bold, 12f)
                drawText(c, right, marginLeft + contentWidth - rightWidth, y)
                y -= 8f
                c.setStrokingColor(ColorTitle)
                c.setLineWidth(1f)
                c.moveTo(marginLeft, y); c.lineTo(marginLeft + contentWidth, y); c.stroke()
                y -= 22f
            }
            return y
        }

        /** Linhas de nome de cada coluna, ja com o sufixo (Audio)/(Video). */
        fun columnsFor(line: AvScheduleLine): List<List<String>> = listOf(
            listOfNotNull(
                line.audio?.let { "$it  (${data.labels.audioTag})" },
                line.video?.let { "$it  (${data.labels.videoTag})" },
            ),
            line.plataforma,
            line.microfones,
            line.acomodadores,
        )

        var y = startPage(isFirst = true)

        if (data.reunioes.isEmpty()) {
            val c = streams.last()
            c.setNonStrokingColor(ColorMuted)
            c.setFont(fonts.italic, 11f)
            drawText(c, data.labels.vazio, marginLeft, y)
        }

        data.reunioes.forEach { line ->
            val columns = columnsFor(line)
            val maxNameLines = columns.maxOfOrNull { it.size } ?: 0
            val blockHeight = dateToHeaderGap + headerToNamesGap +
                maxNameLines * lineHeight + blockGap

            if (y - blockHeight < marginBottom) {
                y = startPage(isFirst = false)
            }
            val c = streams.last()

            c.setNonStrokingColor(ColorTitle)
            c.setFont(fonts.bold, dateSize)
            drawText(c, "${line.dataLabel} ${line.tipoLabel}", marginLeft, y)
            y -= dateToHeaderGap

            val headers = listOf(
                data.labels.audioVideo,
                data.labels.plataforma,
                data.labels.microfones,
                data.labels.acomodadores,
            )
            c.setFont(fonts.bold, headerSize)
            headers.forEachIndexed { i, header ->
                drawText(c, fitText(header, fonts.bold, headerSize, colWidth[i]), colX[i], y)
            }
            y -= headerToNamesGap

            c.setFont(fonts.regular, nameSize)
            columns.forEachIndexed { i, names ->
                names.forEachIndexed { row, name ->
                    drawText(
                        c,
                        fitText(name, fonts.regular, nameSize, colWidth[i]),
                        colX[i],
                        y - row * lineHeight,
                    )
                }
            }
            y -= maxNameLines * lineHeight + blockGap
        }

        val footerText = currentTimestamp()
        streams.last().let { c ->
            c.setNonStrokingColor(ColorMuted)
            c.setFont(fonts.regular, 8f)
            val footerWidth = textWidth(footerText, fonts.regular, 8f)
            drawText(c, footerText, marginLeft + contentWidth - footerWidth, marginBottom - 15f)
        }

        streams.forEach { it.close() }
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
