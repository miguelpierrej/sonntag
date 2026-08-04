package com.example.sonntag.imports

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import kotlin.math.abs

/**
 * Extrai o texto da apostila respeitando as duas colunas da pagina.
 *
 * O [PDFTextStripper] comum devolve a ordem visual (esquerda -> direita, linha a linha),
 * o que concatena as duas colunas na mesma linha e mistura partes que nada tem a ver uma
 * com a outra. Aqui as palavras sao agrupadas por coluna antes de virar texto, entao cada
 * coluna e lida inteira de cima para baixo, como se le de verdade.
 *
 * A geometria tambem resolve os espacos espurios da fonte ("Canció n"): a apostila desenha
 * alguns acentos como glifo separado, e o PDFBox insere um espaco entre eles. Colados, os
 * pedacos ficam a distancia ~0; um espaco de verdade mede quase a largura de um espaco.
 * Ver [joinWords].
 */
object MwbTextExtractor {

    /** Palavra desenhada na pagina, com a caixa que ocupa. */
    private data class Word(
        val text: String,
        val x0: Float,
        val x1: Float,
        val y: Float,
        val spaceWidth: Float,
    )

    /** Palavras da mesma linha ficam a menos disso em y (entrelinha da apostila e ~11). */
    private const val LINE_TOLERANCE = 3f

    /** Abaixo desta fracao da largura de um espaco, o "espaco" e artefato da fonte. */
    private const val GLUE_RATIO = 0.3f

    /** Uma calha de verdade e atravessada por quase nada (so a arte dos titulos). */
    private const val MAX_GUTTER_CROSSINGS = 4

    /**
     * Vao minimo, na calha, para considerar que a linha e mesmo de duas colunas. A separacao
     * e nitida na apostila: o vao entre colunas passa de 50, enquanto um espaco entre
     * palavras fica em 2-4. Sem esse piso, uma linha de largura total ("Palabras de
     * conclusión (3 mins.) Canción 156 y oración") seria partida ao meio.
     */
    private const val MIN_GUTTER_GAP = 12f

    /** Distancia que isola o numero da pagina do resto da primeira linha. */
    private const val ISOLATION_GAP = 30f

    fun extract(document: PDDocument): String {
        val sb = StringBuilder()
        for (page in 1..document.numberOfPages) {
            val words = semNumeroDaPagina(wordsOf(document, page))
            if (words.isEmpty()) continue
            sb.append(layoutPage(words))
        }
        return sb.toString()
    }

    /**
     * O numero da pagina fica sozinho na margem, na primeira linha -- e antes grudava no
     * cabecalho da semana ("...JEREMIAS 13-15 2"), fazendo o capitulo final ser confundido
     * com ele. Aqui ele e reconhecido pelo isolamento e descartado na origem.
     */
    private fun semNumeroDaPagina(words: List<Word>): List<Word> {
        if (words.isEmpty()) return words
        val topo = words.minOf { it.y }
        val primeira = words.filter { it.y - topo <= LINE_TOLERANCE }.sortedBy { it.x0 }
        val numero = primeira.filter { w ->
            w.text.all(Char::isDigit) && primeira.none { it !== w && distancia(it, w) < ISOLATION_GAP }
        }
        return words - numero.toSet()
    }

    private fun distancia(a: Word, b: Word): Float =
        if (a.x0 < b.x0) b.x0 - a.x1 else a.x0 - b.x1

    private fun wordsOf(document: PDDocument, page: Int): List<Word> {
        val words = mutableListOf<Word>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: List<TextPosition>) {
                val limpo = text.trim()
                if (limpo.isEmpty() || textPositions.isEmpty()) return
                words += Word(
                    text = limpo,
                    x0 = textPositions.minOf { it.xDirAdj },
                    x1 = textPositions.maxOf { it.xDirAdj + it.widthDirAdj },
                    y = textPositions[0].yDirAdj,
                    spaceWidth = textPositions[0].widthOfSpace.takeIf { it > 0f } ?: 2.5f,
                )
            }
        }
        stripper.sortByPosition = true
        stripper.startPage = page
        stripper.endPage = page
        stripper.getText(document)
        return words
    }

    /**
     * Procura a calha entre as colunas: o x do meio da pagina atravessado pelo menor numero
     * de palavras. Paginas de coluna unica (as de "Nossa Vida Crista") nao tem calha -- toda
     * posicao central corta muitas palavras --, e ai a pagina e lida de uma vez so.
     */
    private fun layoutPage(words: List<Word>): String {
        val left = words.minOf { it.x0 }
        val right = words.maxOf { it.x1 }
        val candidatos = ((left + (right - left) * 0.35f).toInt()..(left + (right - left) * 0.65f).toInt())
        val calha = candidatos.minByOrNull { x -> words.count { it.x0 < x && it.x1 > x } }
        val cruzam = calha?.let { x -> words.count { it.x0 < x && it.x1 > x } } ?: Int.MAX_VALUE
        if (calha == null || cruzam > MAX_GUTTER_CROSSINGS) return linesOf(words)

        // Uma linha que atravessa a calha (o cabecalho da semana, a arte dos titulos de
        // secao) ocupa a largura toda e fecha a faixa: as duas colunas acumuladas ate ali
        // sao despejadas -- esquerda inteira, depois direita -- e a linha sai intacta.
        val sb = StringBuilder()
        val esquerda = mutableListOf<Word>()
        val direita = mutableListOf<Word>()
        fun despeja() {
            sb.append(linesOf(esquerda)).append(linesOf(direita))
            esquerda.clear()
            direita.clear()
        }
        groupLines(words).forEach { linha ->
            if (temVaoNaCalha(linha, calha)) {
                linha.forEach { if (it.x1 <= calha) esquerda += it else direita += it }
            } else {
                despeja()
                sb.append(joinWords(linha)).append('\n')
            }
        }
        despeja()
        return sb.toString()
    }

    /**
     * A linha so e de duas colunas se houver, exatamente sobre a calha, um vao largo o
     * bastante. Linhas que atravessam a calha com espacamento normal de texto (o cabecalho
     * da semana, o rodape com o cantico final) sao de largura total.
     */
    private fun temVaoNaCalha(linha: List<Word>, calha: Int): Boolean {
        // Palavra pousada em cima da calha (a arte dos titulos e uma so, de ponta a ponta):
        // a linha ocupa a largura toda.
        if (linha.any { it.x0 < calha && it.x1 > calha }) return false
        val esquerda = linha.filter { it.x1 <= calha }
        val direita = linha.filter { it.x0 >= calha }
        if (esquerda.isEmpty() || direita.isEmpty()) return true // uma coluna so
        return direita.minOf { it.x0 } - esquerda.maxOf { it.x1 } >= MIN_GUTTER_GAP
    }

    /** Agrupa as palavras da pagina em linhas, de cima para baixo. */
    private fun groupLines(words: List<Word>): List<List<Word>> {
        val out = mutableListOf<List<Word>>()
        var linha = mutableListOf<Word>()
        words.sortedWith(compareBy({ it.y }, { it.x0 })).forEach { w ->
            if (linha.isNotEmpty() && abs(w.y - linha[0].y) > LINE_TOLERANCE) {
                out += linha
                linha = mutableListOf()
            }
            linha += w
        }
        if (linha.isNotEmpty()) out += linha
        return out
    }

    /** Agrupa palavras em linhas por y e devolve o texto, uma linha por \n. */
    private fun linesOf(words: List<Word>): String =
        groupLines(words).joinToString("") { joinWords(it) + "\n" }

    /**
     * Junta as palavras de uma linha. Um vao muito menor que um espaco nao e separacao de
     * palavras, e sim a fonte tendo desenhado o acento a parte ("Canció" + "n" -> "Canción").
     */
    private fun joinWords(linha: List<Word>): String {
        val sb = StringBuilder()
        var anterior: Word? = null
        linha.forEach { w ->
            val prev = anterior
            if (prev != null && w.x0 - prev.x1 >= GLUE_RATIO * prev.spaceWidth) sb.append(' ')
            sb.append(w.text)
            anterior = w
        }
        return sb.toString()
    }
}
