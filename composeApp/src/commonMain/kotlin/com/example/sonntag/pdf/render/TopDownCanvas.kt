package com.example.sonntag.pdf.render

/**
 * Adaptador para layouts escritos **de cima para baixo** — o caso dos PNG, que
 * nasceram sobre o Graphics2D.
 *
 * O [DocumentCanvas] usa a convencao do PDF (origem embaixo, Y para cima). Em vez
 * de reescrever toda a aritmetica de Y desses layouts, o que costuma introduzir
 * erros de um pixel aqui e ali, a conversao acontece num lugar so: aqui.
 *
 * O baseline do texto tem o mesmo significado nas duas convencoes, entao para texto
 * basta espelhar. Para retangulos, o (x, y) de cima vira o canto inferior.
 */
class TopDownCanvas(private val inner: DocumentCanvas) {

    val width: Float get() = inner.pageWidth
    val height: Float get() = inner.pageHeight

    private fun flip(y: Float): Float = height - y

    fun text(text: String, x: Float, baseline: Float, style: TextStyle) =
        inner.text(text, x, flip(baseline), style)

    fun measure(text: String, style: TextStyle): Float = inner.measure(text, style)

    fun fillRect(x: Float, top: Float, w: Float, h: Float, color: DocColor, radius: Float = 0f) =
        inner.fillRect(x, flip(top + h), w, h, color, radius)

    fun strokeRect(
        x: Float,
        top: Float,
        w: Float,
        h: Float,
        color: DocColor,
        lineWidth: Float = 1f,
        radius: Float = 0f,
    ) = inner.strokeRect(x, flip(top + h), w, h, color, lineWidth, radius)

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: DocColor, lineWidth: Float = 1f) =
        inner.line(x1, flip(y1), x2, flip(y2), color, lineWidth)

    fun image(bytes: ByteArray, x: Float, top: Float, w: Float, h: Float) =
        inner.image(bytes, x, flip(top + h), w, h)

    fun wrapText(text: String, style: TextStyle, maxWidth: Float): List<String> =
        inner.wrapText(text, style, maxWidth)

    fun fitText(text: String, style: TextStyle, maxWidth: Float): String =
        inner.fitText(text, style, maxWidth)
}
