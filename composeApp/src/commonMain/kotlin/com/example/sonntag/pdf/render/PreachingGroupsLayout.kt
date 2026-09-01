package com.example.sonntag.pdf.render

import com.example.sonntag.pdf.PreachingGroupMemberPdf
import com.example.sonntag.pdf.PreachingGroupSheetPdf
import com.example.sonntag.pdf.PreachingGroupsPdfData

/**
 * Folha dos grupos de pregacao: um grupo por coluna, lado a lado, com o dirigente, o
 * auxiliar e o ponto de encontro no alto e os publicadores embaixo.
 *
 * Sao tres colunas por pagina; passando disso, a folha continua na proxima com o
 * mesmo cabecalho azul. Um grupo grande demais para uma coluna tambem continua na
 * pagina seguinte, com o nome repetido e a marca de continuacao.
 */
class PreachingGroupsLayout(
    private val data: PreachingGroupsPdfData,
    private val geradoEm: String,
) {

    private val marginLeft = 45f
    private val marginRight = 45f
    private val marginBottom = 55f

    private val colunasMax = 3
    private val colGap = 14f

    /**
     * Largura maxima de uma coluna. Com um grupo so, esticar a coluna pela folha
     * inteira deixaria um nome perdido no meio de um palmo de branco.
     */
    private val colunaLarguraMax = 250f

    private val headerHeight = 22f
    private val cargoLabelLine = 9.5f
    private val cargoValueLine = 10.5f
    private val cargoGap = 4f
    private val memberLine = 13f
    private val boxPadTop = 8f
    private val boxPadBottom = 8f
    private val separatorGap = 7f
    private val textPad = 8f

    private val nomeGrupoStyle = TextStyle(10f, DocColor.White, FontStyle.BOLD)
    private val labelStyle = TextStyle(6.5f, DocColors.Muted)
    private val cargoStyle = TextStyle(8.5f, DocColors.NavyInk, FontStyle.BOLD)
    private val membroStyle = TextStyle(9f, DocColors.Ink)
    private val siglaStyle = TextStyle(7f, DocColors.Muted, FontStyle.BOLD)
    private val vazioStyle = TextStyle(8.5f, DocColors.Muted, FontStyle.ITALIC)
    private val legendaStyle = TextStyle(7.5f, DocColors.Muted)

    /** Um pedaco de coluna: o grupo inteiro, ou o que dele coube nesta pagina. */
    private data class Parte(
        val grupo: PreachingGroupSheetPdf,
        val membros: List<PreachingGroupMemberPdf>,
        val continuacao: Boolean,
    )

    fun draw(canvas: DocumentCanvas) {
        val contentWidth = canvas.pageWidth - marginLeft - marginRight

        // Com dois grupos nao ha por que espremer cada um num terco da folha: as
        // colunas ocupam o que ha, ate um limite, e o conjunto fica centralizado.
        val colunas = data.grupos.size.coerceIn(1, colunasMax)
        val colWidth = ((contentWidth - colGap * (colunas - 1)) / colunas).coerceAtMost(colunaLarguraMax)
        val larguraTotal = colWidth * colunas + colGap * (colunas - 1)
        val xInicial = marginLeft + (contentWidth - larguraTotal) / 2f

        // A legenda so aparece quando alguem tem responsabilidade; sem isso ela seria
        // um rodape explicando siglas que nao estao na folha.
        val legenda = data.labels.legendaSiglas
            .takeIf { data.grupos.any { g -> g.membros.any { !it.siglas.isNullOrBlank() } } }
        val alturaLegenda = if (legenda == null) 0f else 18f

        // A altura util e igual em todas as paginas: o cartao de titulo se repete.
        val alturaUtil = canvas.pageHeight - TITLE_CARD_SPACE - marginBottom - alturaLegenda

        val paginas = paginar(canvas, alturaUtil, colWidth).chunked(colunas)
        if (paginas.isEmpty()) {
            val y = cabecalho(canvas, contentWidth)
            canvas.text(data.labels.semGrupos, marginLeft, y - 12f, vazioStyle)
            rodape(canvas, contentWidth, 1, 1, null)
            return
        }

        paginas.forEachIndexed { index, pagina ->
            if (index > 0) canvas.newPage()
            val y = cabecalho(canvas, contentWidth)
            // Todas as colunas da pagina terminam na mesma linha: a folha impressa
            // fica com a base reta, mesmo com grupos de tamanhos diferentes.
            val altura = pagina.maxOf { alturaDaParte(canvas, it, colWidth) }
            pagina.forEachIndexed { coluna, parte ->
                desenhaColuna(canvas, parte, xInicial + coluna * (colWidth + colGap), y, colWidth, altura)
            }
            rodape(canvas, contentWidth, index + 1, paginas.size, legenda)
        }
    }

    // ─── Paginacao ───────────────────────────────────────────────────────────

    /** Quantas linhas de nome cabem numa coluna, com e sem o bloco dos cargos. */
    private fun linhasQueCabem(alturaUtil: Float, cargos: Float): Int =
        ((alturaUtil - headerHeight - boxPadTop - cargos - boxPadBottom) / memberLine).toInt().coerceAtLeast(1)

    private fun paginar(canvas: DocumentCanvas, alturaUtil: Float, largura: Float): List<Parte> {
        val partes = mutableListOf<Parte>()
        val emContinuacao = linhasQueCabem(alturaUtil, 0f)
        data.grupos.forEach { grupo ->
            val naPrimeira = linhasQueCabem(alturaUtil, alturaDosCargos(canvas, grupo, largura))
            if (grupo.membros.size <= naPrimeira) {
                partes += Parte(grupo, grupo.membros, continuacao = false)
            } else {
                partes += Parte(grupo, grupo.membros.take(naPrimeira), continuacao = false)
                grupo.membros.drop(naPrimeira).chunked(emContinuacao).forEach {
                    partes += Parte(grupo, it, continuacao = true)
                }
            }
        }
        return partes
    }

    private fun cargosDe(grupo: PreachingGroupSheetPdf): List<Pair<String, String>> = listOfNotNull(
        grupo.dirigente?.takeIf { it.isNotBlank() }?.let { data.labels.dirigente to it },
        grupo.auxiliar?.takeIf { it.isNotBlank() }?.let { data.labels.auxiliar to it },
        grupo.ponto?.takeIf { it.isNotBlank() }?.let { data.labels.ponto to it },
    )

    /**
     * O valor do cargo quebra em ate tres linhas em vez de sair cortado: um ponto de
     * encontro chamado "Salão do Reino das Testemunhas de Jeová - Arere" nao cabe numa
     * linha em largura nenhuma, e cortado no meio nao diz onde e.
     */
    private fun linhasDoCargo(canvas: DocumentCanvas, valor: String, largura: Float): List<String> =
        canvas.wrapText(valor, cargoStyle, largura - textPad * 2f).take(3)

    private fun alturaDosCargos(canvas: DocumentCanvas, grupo: PreachingGroupSheetPdf, largura: Float): Float {
        val cargos = cargosDe(grupo)
        if (cargos.isEmpty()) return 0f
        return cargos.sumOf { (_, valor) ->
            (cargoLabelLine + linhasDoCargo(canvas, valor, largura).size * cargoValueLine + cargoGap).toDouble()
        }.toFloat() + separatorGap
    }

    private fun alturaDaParte(canvas: DocumentCanvas, parte: Parte, largura: Float): Float {
        val cargos = if (parte.continuacao) 0f else alturaDosCargos(canvas, parte.grupo, largura)
        val linhas = parte.membros.size.coerceAtLeast(1)
        return headerHeight + boxPadTop + cargos + linhas * memberLine + boxPadBottom
    }

    // ─── Desenho ─────────────────────────────────────────────────────────────

    private fun cabecalho(canvas: DocumentCanvas, contentWidth: Float): Float =
        canvas.titleCard(
            marginLeft = marginLeft,
            contentWidth = contentWidth,
            title = data.labels.tituloGrupos,
            subtitle = data.subtitulo,
            congregacaoLabel = data.labels.common.congregacao,
            congregacao = data.congregacao,
        )

    private fun desenhaColuna(
        canvas: DocumentCanvas,
        parte: Parte,
        x: Float,
        yTop: Float,
        largura: Float,
        altura: Float,
    ) {
        val raio = 4f
        canvas.strokeRect(x, yTop - altura, largura, altura, DocColors.Border, 0.7f, raio)

        // A faixa do nome e arredondada como a caixa; o retangulo baixo esquadreja a
        // borda de baixo, para faixa e conteudo encostarem sem degrau.
        val faixaBase = yTop - headerHeight
        canvas.fillRect(x, faixaBase, largura, headerHeight, DocColors.Navy, raio)
        canvas.fillRect(x, faixaBase, largura, raio, DocColors.Navy)

        val nome = if (parte.continuacao) "${parte.grupo.nome} ${data.labels.continuacao}" else parte.grupo.nome
        canvas.textCentered(
            canvas.fitText(nome, nomeGrupoStyle, largura - 12f),
            x,
            largura,
            faixaBase + 7f,
            nomeGrupoStyle,
        )

        var y = faixaBase - boxPadTop

        if (!parte.continuacao) {
            val cargos = cargosDe(parte.grupo)
            cargos.forEach { (label, valor) ->
                canvas.text(label, x + textPad, y - 7f, labelStyle)
                y -= cargoLabelLine
                linhasDoCargo(canvas, valor, largura).forEach { linha ->
                    canvas.text(linha, x + textPad, y - 8f, cargoStyle)
                    y -= cargoValueLine
                }
                y -= cargoGap
            }
            if (cargos.isNotEmpty()) {
                canvas.line(x + textPad, y - 2f, x + largura - textPad, y - 2f, DocColors.Separator, 0.7f)
                y -= separatorGap
            }
        }

        if (parte.membros.isEmpty()) {
            canvas.text(data.labels.semMembros, x + textPad, y - 9.5f, vazioStyle)
            return
        }

        parte.membros.forEachIndexed { i, membro ->
            // Zebra: numa coluna de nomes soltos, a faixa clara e o que guia o olho.
            if (i % 2 == 1) {
                canvas.fillRect(x + 1f, y - memberLine, largura - 2f, memberLine, DocColors.Zebra)
            }
            // As siglas ficam encostadas na direita e o nome recebe o que sobra: assim
            // a responsabilidade nunca some por causa de um nome comprido.
            val siglas = membro.siglas?.takeIf { it.isNotBlank() }
            val larguraSiglas = siglas?.let { canvas.measure(it, siglaStyle) + 6f } ?: 0f
            if (siglas != null) {
                canvas.text(
                    siglas,
                    x + largura - textPad - canvas.measure(siglas, siglaStyle),
                    y - 9f,
                    siglaStyle,
                )
            }
            canvas.text(
                canvas.fitText(membro.nome, membroStyle, largura - textPad * 2f - larguraSiglas),
                x + textPad,
                y - 9.5f,
                membroStyle,
            )
            y -= memberLine
        }
    }

    private fun rodape(
        canvas: DocumentCanvas,
        contentWidth: Float,
        pagina: Int,
        total: Int,
        legenda: String?,
    ) {
        val style = TextStyle(8f, DocColors.Muted)
        legenda?.let {
            canvas.line(marginLeft, marginBottom + 12f, marginLeft + contentWidth, marginBottom + 12f, DocColors.Separator, 0.6f)
            canvas.text(it, marginLeft, marginBottom + 2f, legendaStyle)
        }
        if (total > 1) {
            canvas.text(data.labels.common.pagina(pagina, total), marginLeft, marginBottom - 14f, style)
        }
        val timestamp = data.labels.common.geradoEm(geradoEm)
        canvas.text(
            timestamp,
            marginLeft + contentWidth - canvas.measure(timestamp, style),
            marginBottom - 14f,
            style,
        )
    }
}
