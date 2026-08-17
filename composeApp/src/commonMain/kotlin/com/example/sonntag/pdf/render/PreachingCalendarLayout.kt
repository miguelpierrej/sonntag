package com.example.sonntag.pdf.render

import com.example.sonntag.pdf.PreachingDayPdf
import com.example.sonntag.pdf.PreachingProgramPdfData
import com.example.sonntag.pdf.PreachingShiftPdf

/**
 * Programa de pregacao em calendario mensal, na forma do impresso da congregacao:
 * domingo a sabado, os dias de fora do mes em cinza, e o rodape com os grupos e o
 * recado do mes.
 *
 * A altura das linhas se ajusta ao mes (cinco ou seis semanas) e ao rodape, para o
 * calendario ocupar a folha inteira sem passar para a segunda.
 */
class PreachingCalendarLayout(
    private val data: PreachingProgramPdfData,
    private val geradoEm: String,
) {

    private val marginLeft = 32f
    private val marginRight = 32f
    private val marginBottom = 32f

    private val headerHeight = 18f
    private val dayStripHeight = 11f
    private val cellPad = 3f

    private val diaStyle = TextStyle(7f, DocColors.Ink, FontStyle.BOLD)
    private val nomeStyle = TextStyle(7f, DocColors.NameInk, FontStyle.BOLD)
    private val detalheStyle = TextStyle(6.5f, DocColors.Ink)
    private val notaStyle = TextStyle(6.5f, DocColors.Alert, FontStyle.BOLD)

    fun draw(canvas: DocumentCanvas) {
        val contentWidth = canvas.pageWidth - marginLeft - marginRight
        val colWidth = contentWidth / 7f

        var y = canvas.titleCard(
            marginLeft = marginLeft,
            contentWidth = contentWidth,
            title = data.titulo,
            subtitle = data.mesLabel,
            congregacaoLabel = data.labels.common.congregacao,
            congregacao = data.congregacao,
        )

        y = drawWeekdays(canvas, y, colWidth)

        val alturaRodape = alturaDoRodape(canvas, contentWidth)
        val disponivel = y - marginBottom - alturaRodape - 14f
        val alturaLinha = (disponivel / data.semanas.size.coerceAtLeast(1)).coerceIn(46f, 108f)

        data.semanas.forEach { semana ->
            semana.forEachIndexed { i, dia ->
                drawDay(canvas, dia, marginLeft + i * colWidth, y, colWidth, alturaLinha)
            }
            y -= alturaLinha
        }

        drawRodape(canvas, y - 14f, contentWidth)

        val footerStyle = TextStyle(7f, DocColors.Muted)
        val footer = data.labels.common.geradoEm(geradoEm)
        canvas.text(
            footer,
            marginLeft + contentWidth - canvas.measure(footer, footerStyle),
            marginBottom - 18f,
            footerStyle,
        )
    }

    /** Faixa azul com os dias da semana. */
    private fun drawWeekdays(canvas: DocumentCanvas, yTop: Float, colWidth: Float): Float {
        canvas.fillRect(marginLeft, yTop - headerHeight, colWidth * 7f, headerHeight, DocColors.GridHeader)
        val style = TextStyle(8.5f, DocColor.White, FontStyle.BOLD)
        data.labels.diasDaSemana.forEachIndexed { i, dia ->
            canvas.textCentered(
                canvas.fitText(dia, style, colWidth - 6f),
                marginLeft + i * colWidth,
                colWidth,
                yTop - headerHeight + 5.5f,
                style,
            )
        }
        return yTop - headerHeight
    }

    /** Uma celula: o numero do dia na tarja e os turnos embaixo. */
    private fun drawDay(
        canvas: DocumentCanvas,
        dia: PreachingDayPdf,
        x: Float,
        yTop: Float,
        largura: Float,
        altura: Float,
    ) {
        val fora = !dia.doMes
        if (fora) {
            canvas.fillRect(x, yTop - altura, largura, altura, DocColors.GridOutside)
        }
        canvas.fillRect(x, yTop - dayStripHeight, largura, dayStripHeight, DocColors.GridDayStrip)
        val numero = dia.dia.toString()
        canvas.text(
            numero,
            x + largura - cellPad - canvas.measure(numero, diaStyle),
            yTop - dayStripHeight + 3f,
            diaStyle,
        )
        canvas.strokeRect(x, yTop - altura, largura, altura, DocColors.GridHeader, 0.4f)

        if (fora) return
        var y = yTop - dayStripHeight - 4f
        val limite = yTop - altura + 2f

        dia.turnos.forEach { turno ->
            if (y <= limite) return
            y = drawShift(canvas, turno, x, y, largura, limite)
        }
    }

    private fun drawShift(
        canvas: DocumentCanvas,
        turno: PreachingShiftPdf,
        x: Float,
        yTop: Float,
        largura: Float,
        limite: Float,
    ): Float {
        var y = yTop
        val util = largura - cellPad * 2f
        val linha = 8f

        // Um nome por linha: dois lado a lado nao cabem na largura da celula.
        turno.nomes.forEach { nome ->
            if (y <= limite) return y
            canvas.text(canvas.fitText(nome, nomeStyle, util), x + cellPad, y - 6f, nomeStyle)
            y -= linha
        }
        if (y > limite) {
            canvas.text(canvas.fitText(turno.hora, detalheStyle, util), x + cellPad, y - 6f, detalheStyle)
            y -= linha
        }
        // O destaque tem prioridade sobre o nome do ponto: quando os dois nao cabem,
        // o ponto vira uma linha so, para o aviso nao sair cortado no meio.
        val nota = turno.nota?.takeIf { it.isNotBlank() }
        val linhasDaNota = nota?.let { canvas.wrapText(it, notaStyle, util).take(2) }.orEmpty()
        turno.ponto?.takeIf { it.isNotBlank() }?.let { ponto ->
            // Quantas linhas sobram depois de reservar o destaque; nunca menos de uma,
            // e no maximo tres, senao um nome de ponto comprido engole a celula.
            val cabe = (((y - limite) / linha).toInt() - linhasDaNota.size).coerceIn(1, 3)
            val pedacos = canvas.wrapText(ponto, detalheStyle, util)
            val linhasDoPonto = if (cabe >= pedacos.size) {
                pedacos
            } else if (cabe == 1) {
                listOf(canvas.fitText(ponto, detalheStyle, util))
            } else {
                // Corta no fim da ultima linha que cabe, com reticencias.
                pedacos.take(cabe - 1) + canvas.fitText(pedacos.drop(cabe - 1).joinToString(" "), detalheStyle, util)
            }
            linhasDoPonto.forEach { pedaco ->
                if (y > limite) {
                    canvas.text(pedaco, x + cellPad, y - 6f, detalheStyle)
                    y -= linha
                }
            }
        }
        linhasDaNota.forEach { pedaco ->
            if (y > limite) {
                canvas.text(pedaco, x + cellPad, y - 6f, notaStyle)
                y -= linha
            }
        }
        return y - 2f
    }

    // ─── Rodape ──────────────────────────────────────────────────────────────

    /** Linhas do rodape: os grupos e, depois, o recado do mes. */
    private fun linhasDoRodape(canvas: DocumentCanvas, contentWidth: Float): List<Pair<String, TextStyle>> {
        val grupoStyle = TextStyle(7.5f, DocColors.NameInk, FontStyle.BOLD)
        val obsStyle = TextStyle(7.5f, DocColors.Ink)
        val linhas = mutableListOf<Pair<String, TextStyle>>()
        data.grupos.forEach { grupo ->
            val texto = listOfNotNull(
                grupo.nome,
                grupo.dirigente?.takeIf { it.isNotBlank() },
                grupo.local?.takeIf { it.isNotBlank() },
            ).joinToString(" – ")
            linhas += texto to grupoStyle
        }
        data.observacao?.takeIf { it.isNotBlank() }?.lines()?.forEach { linha ->
            canvas.wrapText(linha, obsStyle, contentWidth).forEach { linhas += it to obsStyle }
        }
        return linhas
    }

    private fun alturaDoRodape(canvas: DocumentCanvas, contentWidth: Float): Float {
        val linhas = linhasDoRodape(canvas, contentWidth)
        return if (linhas.isEmpty()) 0f else linhas.size * 10f + 8f
    }

    private fun drawRodape(canvas: DocumentCanvas, yTop: Float, contentWidth: Float) {
        val linhas = linhasDoRodape(canvas, contentWidth)
        if (linhas.isEmpty()) return
        var y = yTop
        canvas.line(marginLeft, y + 6f, marginLeft + contentWidth, y + 6f, DocColors.GridHeader, 0.6f)
        linhas.forEach { (texto, style) ->
            canvas.text(texto, marginLeft, y - 7f, style)
            y -= 10f
        }
    }
}
