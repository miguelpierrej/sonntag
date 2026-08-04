package com.example.sonntag.pdf.render

/** Quebra [text] em linhas que cabem em [maxWidth]. */
fun DocumentCanvas.wrapText(text: String, style: TextStyle, maxWidth: Float): List<String> {
    if (text.isBlank()) return listOf("")
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var current = StringBuilder()
    words.forEach { word ->
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (measure(candidate, style) <= maxWidth || current.isEmpty()) {
            current = StringBuilder(candidate)
        } else {
            lines += current.toString()
            current = StringBuilder(word)
        }
    }
    if (current.isNotEmpty()) lines += current.toString()
    return lines
}

/** Corta [text] com reticencias ate caber em [maxWidth]. */
fun DocumentCanvas.fitText(text: String, style: TextStyle, maxWidth: Float): String {
    if (measure(text, style) <= maxWidth) return text
    var cut = text
    while (cut.isNotEmpty() && measure("$cut…", style) > maxWidth) {
        cut = cut.dropLast(1)
    }
    return "$cut…"
}

/** Escreve [text] centralizado no intervalo horizontal informado. */
fun DocumentCanvas.textCentered(text: String, left: Float, width: Float, y: Float, style: TextStyle) {
    val x = left + (width - measure(text, style)) / 2f
    text(text, x, y, style)
}
