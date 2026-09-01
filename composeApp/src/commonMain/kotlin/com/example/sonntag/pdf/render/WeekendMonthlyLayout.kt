package com.example.sonntag.pdf.render

import com.example.sonntag.pdf.MonthlyProgramPdfData
import com.example.sonntag.pdf.PdfMeetingLine
import com.example.sonntag.pdf.WeekendPdfStrings

/**
 * Programa mensal de fim de semana: um bloco por reuniao, paginado.
 *
 * O rodape traz "pagina X de Y", entao o total precisa ser conhecido **antes** de
 * desenhar: o layout faz uma passada de medicao e so depois desenha. O codigo
 * anterior reabria cada pagina no fim, o que o PdfDocument do Android nao permite.
 */
class WeekendMonthlyLayout(
    private val data: MonthlyProgramPdfData,
    private val geradoEm: String,
) {

    private val marginLeft = 50f
    private val marginRight = 50f
    private val marginBottom = 60f
    private val blockGap = 16f

    fun draw(canvas: DocumentCanvas) {
        val contentWidth = canvas.pageWidth - marginLeft - marginRight
        val paginas = paginate(canvas, contentWidth)

        paginas.forEachIndexed { index, blocos ->
            if (index > 0) canvas.newPage()
            // O cartao se repete em todas as folhas: cada uma se explica sozinha.
            var y = canvas.titleCard(
                marginLeft = marginLeft,
                contentWidth = contentWidth,
                title = data.labels.tituloMensal,
                subtitle = data.mesLabel,
                congregacaoLabel = data.labels.common.congregacao,
                congregacao = data.congregacao,
            )

            if (index == 0 && data.reunioes.isEmpty()) {
                canvas.text(
                    data.labels.vazio,
                    marginLeft,
                    y - 14f,
                    TextStyle(12f, DocColors.Muted, FontStyle.ITALIC),
                )
            }

            blocos.forEach { line ->
                drawBlock(canvas, line, marginLeft, y, contentWidth)
                y -= blockHeight(canvas, line, contentWidth) + blockGap
            }

            footer(canvas, contentWidth, index + 1, paginas.size)
        }
    }

    /** Distribui os blocos entre paginas usando as mesmas alturas do desenho. */
    private fun paginate(canvas: DocumentCanvas, contentWidth: Float): List<List<PdfMeetingLine>> {
        if (data.reunioes.isEmpty()) return listOf(emptyList())

        val paginas = mutableListOf<MutableList<PdfMeetingLine>>()
        var atual = mutableListOf<PdfMeetingLine>()
        var y = canvas.pageHeight - TITLE_CARD_SPACE

        data.reunioes.forEach { line ->
            val altura = blockHeight(canvas, line, contentWidth)
            if (y - altura < marginBottom + 24f && atual.isNotEmpty()) {
                paginas += atual
                atual = mutableListOf()
                y = canvas.pageHeight - TITLE_CARD_SPACE
            }
            atual += line
            y -= altura + blockGap
        }
        if (atual.isNotEmpty() || paginas.isEmpty()) paginas += atual
        return paginas
    }

    private fun rows(line: PdfMeetingLine, labels: WeekendPdfStrings): List<Triple<String, String, Boolean>> {
        val vazio = labels.common.aDefinir
        // Semana de evento: uma linha so, no lugar dos cinco campos que ninguem preenche.
        line.eventoLabel?.let { return listOf(Triple(labels.common.evento, it, true)) }
        return listOf(
            Triple(labels.titulo, line.tituloDiscurso ?: labels.common.discursoADefinir, line.tituloDiscurso == null),
            Triple(labels.orador, line.orador ?: vazio, line.orador == null),
            Triple(labels.presidente, line.presidente ?: vazio, line.presidente == null),
            Triple(labels.dirigente, line.dirigenteEstudo ?: vazio, line.dirigenteEstudo == null),
            Triple(labels.leitor, line.leitor ?: vazio, line.leitor == null),
        )
    }

    private fun blockHeight(canvas: DocumentCanvas, line: PdfMeetingLine, width: Float): Float {
        val labelColWidth = 140f
        val labelValueGap = 12f
        val valueColWidth = width - labelColWidth - labelValueGap
        var h = 28f
        rows(line, data.labels).forEach { (_, value, isPlaceholder) ->
            val style = valueStyle(isPlaceholder)
            val lines = canvas.wrapText(value, style, valueColWidth)
            h += 6f * 2f + lines.size * (11f + 4f) + 0.5f
        }
        return h
    }

    private fun valueStyle(isPlaceholder: Boolean) =
        if (isPlaceholder) TextStyle(11f, DocColors.Muted, FontStyle.ITALIC)
        else TextStyle(11f, DocColors.Title)

    private fun drawBlock(canvas: DocumentCanvas, line: PdfMeetingLine, x: Float, y: Float, width: Float) {
        val blockHeaderHeight = 28f
        val labelStyle = TextStyle(10f, DocColors.Muted)
        val labelColWidth = 140f
        val labelValueGap = 12f
        val valueColWidth = width - labelColWidth - labelValueGap
        val rowVertPad = 6f
        val lineGap = 4f

        // Faixa azul da reuniao, com o texto por cima.
        canvas.fillRect(x, y - blockHeaderHeight, width, blockHeaderHeight, DocColors.Navy)
        canvas.text(
            "${line.dateLabel} — ${line.hora}",
            x + 12f,
            y - 19f,
            TextStyle(13f, DocColor.White, FontStyle.BOLD),
        )
        var cursor = y - blockHeaderHeight

        rows(line, data.labels).forEach { (label, value, isPlaceholder) ->
            val style = valueStyle(isPlaceholder)
            val valueLines = canvas.wrapText(value, style, valueColWidth)
            val rowHeight = rowVertPad * 2f + valueLines.size * (11f + lineGap)
            val rowTop = cursor
            val rowBottom = cursor - rowHeight

            val labelWidth = canvas.measure(label, labelStyle)
            canvas.text(label, x + labelColWidth - labelWidth, rowTop - rowVertPad - 10f, labelStyle)

            valueLines.forEachIndexed { i, vline ->
                canvas.text(
                    vline,
                    x + labelColWidth + labelValueGap,
                    rowTop - rowVertPad - 11f - i * (11f + lineGap),
                    style,
                )
            }

            // O separador tem recuo de 8pt e o avanco e exatamente rowBottom: o
            // 0.5f extra pertence so a medicao da altura do bloco.
            canvas.line(x + 8f, rowBottom, x + width - 8f, rowBottom, DocColors.Separator, 0.5f)
            cursor = rowBottom
        }
    }

    private fun footer(canvas: DocumentCanvas, contentWidth: Float, pagina: Int, total: Int) {
        val style = TextStyle(8f, DocColors.Muted)
        canvas.text(data.labels.common.pagina(pagina, total), marginLeft, marginBottom - 14f, style)
        val timestamp = data.labels.common.geradoEm(geradoEm)
        val width = canvas.measure(timestamp, style)
        canvas.text(timestamp, marginLeft + contentWidth - width, marginBottom - 14f, style)
    }
}
