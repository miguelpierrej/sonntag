package com.example.sonntag.pdf.render

/**
 * Faixa de cabecalho dos documentos: congregacao, titulo, subtitulo e o icone
 * grande na borda direita. Devolve o Y onde o conteudo pode comecar.
 */
fun DocumentCanvas.headerBand(
    marginLeft: Float,
    contentWidth: Float,
    congregacao: String,
    title: String,
    subtitle: String,
    iconBytes: ByteArray?,
): Float {
    val headerBandTop = pageHeight - 50f
    val headerBandHeight = 80f
    val headerBandBottom = headerBandTop - headerBandHeight

    val iconSize = 56f
    iconBytes?.let {
        image(
            it,
            marginLeft + contentWidth - iconSize,
            headerBandBottom + (headerBandHeight - iconSize) / 2f,
            iconSize,
            iconSize,
        )
    }

    text(congregacao, marginLeft, headerBandTop - 14f, TextStyle(16f, DocColors.Title, FontStyle.BOLD))
    text(title, marginLeft, headerBandTop - 44f, TextStyle(20f, DocColors.Title, FontStyle.BOLD))
    text(subtitle, marginLeft, headerBandTop - 66f, TextStyle(14f, DocColors.Muted))

    line(marginLeft, headerBandBottom, marginLeft + contentWidth, headerBandBottom, DocColors.Border, 0.5f)
    return headerBandBottom - 16f
}

/** Cabecalho enxuto das paginas seguintes: so uma linha de contexto. */
fun DocumentCanvas.compactHeader(marginLeft: Float, contentWidth: Float, text: String): Float {
    val top = pageHeight - 40f
    text(text, marginLeft, top, TextStyle(11f, DocColors.Muted))
    line(marginLeft, top - 8f, marginLeft + contentWidth, top - 8f, DocColors.Border, 0.5f)
    return top - 24f
}
