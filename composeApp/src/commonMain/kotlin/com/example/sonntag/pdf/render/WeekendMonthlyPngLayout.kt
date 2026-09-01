package com.example.sonntag.pdf.render

import com.example.sonntag.pdf.MonthlyProgramPdfData
import com.example.sonntag.pdf.PdfMeetingLine

/**
 * Programa mensal de fim de semana em imagem: um cartao por reuniao, numa figura
 * alta. Escrito de cima para baixo sobre [TopDownCanvas].
 *
 * A altura depende de quantas linhas cada valor ocupa, e isso so se sabe medindo o
 * texto — por isso [measureBlocks] roda antes, sobre uma superficie descartavel.
 */
class WeekendMonthlyPngLayout(
    private val data: MonthlyProgramPdfData,
    private val geradoEm: String,
    private val iconBytes: ByteArray?,
) {

    private val padding = 64
    private val titleStyle = TextStyle(50f, DocColors.Title, FontStyle.BOLD)
    private val subtitleStyle = TextStyle(38f, PngColors.Navy, FontStyle.BOLD)
    private val monthStyle = TextStyle(28f, DocColors.Muted)
    private val blockHeaderStyle = TextStyle(32f, DocColors.Title, FontStyle.BOLD)
    private val labelStyle = TextStyle(26f, DocColors.Muted, FontStyle.BOLD)
    private val valueStyle = TextStyle(30f, DocColors.Title)
    private val valueItalic = TextStyle(30f, DocColors.Muted, FontStyle.ITALIC)
    private val footerStyle = TextStyle(20f, DocColors.Muted, FontStyle.ITALIC)

    private val blockGap = 24
    private val rowVertPad = 14
    private val rowLineGap = 8
    private val cardInnerPad = 28
    private val cardHeaderH = 64
    private val cardWidth = PNG_WIDTH - padding * 2
    private val labelColW = 280
    private val valueColX = padding + cardInnerPad + labelColW
    private val valueColW = cardWidth - cardInnerPad * 2 - labelColW

    private data class RenderRow(val label: String, val lines: List<String>, val placeholder: Boolean)
    private data class RenderBlock(val header: String, val rows: List<RenderRow>, val height: Int)

    private var blocks: List<RenderBlock> = emptyList()

    /** Mede os cartoes. Precisa rodar antes de [draw]. */
    fun measureBlocks(measurer: DocumentCanvas): Int {
        blocks = data.reunioes.map { blockFor(measurer, it) }
        val blocksTotalHeight = if (blocks.isEmpty()) 180
        else blocks.sumOf { it.height } + (blocks.size - 1) * blockGap
        val headerHeight = padding + titleStyle.size.toInt() + 30 + subtitleStyle.size.toInt() +
            12 + monthStyle.size.toInt() + 36
        return (headerHeight + blocksTotalHeight + padding + footerStyle.size.toInt() + 24)
            .coerceAtLeast(1350)
    }

    private fun blockFor(measurer: DocumentCanvas, line: PdfMeetingLine): RenderBlock {
        val vazio = data.labels.common.aDefinir
        // Semana de evento: uma linha so, no lugar dos cinco campos que ninguem preenche.
        val origem = if (line.eventoLabel != null) listOf(
            Triple(data.labels.common.evento, null, line.eventoLabel),
        ) else listOf(
            Triple(data.labels.titulo, line.tituloDiscurso, data.labels.common.discursoADefinir),
            Triple(data.labels.orador, line.orador, vazio),
            Triple(data.labels.presidente, line.presidente, vazio),
            Triple(data.labels.dirigente, line.dirigenteEstudo, vazio),
            Triple(data.labels.leitor, line.leitor, vazio),
        )
        val rows = origem.map { (label, valor, placeholderText) ->
            val isPlaceholder = valor == null
            val style = if (isPlaceholder) valueItalic else valueStyle
            RenderRow(
                label,
                measurer.wrapText(valor ?: placeholderText, style, valueColW.toFloat()),
                isPlaceholder,
            )
        }
        var h = cardHeaderH
        rows.forEach { h += rowVertPad * 2 + it.lines.size * (valueStyle.size.toInt() + 4) + rowLineGap }
        return RenderBlock("${line.dateLabel} — ${line.hora}", rows, h)
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
        canvas.text(data.labels.tituloMensal, padding.toFloat(), y.toFloat(), subtitleStyle)

        y += 12 + monthStyle.size.toInt()
        canvas.text(data.mesLabel, padding.toFloat(), y.toFloat(), monthStyle)
        y += 28

        val blockX = padding.toFloat()

        if (blocks.isEmpty()) {
            canvas.fillRect(blockX, y.toFloat(), cardWidth.toFloat(), 140f, PngColors.Card, CARD_RADIUS)
            canvas.text(data.labels.vazio, blockX + 32f, y + 80f, valueItalic)
        } else {
            blocks.forEach { block ->
                val h = block.height
                canvas.fillRect(blockX, y.toFloat(), cardWidth.toFloat(), h.toFloat(), PngColors.Card, CARD_RADIUS)
                canvas.strokeRect(blockX, y.toFloat(), cardWidth.toFloat(), h.toFloat(), DocColors.Border, 1f, CARD_RADIUS)
                canvas.fillRect(
                    blockX, y.toFloat(), cardWidth.toFloat(), cardHeaderH.toFloat(),
                    DocColors.BlockHeader, CARD_RADIUS,
                )
                canvas.text(block.header, blockX + cardInnerPad, (y + cardHeaderH - 22).toFloat(), blockHeaderStyle)

                var rowY = y + cardHeaderH
                block.rows.forEach { r ->
                    val rowH = rowVertPad * 2 + r.lines.size * (valueStyle.size.toInt() + 4) + rowLineGap
                    canvas.text(
                        r.label,
                        blockX + cardInnerPad,
                        (rowY + rowVertPad + labelStyle.size.toInt() - 2).toFloat(),
                        labelStyle,
                    )
                    val style = if (r.placeholder) valueItalic else valueStyle
                    r.lines.forEachIndexed { i, vline ->
                        canvas.text(
                            vline,
                            valueColX.toFloat(),
                            (rowY + rowVertPad + valueStyle.size.toInt() + i * (valueStyle.size.toInt() + 4)).toFloat(),
                            style,
                        )
                    }
                    rowY += rowH
                }
                y += h + blockGap
            }
        }

        val footerText = data.labels.common.geradoEm(geradoEm)
        val fw = canvas.measure(footerText, footerStyle)
        canvas.text(footerText, width - padding - fw, height - padding, footerStyle)
    }
}
