package com.example.sonntag.pdf.render

import com.example.sonntag.pdf.MeetingProgramPdfData

/**
 * Programa de uma reuniao de fim de semana em imagem: um cartao unico com as cinco
 * designacoes. Escrito de cima para baixo sobre [TopDownCanvas].
 */
class WeekendMeetingPngLayout(
    private val data: MeetingProgramPdfData,
    private val geradoEm: String,
    private val iconBytes: ByteArray?,
) {

    private val padding = 64
    private val titleStyle = TextStyle(50f, DocColors.Title, FontStyle.BOLD)
    private val subtitleStyle = TextStyle(38f, PngColors.Navy, FontStyle.BOLD)
    private val monthStyle = TextStyle(28f, DocColors.Muted)
    private val blockHeaderStyle = TextStyle(34f, DocColors.Title, FontStyle.BOLD)
    private val labelStyle = TextStyle(28f, DocColors.Muted, FontStyle.BOLD)
    private val valueStyle = TextStyle(34f, DocColors.Title)
    private val valueItalic = TextStyle(34f, DocColors.Muted, FontStyle.ITALIC)
    private val footerStyle = TextStyle(20f, DocColors.Muted, FontStyle.ITALIC)

    private val rowVertPad = 18
    private val rowLineGap = 10
    private val cardInnerPad = 32
    private val cardHeaderH = 76
    private val rowH = rowVertPad * 2 + valueStyle.size.toInt() + rowLineGap
    private val blockH = cardHeaderH + 5 * rowH

    fun measureHeight(): Int {
        val headerHeight = padding + titleStyle.size.toInt() + 30 + subtitleStyle.size.toInt() +
            12 + monthStyle.size.toInt() + 36
        return (headerHeight + blockH + padding + footerStyle.size.toInt() + 24).coerceAtLeast(1350)
    }

    fun draw(canvas: TopDownCanvas) {
        val width = canvas.width.toInt()
        val height = canvas.height

        var y = padding + titleStyle.size.toInt()
        iconBytes?.let {
            canvas.image(
                it,
                (width - padding - PNG_HEADER_ICON).toFloat(),
                padding.toFloat(),
                PNG_HEADER_ICON.toFloat(),
                PNG_HEADER_ICON.toFloat(),
            )
        }
        canvas.text(data.congregacao, padding.toFloat(), y.toFloat(), titleStyle)

        y += 30
        y += subtitleStyle.size.toInt()
        canvas.text(data.labels.tituloReuniao, padding.toFloat(), y.toFloat(), subtitleStyle)

        y += 12 + monthStyle.size.toInt()
        canvas.text(data.dateLabel, padding.toFloat(), y.toFloat(), monthStyle)
        y += 28

        val blockX = padding.toFloat()
        val blockWidth = (width - padding * 2).toFloat()

        canvas.fillRect(blockX, y.toFloat(), blockWidth, blockH.toFloat(), PngColors.Card, CARD_RADIUS)
        canvas.strokeRect(blockX, y.toFloat(), blockWidth, blockH.toFloat(), DocColors.Border, 1f, CARD_RADIUS)
        canvas.fillRect(blockX, y.toFloat(), blockWidth, cardHeaderH.toFloat(), DocColors.BlockHeader, CARD_RADIUS)
        canvas.text(
            "${data.dateLabel} — ${data.hora}",
            blockX + cardInnerPad,
            (y + cardHeaderH - 26).toFloat(),
            blockHeaderStyle,
        )

        val rows = listOf(
            Triple(data.labels.titulo, data.tituloDiscurso, data.labels.common.discursoADefinir),
            Triple(data.labels.orador, data.orador, data.labels.common.aDefinir),
            Triple(data.labels.presidente, data.presidente, data.labels.common.aDefinir),
            Triple(data.labels.dirigente, data.dirigenteEstudo, data.labels.common.aDefinir),
            Triple(data.labels.leitor, data.leitor, data.labels.common.aDefinir),
        )

        var rowY = y + cardHeaderH
        rows.forEach { (label, valorOriginal, placeholderText) ->
            val isPlaceholder = valorOriginal == null
            canvas.text(
                label,
                blockX + cardInnerPad,
                (rowY + rowVertPad + labelStyle.size.toInt() - 2).toFloat(),
                labelStyle,
            )
            canvas.text(
                valorOriginal ?: placeholderText,
                blockX + cardInnerPad + 320,
                (rowY + rowVertPad + valueStyle.size.toInt()).toFloat(),
                if (isPlaceholder) valueItalic else valueStyle,
            )
            rowY += rowH
        }

        val footerText = data.labels.common.geradoEm(geradoEm)
        val fw = canvas.measure(footerText, footerStyle)
        canvas.text(footerText, width - padding - fw, height - padding, footerStyle)
    }
}
