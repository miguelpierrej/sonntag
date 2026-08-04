package com.example.sonntag.imports

/**
 * Palavra desenhada numa pagina do PDF, com a caixa que ocupa.
 *
 * A extracao das posicoes depende da biblioteca de cada plataforma (PDFBox no
 * desktop, pdfbox-android no celular), mas o que se faz com elas — agrupar por
 * coluna, juntar acentos soltos — e o mesmo nos dois. Por isso a fronteira e aqui.
 */
data class PdfWord(
    val text: String,
    val x0: Float,
    val x1: Float,
    val y: Float,
    val spaceWidth: Float,
)

/** Palavras de cada pagina do PDF, na ordem das paginas. */
expect fun extractPdfWords(bytes: ByteArray): List<List<PdfWord>>
