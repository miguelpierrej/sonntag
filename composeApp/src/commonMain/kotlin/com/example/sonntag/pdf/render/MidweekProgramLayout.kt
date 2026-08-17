package com.example.sonntag.pdf.render

import com.example.sonntag.pdf.MidweekPartPdf
import com.example.sonntag.pdf.MidweekPdfStrings
import com.example.sonntag.pdf.MidweekProgramPdfData
import com.example.sonntag.pdf.MidweekWeekPdf

/**
 * Programa de meio de semana (S-140): duas semanas por folha A4, lado a lado.
 *
 * Segue o modelo impresso azul: titulo centralizado com a caixa da congregacao no
 * canto, faixa da semana com o periodo a esquerda e a leitura a direita, e cada
 * secao na sua cor (tesouros, ministerio, vida crista).
 *
 * Os tres icones das secoes sao opcionais: sem eles, o lugar do icone vira um
 * quadrado na cor da secao.
 *
 * Os PNGs em `icons/secao-*.png` (resources no desktop, assets no Android) foram
 * gerados a partir da fonte de icones `jw-icons-all` usada na Biblioteca on-line —
 * glifos U+E720 (diamante), U+E898 (espiga) e U+E800 (ovelha, espelhada na
 * horizontal, como o site tambem faz), em branco sobre a cor da secao.
 */
class MidweekProgramLayout(
    private val data: MidweekProgramPdfData,
    private val iconTesouros: ByteArray? = null,
    private val iconMinisterio: ByteArray? = null,
    private val iconVida: ByteArray? = null,
) {

    private val marginLeft = 40f
    private val marginRight = 40f
    private val marginBottom = 40f
    private val gap = 24f

    /** Caixa da congregacao, encostada na borda direita como no impresso. */
    private val boxWidth = 172f
    private val boxHeight = 116f

    fun draw(canvas: DocumentCanvas) {
        val contentWidth = canvas.pageWidth - marginLeft - marginRight
        val colWidth = (contentWidth - gap) / 2f

        val chunks = data.semanas.chunked(2).ifEmpty { listOf(emptyList()) }
        chunks.forEachIndexed { pageIndex, pair ->
            if (pageIndex > 0) canvas.newPage()
            val colTop = drawHeader(canvas)

            if (pair.size == 2) {
                val dividerX = marginLeft + colWidth + gap / 2f
                canvas.line(dividerX, colTop, dividerX, marginBottom, DocColors.Border, 0.8f, dashed = true)
            }
            pair.forEachIndexed { idx, week ->
                drawWeek(canvas, week, marginLeft + idx * (colWidth + gap), colTop, colWidth, data.labels)
            }
        }
    }

    private fun drawHeader(canvas: DocumentCanvas): Float {
        val top = canvas.pageHeight
        val boxX = canvas.pageWidth - boxWidth
        canvas.fillRect(boxX, top - 14f - boxHeight, boxWidth, boxHeight, DocColors.NavySoft)

        // O titulo fica centralizado no espaco que sobra a esquerda da caixa.
        val tituloArea = boxX - marginLeft
        canvas.textCentered(
            data.labels.headerTitle, marginLeft, tituloArea, top - 62f,
            TextStyle(22f, DocColors.Navy, FontStyle.BOLD),
        )
        canvas.textCentered(
            data.labels.headerSubtitle, marginLeft, tituloArea, top - 86f,
            TextStyle(18f, DocColors.Navy),
        )
        canvas.textCentered(
            data.labels.headerGuide, marginLeft, tituloArea, top - 101f,
            TextStyle(8.5f, DocColors.Navy, FontStyle.BOLD),
        )

        val labelStyle = TextStyle(9f, DocColors.NavyInk)
        canvas.text(data.labels.congregacaoLabel, boxX + 14f, top - 44f, labelStyle)
        val nameStyle = TextStyle(13f, DocColors.NavyInk, FontStyle.BOLD)
        canvas.wrapText(data.congregacao, nameStyle, boxWidth - 28f).take(2).forEachIndexed { i, ln ->
            canvas.text(ln, boxX + 14f, top - 62f - i * 15f, nameStyle)
        }
        data.subtitulo?.takeIf { it.isNotBlank() }?.let {
            val style = TextStyle(8.5f, DocColors.NavyInk)
            canvas.text(canvas.fitText(it, style, boxWidth - 28f), boxX + 14f, top - 14f - boxHeight + 12f, style)
        }
        return top - 14f - boxHeight - 16f
    }

    private fun drawWeek(
        canvas: DocumentCanvas,
        wk: MidweekWeekPdf,
        x: Float,
        yTop: Float,
        colW: Float,
        labels: MidweekPdfStrings,
    ) {
        var y = yTop

        // Faixa da semana: periodo a esquerda, leitura da semana a direita.
        val bandH = 20f
        canvas.fillRect(x, y - bandH, colW, bandH, DocColors.Navy)
        val bandStyle = TextStyle(9f, DocColor.White, FontStyle.BOLD)
        val baseline = y - bandH + 6.5f
        canvas.text(canvas.fitText(wk.periodo.uppercase(), bandStyle, colW * 0.55f), x + 7f, baseline, bandStyle)
        wk.leitura.takeIf { it.isNotBlank() }?.let {
            val leitura = canvas.fitText(it.uppercase(), bandStyle, colW * 0.42f)
            canvas.text(leitura, x + colW - 7f - canvas.measure(leitura, bandStyle), baseline, bandStyle)
        }
        y -= bandH + 12f

        y = labelValue(canvas, labels.presidente, wk.presidente, x, y, colW)
        y = labelValue(canvas, labels.oracaoInicial, wk.oracaoInicial, x, y, colW)
        y -= 8f

        y = sectionBar(canvas, DocColors.Teal, iconTesouros, labels.tesouros1, labels.tesouros2, wk.canticoInicial, labels.cancion, x, y, colW)
        y = part(canvas, wk.tesouros, labels.mins, DocColors.TealDark, x, y, colW)
        y = part(canvas, wk.joias, labels.mins, DocColors.TealDark, x, y, colW)
        y = part(canvas, wk.leituraBiblia, labels.mins, DocColors.TealDark, x, y, colW)
        y -= 8f

        y = sectionBar(canvas, DocColors.Gold, iconMinisterio, labels.seamos1, labels.seamos2, null, labels.cancion, x, y, colW)
        wk.ministerio.forEach { y = part(canvas, it, labels.mins, DocColors.GoldDark, x, y, colW) }
        y -= 8f

        y = sectionBar(canvas, DocColors.Red, iconVida, labels.vida1, labels.vida2, wk.canticoMeio, labels.cancion, x, y, colW)
        wk.vida.forEach { y = part(canvas, it, labels.mins, DocColors.Red, x, y, colW) }
        y = studyPart(canvas, wk.estudo, labels, x, y, colW)
        y -= 10f

        val conclStyle = TextStyle(8.5f, DocColors.Navy, FontStyle.BOLD)
        canvas.textCentered("${labels.conclusion} ${wk.canticoFinal ?: "___"}", x, colW, y, conclStyle)
        y -= 12f
        wk.oracaoFinal?.takeIf { it.isNotBlank() }?.let {
            canvas.textCentered("${labels.oracaoFinal}: $it", x, colW, y, TextStyle(8.5f, DocColors.Navy, FontStyle.BOLD))
        }
    }

    private fun labelValue(
        canvas: DocumentCanvas,
        label: String,
        value: String?,
        x: Float,
        y: Float,
        colW: Float,
    ): Float {
        val labelStyle = TextStyle(8.5f, DocColors.Navy, FontStyle.BOLD)
        val valueStyle = TextStyle(8.5f, DocColors.Ink, FontStyle.BOLD)
        canvas.text(label, x, y - 9f, labelStyle)
        val lw = canvas.measure(label, labelStyle)
        canvas.text(canvas.fitText(value.orEmpty(), valueStyle, colW - lw - 10f), x + lw + 8f, y - 9f, valueStyle)
        return y - 14f
    }

    private fun sectionBar(
        canvas: DocumentCanvas,
        color: DocColor,
        icon: ByteArray?,
        line1: String,
        line2: String,
        cancion: String?,
        cancionLabel: String,
        x: Float,
        y: Float,
        colW: Float,
    ): Float {
        val iconSize = 22f
        val iconY = y - iconSize + 1f
        if (icon != null) {
            canvas.image(icon, x, iconY, iconSize, iconSize)
        } else {
            canvas.fillRect(x, iconY, iconSize, iconSize, color)
        }
        val tx = x + iconSize + 8f
        val style = TextStyle(9f, color, FontStyle.BOLD)
        canvas.text(line1, tx, y - 7f, style)
        canvas.text(line2, tx, y - 17f, style)
        if (!cancion.isNullOrBlank()) {
            val ct = "$cancionLabel $cancion"
            canvas.text(ct, x + colW - canvas.measure(ct, style), y - 17f, style)
        }
        val underlineY = y - 23f
        canvas.line(x, underlineY, x + colW, underlineY, color, 1f)
        return underlineY - 9f
    }

    private fun part(
        canvas: DocumentCanvas,
        part: MidweekPartPdf,
        mins: String,
        color: DocColor,
        x: Float,
        y: Float,
        colW: Float,
    ): Float {
        val titleStyle = TextStyle(8.5f, color, FontStyle.BOLD)
        val lineH = 11f
        val lines = canvas.wrapText("${part.numero}. ${part.titulo}", titleStyle, colW - 2f)
        lines.forEachIndexed { i, ln -> canvas.text(ln, x, y - 9f - i * lineH, titleStyle) }
        val lastLineW = canvas.measure(lines.last(), titleStyle)
        val lastY = y - 9f - (lines.size - 1) * lineH

        part.minutos?.let {
            val minStyle = TextStyle(7.5f, DocColors.Muted)
            val ms = " ($it $mins)"
            if (lastLineW + canvas.measure(ms, minStyle) <= colW) {
                canvas.text(ms, x + lastLineW, lastY, minStyle)
            }
        }

        var yy = lastY - lineH
        val names = listOfNotNull(
            part.nome1?.takeIf { it.isNotBlank() },
            part.nome2?.takeIf { it.isNotBlank() },
        )
        if (names.isNotEmpty()) {
            val nameStyle = TextStyle(8.5f, DocColors.Ink)
            canvas.wrapText(names.joinToString(" / "), nameStyle, colW - 12f).forEach {
                canvas.text(it, x + 10f, yy, nameStyle)
                yy -= lineH
            }
        }
        return yy - 3f
    }

    private fun studyPart(
        canvas: DocumentCanvas,
        part: MidweekPartPdf,
        labels: MidweekPdfStrings,
        x: Float,
        y: Float,
        colW: Float,
    ): Float {
        val titleStyle = TextStyle(8.5f, DocColors.Red, FontStyle.BOLD)
        val lineH = 11f
        val lines = canvas.wrapText("${part.numero}. ${part.titulo}", titleStyle, colW - 2f)
        lines.forEachIndexed { i, ln -> canvas.text(ln, x, y - 9f - i * lineH, titleStyle) }
        val lastLineW = canvas.measure(lines.last(), titleStyle)
        val lastY = y - 9f - (lines.size - 1) * lineH

        part.minutos?.let {
            val minStyle = TextStyle(7.5f, DocColors.Muted)
            val ms = " ($it ${labels.mins})"
            if (lastLineW + canvas.measure(ms, minStyle) <= colW) {
                canvas.text(ms, x + lastLineW, lastY, minStyle)
            }
        }

        // Tabela Dirigente / Leitor
        val tableW = colW - 20f
        val tx = x + 10f
        var ty = lastY - 8f
        val rowH = 15f
        val half = tableW / 2f

        canvas.fillRect(tx, ty - rowH, tableW, rowH, DocColors.Red)
        val headStyle = TextStyle(8f, DocColor.White, FontStyle.BOLD)
        canvas.textCentered(labels.conductor, tx, half, ty - rowH + 4.5f, headStyle)
        canvas.textCentered(labels.lector, tx + half, half, ty - rowH + 4.5f, headStyle)
        ty -= rowH

        canvas.strokeRect(tx, ty - rowH, tableW, rowH, DocColors.Red, 0.7f)
        canvas.line(tx + half, ty, tx + half, ty - rowH, DocColors.Red, 0.7f)

        // A linha de base fica dentro da celula, nao colada na faixa de cima.
        val cellStyle = TextStyle(8f, DocColors.Ink)
        val cellBaseline = ty - rowH + 4.5f
        canvas.textCentered(canvas.fitText(part.nome1.orEmpty(), cellStyle, half - 6f), tx, half, cellBaseline, cellStyle)
        canvas.textCentered(canvas.fitText(part.nome2.orEmpty(), cellStyle, half - 6f), tx + half, half, cellBaseline, cellStyle)
        return ty - rowH - 6f
    }
}
