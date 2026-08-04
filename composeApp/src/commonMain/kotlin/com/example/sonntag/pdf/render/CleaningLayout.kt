package com.example.sonntag.pdf.render

import com.example.sonntag.pdf.CleaningSchedulePdfData

/** Paleta dos documentos, espelhando o que o desktop ja usava. */
object DocColors {
    val Title = DocColor.hex(0x1A1D29)
    val Muted = DocColor.hex(0x787C8A)
    val Border = DocColor.hex(0xD4D8E0)
    val BlockHeader = DocColor.hex(0xE8EBF0)
    val Separator = DocColor.hex(0xE8EAF0)
    val Zebra = DocColor.hex(0xF7F8FA)
    val Maroon = DocColor.hex(0x561745)
    val Ink = DocColor.hex(0x222228)
    val Teal = DocColor.hex(0x2E7A88)
    val Gold = DocColor.hex(0xA86C00)
    val GrayBox = DocColor.hex(0xEBE6EB)
    val TableHeader = DocColor.hex(0xE5C4C6)
}

/**
 * Escala de limpeza em folha A4. Escrito contra [DocumentCanvas], entao o mesmo
 * codigo produz o PDF no desktop (PDFBox) e no Android (PdfDocument).
 *
 * As medidas sao as do layout original em pontos; a saida do desktop nao muda.
 */
class CleaningLayout(
    private val data: CleaningSchedulePdfData,
    private val geradoEm: String,
    private val iconBytes: ByteArray?,
) {

    fun draw(canvas: DocumentCanvas) {
        val marginLeft = 50f
        val marginBottom = 50f
        val contentWidth = canvas.pageWidth - marginLeft - 50f

        val colWeek = contentWidth * 0.35f
        val colMeetings = contentWidth * 0.35f
        val colGroup = contentWidth * 0.30f

        val cellPad = 8f
        val rowMinHeight = 26f
        val rowLineHeight = 14f

        var y = canvas.headerBand(
            marginLeft = marginLeft,
            contentWidth = contentWidth,
            congregacao = data.congregacao,
            title = data.labels.title,
            subtitle = data.mesLabel,
            iconBytes = iconBytes,
        ) - 4f

        val xWeek = marginLeft
        val xMeetings = marginLeft + colWeek
        val xGroup = marginLeft + colWeek + colMeetings

        // Cabecalho da tabela
        val headerRowHeight = 24f
        canvas.fillRect(marginLeft, y - headerRowHeight, contentWidth, headerRowHeight, DocColors.BlockHeader)
        val headerStyle = TextStyle(11f, DocColors.Title, FontStyle.BOLD)
        val headerBaseline = y - cellPad - 9f
        canvas.text(data.labels.semana, xWeek + cellPad, headerBaseline, headerStyle)
        canvas.text(data.labels.reunioes, xMeetings + cellPad, headerBaseline, headerStyle)
        canvas.text(data.labels.grupo, xGroup + cellPad, headerBaseline, headerStyle)
        val tableHeaderBottom = y - headerRowHeight
        canvas.line(marginLeft, tableHeaderBottom, marginLeft + contentWidth, tableHeaderBottom, DocColors.Border, 0.5f)
        y = tableHeaderBottom

        val bodyStyle = TextStyle(11f, DocColors.Title)
        val placeholderStyle = TextStyle(11f, DocColors.Muted, FontStyle.ITALIC)

        if (data.semanas.isEmpty()) {
            val rowBottom = y - rowMinHeight
            canvas.text(data.labels.vazio, marginLeft + cellPad, y - cellPad - 9f, placeholderStyle)
            canvas.line(marginLeft, rowBottom, marginLeft + contentWidth, rowBottom, DocColors.Border, 0.3f)
            y = rowBottom
        } else {
            data.semanas.forEachIndexed { index, row ->
                val weekLines = wrapText(canvas, row.periodo, bodyStyle, colWeek - cellPad * 2f)
                val meetLines = wrapText(canvas, row.diasReuniao, bodyStyle, colMeetings - cellPad * 2f)
                val isPlaceholder = row.grupoResponsavel.isNullOrBlank()
                val groupText = row.grupoResponsavel?.takeIf { it.isNotBlank() } ?: data.labels.common.aDefinir
                val groupStyle = if (isPlaceholder) placeholderStyle else bodyStyle
                val groupLines = wrapText(canvas, groupText, groupStyle, colGroup - cellPad * 2f)

                val maxLines = maxOf(weekLines.size, meetLines.size, groupLines.size)
                val rowHeight = maxOf(rowMinHeight, maxLines * rowLineHeight + cellPad * 2f)
                val rowBottom = y - rowHeight

                if (index % 2 == 1) {
                    canvas.fillRect(marginLeft, rowBottom, contentWidth, rowHeight, DocColors.Zebra)
                }

                val textBaseline = y - cellPad - 9f
                weekLines.forEachIndexed { i, line ->
                    canvas.text(line, xWeek + cellPad, textBaseline - i * rowLineHeight, bodyStyle)
                }
                meetLines.forEachIndexed { i, line ->
                    canvas.text(line, xMeetings + cellPad, textBaseline - i * rowLineHeight, bodyStyle)
                }
                groupLines.forEachIndexed { i, line ->
                    canvas.text(line, xGroup + cellPad, textBaseline - i * rowLineHeight, groupStyle)
                }

                canvas.line(marginLeft, rowBottom, marginLeft + contentWidth, rowBottom, DocColors.Border, 0.3f)
                y = rowBottom
            }
        }

        val footerStyle = TextStyle(8f, DocColors.Muted)
        val footerText = data.labels.common.geradoEm(geradoEm)
        val footerWidth = canvas.measure(footerText, footerStyle)
        canvas.text(footerText, marginLeft + contentWidth - footerWidth, marginBottom - 10f, footerStyle)
    }

    private fun wrapText(
        canvas: DocumentCanvas,
        text: String,
        style: TextStyle,
        maxWidth: Float,
    ): List<String> {
        if (text.isBlank()) return listOf("")
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (canvas.measure(candidate, style) <= maxWidth || current.isEmpty()) {
                current = StringBuilder(candidate)
            } else {
                lines += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }
}
