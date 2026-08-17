package com.example.sonntag.pdf.render

import com.example.sonntag.pdf.AvScheduleLine
import com.example.sonntag.pdf.AvSchedulePdfData

/**
 * Folha de audio/video e acomodadores: um bloco por reuniao, quatro colunas de
 * nomes, com quebra de pagina quando o bloco nao cabe.
 */
class AvScheduleLayout(
    private val data: AvSchedulePdfData,
    private val geradoEm: String,
) {

    fun draw(canvas: DocumentCanvas) {
        val marginLeft = 45f
        val marginRight = 45f
        val marginBottom = 55f
        val contentWidth = canvas.pageWidth - marginLeft - marginRight

        // Quatro colunas: audio/video, plataforma, microfones, acomodadores.
        val colX = listOf(0f, 0.29f, 0.50f, 0.75f).map { marginLeft + 12f + contentWidth * it }
        val colWidth = listOf(0.27f, 0.19f, 0.23f, 0.25f).map { contentWidth * it }

        val dateStyle = TextStyle(10f, DocColor.White, FontStyle.BOLD)
        val headerStyle = TextStyle(8.5f, DocColors.Title, FontStyle.BOLD)
        val nameStyle = TextStyle(9f, DocColors.Title)
        val lineHeight = 13.5f

        /** Faixa azul atras da data: separa uma reuniao da outra de longe. */
        val dateBandHeight = 18f
        val dateBandBelow = 5f
        val dateToHeaderGap = 20f
        val headerToNamesGap = 13.5f
        val blockGap = 14f

        fun startPage(isFirst: Boolean): Float {
            if (!isFirst) canvas.newPage()
            return drawHeader(canvas, marginLeft, contentWidth)
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
            canvas.text(data.labels.vazio, marginLeft, y, TextStyle(11f, DocColors.Muted, FontStyle.ITALIC))
        }

        data.reunioes.forEach { line ->
            val columns = columnsFor(line)
            val maxNameLines = columns.maxOfOrNull { it.size } ?: 0
            val blockHeight = dateToHeaderGap + headerToNamesGap + maxNameLines * lineHeight + blockGap

            if (y - blockHeight < marginBottom) {
                y = startPage(isFirst = false)
            }

            canvas.fillRect(marginLeft, y - dateBandBelow, contentWidth, dateBandHeight, DocColors.Navy)
            canvas.text("${line.dataLabel} ${line.tipoLabel}", marginLeft + 8f, y, dateStyle)
            y -= dateToHeaderGap

            val headers = listOf(
                data.labels.audioVideo,
                data.labels.plataforma,
                data.labels.microfones,
                data.labels.acomodadores,
            )
            headers.forEachIndexed { i, header ->
                canvas.text(canvas.fitText(header, headerStyle, colWidth[i]), colX[i], y, headerStyle)
            }
            y -= headerToNamesGap

            columns.forEachIndexed { i, names ->
                names.forEachIndexed { row, name ->
                    canvas.text(
                        canvas.fitText(name, nameStyle, colWidth[i]),
                        colX[i],
                        y - row * lineHeight,
                        nameStyle,
                    )
                }
            }
            y -= maxNameLines * lineHeight + blockGap
        }

        val footerStyle = TextStyle(8f, DocColors.Muted)
        val footerWidth = canvas.measure(geradoEm, footerStyle)
        canvas.text(geradoEm, marginLeft + contentWidth - footerWidth, marginBottom - 15f, footerStyle)
    }

    /** Mesmo cartao de titulo dos demais documentos. Devolve o Y do primeiro bloco. */
    private fun drawHeader(canvas: DocumentCanvas, marginLeft: Float, contentWidth: Float): Float =
        canvas.titleCard(
            marginLeft = marginLeft,
            contentWidth = contentWidth,
            title = data.labels.title,
            subtitle = data.mesLabel,
            congregacaoLabel = data.labels.common.congregacao,
            congregacao = data.congregacao,
        )
}
