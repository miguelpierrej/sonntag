package com.example.sonntag.pdf.render

import com.example.sonntag.pdf.MidweekPartPdf
import com.example.sonntag.pdf.MidweekPdfStrings
import com.example.sonntag.pdf.MidweekProgramPdfData
import com.example.sonntag.pdf.MidweekWeekPdf

/** Programa de meio de semana (S-140): duas semanas por folha A4, lado a lado. */
class MidweekProgramLayout(private val data: MidweekProgramPdfData) {

    private val marginLeft = 40f
    private val marginRight = 40f
    private val marginBottom = 40f
    private val gap = 24f

    fun draw(canvas: DocumentCanvas) {
        val contentWidth = canvas.pageWidth - marginLeft - marginRight
        val colWidth = (contentWidth - gap) / 2f

        val chunks = data.semanas.chunked(2).ifEmpty { listOf(emptyList()) }
        chunks.forEachIndexed { pageIndex, pair ->
            if (pageIndex > 0) canvas.newPage()
            val headerBottom = drawHeader(canvas, contentWidth)
            val colTop = headerBottom - 14f

            if (pair.size == 2) {
                val dividerX = marginLeft + colWidth + gap / 2f
                canvas.line(dividerX, colTop, dividerX, marginBottom, DocColors.Border, 0.8f, dashed = true)
            }
            pair.forEachIndexed { idx, week ->
                drawWeek(canvas, week, marginLeft + idx * (colWidth + gap), colTop, colWidth, data.labels)
            }
        }
    }

    private fun drawHeader(canvas: DocumentCanvas, contentWidth: Float): Float {
        val top = canvas.pageHeight - 40f
        canvas.text(data.labels.headerTitle, marginLeft, top - 20f, TextStyle(23f, DocColors.Maroon, FontStyle.BOLD))
        canvas.text(data.labels.headerSubtitle, marginLeft, top - 40f, TextStyle(15f, DocColors.Maroon))
        canvas.text(data.labels.headerGuide, marginLeft, top - 54f, TextStyle(8f, DocColors.Muted, FontStyle.BOLD))

        // Caixa cinza com o nome da congregacao
        val boxW = 175f
        val boxH = 58f
        val boxX = canvas.pageWidth - marginRight - boxW
        val boxY = top - boxH
        canvas.fillRect(boxX, boxY, boxW, boxH, DocColors.GrayBox)

        val nameStyle = TextStyle(12f, DocColors.Maroon, FontStyle.BOLD)
        canvas.wrapText(data.congregacao, nameStyle, boxW - 20f).take(2).forEachIndexed { i, ln ->
            canvas.text(ln, boxX + 12f, boxY + boxH - 16f - i * 14f, nameStyle)
        }
        data.subtitulo?.takeIf { it.isNotBlank() }?.let {
            val style = TextStyle(9.5f, DocColors.Maroon)
            canvas.text(canvas.fitText(it, style, boxW - 20f), boxX + 12f, boxY + 10f, style)
        }
        return canvas.pageHeight - 40f - 66f
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

        val bandH = 20f
        canvas.fillRect(x, y - bandH, colW, bandH, DocColors.Maroon)
        val bandStyle = TextStyle(8.5f, DocColor.White, FontStyle.BOLD)
        val band = listOf(wk.periodo, wk.leitura).filter { it.isNotBlank() }.joinToString("  ").uppercase()
        canvas.text(canvas.fitText(band, bandStyle, colW - 12f), x + 7f, y - bandH + 6.5f, bandStyle)
        y -= bandH + 12f

        y = labelValue(canvas, labels.presidente, wk.presidente, x, y, colW)
        y = labelValue(canvas, labels.oracaoInicial, wk.oracaoInicial, x, y, colW)
        y -= 8f

        y = sectionBar(canvas, DocColors.Teal, labels.tesouros1, labels.tesouros2, wk.canticoInicial, labels.cancion, x, y, colW)
        y = part(canvas, wk.tesouros, labels.mins, x, y, colW)
        y = part(canvas, wk.joias, labels.mins, x, y, colW)
        y = part(canvas, wk.leituraBiblia, labels.mins, x, y, colW)
        y -= 8f

        y = sectionBar(canvas, DocColors.Gold, labels.seamos1, labels.seamos2, null, labels.cancion, x, y, colW)
        wk.ministerio.forEach { y = part(canvas, it, labels.mins, x, y, colW) }
        y -= 8f

        y = sectionBar(canvas, DocColors.Maroon, labels.vida1, labels.vida2, wk.canticoMeio, labels.cancion, x, y, colW)
        wk.vida.forEach { y = part(canvas, it, labels.mins, x, y, colW) }
        y = studyPart(canvas, wk.estudo, labels, x, y, colW)
        y -= 10f

        val conclStyle = TextStyle(8.5f, DocColors.Maroon, FontStyle.BOLD)
        canvas.textCentered("${labels.conclusion} ${wk.canticoFinal ?: "___"}", x, colW, y, conclStyle)
        y -= 12f
        wk.oracaoFinal?.takeIf { it.isNotBlank() }?.let {
            canvas.textCentered("${labels.oracaoFinal}: $it", x, colW, y, TextStyle(8.5f, DocColors.Ink, FontStyle.BOLD))
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
        val labelStyle = TextStyle(8.5f, DocColors.Maroon, FontStyle.BOLD)
        val valueStyle = TextStyle(8.5f, DocColors.Ink, FontStyle.BOLD)
        canvas.text(label, x, y - 9f, labelStyle)
        val lw = canvas.measure(label, labelStyle)
        canvas.text(canvas.fitText(value.orEmpty(), valueStyle, colW - lw - 10f), x + lw + 8f, y - 9f, valueStyle)
        return y - 14f
    }

    private fun sectionBar(
        canvas: DocumentCanvas,
        color: DocColor,
        line1: String,
        line2: String,
        cancion: String?,
        cancionLabel: String,
        x: Float,
        y: Float,
        colW: Float,
    ): Float {
        val iconSize = 13f
        canvas.fillRect(x, y - iconSize - 2f, iconSize, iconSize, color)
        val tx = x + iconSize + 8f
        val style = TextStyle(9f, color, FontStyle.BOLD)
        canvas.text(line1, tx, y - 7f, style)
        canvas.text(line2, tx, y - 17f, style)
        if (!cancion.isNullOrBlank()) {
            val songStyle = TextStyle(8.5f, color, FontStyle.BOLD)
            val ct = "$cancionLabel $cancion"
            canvas.text(ct, x + colW - canvas.measure(ct, songStyle), y - 17f, songStyle)
        }
        val underlineY = y - 23f
        canvas.line(x, underlineY, x + colW, underlineY, color, 1f)
        return underlineY - 9f
    }

    private fun part(
        canvas: DocumentCanvas,
        part: MidweekPartPdf,
        mins: String,
        x: Float,
        y: Float,
        colW: Float,
    ): Float {
        val titleStyle = TextStyle(8.5f, DocColors.Maroon, FontStyle.BOLD)
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
            val nameStyle = TextStyle(8.5f, DocColors.Ink, FontStyle.BOLD)
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
        val titleStyle = TextStyle(8.5f, DocColors.Maroon, FontStyle.BOLD)
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

        canvas.fillRect(tx, ty - rowH, tableW, rowH, DocColors.TableHeader)
        val headStyle = TextStyle(8f, DocColors.Maroon, FontStyle.BOLD)
        canvas.textCentered(labels.conductor, tx, half, ty - rowH + 4.5f, headStyle)
        canvas.textCentered(labels.lector, tx + half, half, ty - rowH + 4.5f, headStyle)
        ty -= rowH

        canvas.strokeRect(tx, ty - rowH, tableW, rowH, DocColors.Border, 0.7f)
        canvas.line(tx + half, ty, tx + half, ty - rowH, DocColors.Border, 0.7f)

        val cellStyle = TextStyle(8f, DocColors.Ink, FontStyle.BOLD)
        canvas.textCentered(canvas.fitText(part.nome1.orEmpty(), cellStyle, half - 6f), tx, half, ty - 3f, cellStyle)
        canvas.textCentered(canvas.fitText(part.nome2.orEmpty(), cellStyle, half - 6f), tx + half, half, ty - 3f, cellStyle)
        return ty - rowH - 6f
    }
}
