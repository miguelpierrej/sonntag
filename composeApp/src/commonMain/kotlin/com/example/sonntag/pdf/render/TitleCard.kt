package com.example.sonntag.pdf.render

private const val CARD_TOP_MARGIN = 34f
private const val CARD_HEIGHT = 62f
private const val CARD_BOTTOM_GAP = 24f

/**
 * Espaco vertical que o cartao de titulo consome, do topo da folha ate a primeira
 * linha de conteudo. Quem pagina antes de desenhar precisa deste numero.
 */
const val TITLE_CARD_SPACE = CARD_TOP_MARGIN + CARD_HEIGHT + CARD_BOTTOM_GAP

/**
 * Cartao de titulo dos documentos: o nome do documento a esquerda e um bloco azul
 * a direita com a congregacao, como nos modelos impressos. Devolve o Y onde o
 * conteudo pode comecar.
 */
fun DocumentCanvas.titleCard(
    marginLeft: Float,
    contentWidth: Float,
    title: String,
    subtitle: String?,
    congregacaoLabel: String,
    congregacao: String,
): Float {
    val cardHeight = CARD_HEIGHT
    val top = pageHeight - CARD_TOP_MARGIN
    val bottom = top - cardHeight
    val radius = 8f

    // O bloco da congregacao ocupa a direita do cartao; o contorno abraca os dois.
    val blockWidth = contentWidth * 0.42f
    val blockX = marginLeft + contentWidth - blockWidth
    fillRect(blockX, bottom, blockWidth, cardHeight, DocColors.Navy, radius)
    strokeRect(marginLeft, bottom, contentWidth, cardHeight, DocColors.Navy, 1.2f, radius)

    val titleStyle = TextStyle(18f, DocColors.Navy, FontStyle.BOLD)
    val tituloWidth = blockX - marginLeft
    textCentered(fitText(title, titleStyle, tituloWidth - 24f), marginLeft, tituloWidth, bottom + 32f, titleStyle)
    subtitle?.takeIf { it.isNotBlank() }?.let {
        val style = TextStyle(10f, DocColors.Navy, FontStyle.BOLD)
        textCentered(fitText(it, style, tituloWidth - 24f), marginLeft, tituloWidth, bottom + 15f, style)
    }

    val labelStyle = TextStyle(8.5f, DocColor.White)
    val nomeStyle = TextStyle(12f, DocColor.White, FontStyle.BOLD)
    textCentered(congregacaoLabel, blockX, blockWidth, bottom + 36f, labelStyle)
    textCentered(fitText(congregacao, nomeStyle, blockWidth - 20f), blockX, blockWidth, bottom + 18f, nomeStyle)

    return bottom - CARD_BOTTOM_GAP
}
