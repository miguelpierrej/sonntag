package com.example.sonntag.pdf.render

import com.example.sonntag.pdf.MeetingProgramPdfData

/** Programa de uma unica reuniao de fim de semana, em folha A4. */
class WeekendMeetingLayout(
    private val data: MeetingProgramPdfData,
    private val geradoEm: String,
    private val iconBytes: ByteArray?,
) {

    fun draw(canvas: DocumentCanvas) {
        val marginLeft = 70f
        val marginRight = 70f
        val marginBottom = 60f
        val contentWidth = canvas.pageWidth - marginLeft - marginRight

        var y = canvas.headerBand(
            marginLeft = marginLeft,
            contentWidth = contentWidth,
            congregacao = data.congregacao,
            title = data.labels.tituloReuniao,
            subtitle = data.dateLabel,
            iconBytes = iconBytes,
        ) - 30f

        val labelStyle = TextStyle(12f, DocColors.Muted)
        val valueSize = 14f
        val rowVertPad = 10f
        val rowGap = 1f
        val labelColWidth = 170f
        val labelValueGap = 16f
        val valueColWidth = contentWidth - labelColWidth - labelValueGap
        val blockHeaderHeight = 36f

        canvas.fillRect(marginLeft, y - blockHeaderHeight, contentWidth, blockHeaderHeight, DocColors.BlockHeader)
        canvas.text(
            "${data.dateLabel} — ${data.hora}",
            marginLeft + 14f,
            y - 22f,
            TextStyle(14f, DocColors.Title, FontStyle.BOLD),
        )
        y -= blockHeaderHeight

        val rows = listOf(
            Triple(data.labels.titulo, data.tituloDiscurso, data.labels.common.discursoADefinir),
            Triple(data.labels.orador, data.orador, data.labels.common.aDefinir),
            Triple(data.labels.presidente, data.presidente, data.labels.common.aDefinir),
            Triple(data.labels.dirigente, data.dirigenteEstudo, data.labels.common.aDefinir),
            Triple(data.labels.leitor, data.leitor, data.labels.common.aDefinir),
        )

        rows.forEach { (label, valorOriginal, placeholderText) ->
            val isPlaceholder = valorOriginal == null
            val value = valorOriginal ?: placeholderText
            val valueStyle = if (isPlaceholder) {
                TextStyle(valueSize, DocColors.Muted, FontStyle.ITALIC)
            } else {
                TextStyle(valueSize, DocColors.Title)
            }
            val valueLines = canvas.wrapText(value, valueStyle, valueColWidth)
            val rowHeight = rowVertPad * 2f + valueLines.size * (valueSize + 4f)
            val rowTop = y
            val rowBottom = y - rowHeight

            val labelWidth = canvas.measure(label, labelStyle)
            canvas.text(label, marginLeft + labelColWidth - labelWidth, rowTop - rowVertPad - 12f, labelStyle)

            valueLines.forEachIndexed { i, vline ->
                val baseline = rowTop - rowVertPad - valueSize - i * (valueSize + 4f)
                canvas.text(vline, marginLeft + labelColWidth + labelValueGap, baseline, valueStyle)
            }

            canvas.line(marginLeft, rowBottom, marginLeft + contentWidth, rowBottom, DocColors.Separator, 0.5f)
            y = rowBottom - rowGap
        }

        val footerStyle = TextStyle(8f, DocColors.Muted)
        val timestamp = data.labels.common.geradoEm(geradoEm)
        val width = canvas.measure(timestamp, footerStyle)
        canvas.text(timestamp, marginLeft + contentWidth - width, marginBottom - 14f, footerStyle)
    }
}
