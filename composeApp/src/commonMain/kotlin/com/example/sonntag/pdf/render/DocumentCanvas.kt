package com.example.sonntag.pdf.render

/**
 * Superficie de desenho independente de plataforma, usada pelos layouts dos
 * documentos. Cada plataforma implementa as primitivas; o posicionamento e escrito
 * uma vez so.
 *
 * **Convencao de coordenadas: origem no canto inferior esquerdo, Y crescendo para
 * cima, unidade em pontos** — a mesma do PDF. As implementacoes que desenham em
 * bitmap (onde Y cresce para baixo) fazem a inversao internamente.
 */
interface DocumentCanvas {

    /** Altura util da pagina, necessaria para quem calcula a partir do topo. */
    val pageHeight: Float

    /** Largura util da pagina. */
    val pageWidth: Float

    fun text(text: String, x: Float, y: Float, style: TextStyle)

    /** Largura que [text] ocupa — usado para centralizar e para quebrar linha. */
    fun measure(text: String, style: TextStyle): Float

    fun fillRect(x: Float, y: Float, width: Float, height: Float, color: DocColor, radius: Float = 0f)

    fun strokeRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: DocColor,
        lineWidth: Float = 0.5f,
        radius: Float = 0f,
    )

    fun line(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: DocColor,
        lineWidth: Float = 0.5f,
        dashed: Boolean = false,
    )

    /** [bytes] e um PNG; a imagem e esticada para o retangulo informado. */
    fun image(bytes: ByteArray, x: Float, y: Float, width: Float, height: Float)

    /** Encerra a pagina atual e comeca outra. Em PNG, os layouts nao chamam. */
    fun newPage()
}

/** Cor em componentes 0..255, sem depender de java.awt nem de android.graphics. */
data class DocColor(val red: Int, val green: Int, val blue: Int) {
    companion object {
        fun hex(value: Int): DocColor =
            DocColor((value shr 16) and 0xFF, (value shr 8) and 0xFF, value and 0xFF)

        val White = DocColor(255, 255, 255)
        val Black = DocColor(0, 0, 0)
    }
}

enum class FontStyle { REGULAR, BOLD, ITALIC }

data class TextStyle(
    val size: Float,
    val color: DocColor,
    val font: FontStyle = FontStyle.REGULAR,
)
