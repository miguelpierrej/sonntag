package com.example.sonntag.domain.usecases

/** Uma parte numerada da programacao (titulo + duracao em minutos). */
data class MwbPart(val titulo: String, val minutos: String?)

/** Programacao de uma semana extraida da apostila (mwb). */
data class MwbWeek(
    val ano: Int,
    val mes: Int,
    val diaInicio: Int,
    val leituraSemanal: String?,
    val canticos: List<String>,
    val tesourosTitulo: String?,
    val ministerio: List<MwbPart>,
    val vida: List<MwbPart>,
)

/**
 * Extrai a programacao das reunioes de meio de semana a partir do texto puro
 * da apostila "Nossa Vida e Ministerio Cristao" (mwb_T_AAAAMM.pdf), extraido
 * com PDFBox usando sortByPosition = true (ordem visual, de cima para baixo).
 *
 * Particularidades tratadas:
 *  - as duas colunas ficam concatenadas na mesma linha, entao uma parte pode
 *    aparecer no meio da linha -> busca partes em qualquer posicao;
 *  - o divisor Tesouros/Ministerio x Vida Crista e o 2o cantico (que introduz
 *    "Nossa Vida Crista"), mais confiavel que o marcador de secao;
 *  - espacos espurios apos letras acentuadas ("obedie ncia") -> [fixAccents];
 *  - numero da pagina prefixado/anexado no cabecalho -> ignorado;
 *  - pagina de apendice repete um cabecalho sem leitura -> descartada.
 */
object MwbParser {

    private val MONTHS = linkedMapOf(
        "JANEIRO" to 1, "FEVEREIRO" to 2, "MARÇO" to 3, "MARCO" to 3, "ABRIL" to 4,
        "MAIO" to 5, "JUNHO" to 6, "JULHO" to 7, "AGOSTO" to 8, "SETEMBRO" to 9,
        "OUTUBRO" to 10, "NOVEMBRO" to 11, "DEZEMBRO" to 12,
    )
    private val monthsAlt = MONTHS.keys.joinToString("|")

    private val headerRegex = Regex("(\\d{1,2})(?:-(\\d{1,2}))?DE($monthsAlt)")
    private val monthOnOriginalRegex = Regex("DE\\s+($monthsAlt)")
    private val inlinePartRegex = Regex("(\\d{1,2})\\.\\s+([^()]*?)\\s*\\((\\d+)\\s*min\\)")
    private val lineStartPartRegex = Regex("^(\\d{1,2})\\.\\s+(.*)$")
    private val minutesRegex = Regex("\\((\\d+)\\s*min\\)")
    private val standaloneMinutesRegex = Regex("^\\(\\d+\\s*min\\)")
    private val songRegex = Regex("CÂNTICO(\\d+)")

    // Letras acentuadas que a fonte separa com um espaco espurio. Inclui apenas as
    // que raramente terminam uma palavra em portugues (evita juntar palavras reais
    // como "voce diria" ou "Jeova merece"); acentos agudos e "e" circunflexo ficam de fora.
    private val accentFixRegex = Regex("([âôûãõçÂÔÛÃÕÇ]) (?=\\p{Ll})")

    private data class Header(val diaInicio: Int, val mes: Int, val leituraSemanal: String?)
    private data class ParsedPart(val num: Int, val part: MwbPart, val line: Int)

    fun parse(rawText: String): List<MwbWeek> {
        val year = Regex("20\\d{2}").find(rawText)?.value?.toIntOrNull() ?: return emptyList()
        val lines = rawText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        val headerIndices = lines.indices.filter { isWeekHeader(lines[it]) }
        if (headerIndices.isEmpty()) return emptyList()

        val weeks = mutableListOf<MwbWeek>()
        headerIndices.forEachIndexed { idx, start ->
            val end = headerIndices.getOrNull(idx + 1) ?: lines.size
            val header = parseHeader(lines[start]) ?: return@forEachIndexed
            weeks += parseWeek(lines.subList(start + 1, end), year, header)
        }
        // Descarta cabecalhos sem leitura real (ex.: pagina de apendice "24-30 DE AGOSTO 16").
        return weeks.filter { it.leituraSemanal?.any(Char::isLetter) == true }
    }

    private fun collapse(line: String): String =
        line.replace("–", "-").replace("—", "-").replace(Regex("\\s+"), "").uppercase()

    private fun isWeekHeader(line: String): Boolean {
        val m = headerRegex.find(collapse(line)) ?: return false
        return m.range.first <= 4 // tolera um numero de pagina prefixado
    }

    private fun parseHeader(line: String): Header? {
        val m = headerRegex.find(collapse(line)) ?: return null
        var dia = m.groupValues[1].toIntOrNull() ?: return null
        val fim = m.groupValues[2].toIntOrNull()
        // Numero da pagina pode grudar no dia (ex.: pag. 3 + "9-15" -> "39-15"): inicio > fim.
        if (fim != null && dia > fim) dia %= 10
        val mes = MONTHS[m.groupValues[3]] ?: return null
        val lastMonth = monthOnOriginalRegex.findAll(line.uppercase()).lastOrNull()
        val leitura = lastMonth?.let { readingFromTail(line.substring(it.range.last + 1)) }
        return Header(dia, mes, leitura)
    }

    /** " JEREMIAS 13 - 15 2" -> "Jeremias 13-15" (remove numero de pagina final). */
    private fun readingFromTail(tail: String): String? {
        val tokens = tail.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        if (tokens.size >= 2) {
            val last = tokens.last()
            val prev = tokens[tokens.size - 2]
            if (last.all(Char::isDigit) && prev != "-") tokens.removeAt(tokens.size - 1)
        }
        return prettifyReading(tokens.joinToString(""))
    }

    /** "JEREMIAS13-15" -> "Jeremias 13-15"; "1SAMUEL15-16" -> "1 Samuel 15-16". */
    private fun prettifyReading(raw: String): String? {
        if (raw.isBlank()) return null
        val tokens = Regex("[A-ZÀ-Ÿ]+|\\d+(?:[-:,]\\d+)*").findAll(raw).map { it.value }.toList()
        if (tokens.none { it.first().isLetter() }) return null
        return tokens.joinToString(" ") { token ->
            if (token.first().isDigit()) token
            else token.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    private fun parseWeek(block: List<String>, year: Int, header: Header): MwbWeek {
        // Canticos e o divisor Ministerio/Vida (linha do 2o cantico).
        val songs = block.flatMapIndexed { i, line ->
            songRegex.findAll(collapse(line)).map { i to it.groupValues[1] }.toList()
        }
        val boundary = songs.getOrNull(1)?.first ?: Int.MAX_VALUE

        val parts = collectParts(block)

        var tesourosTitulo: String? = null
        val ministerio = mutableListOf<Pair<Int, MwbPart>>()
        val vida = mutableListOf<Pair<Int, MwbPart>>()
        parts.forEach { p ->
            when {
                isFixedTesouros(p.part.titulo) || isCongregationStudy(p.part.titulo) -> Unit
                p.line < boundary -> when {
                    p.num == 1 -> if (tesourosTitulo == null) tesourosTitulo = p.part.titulo
                    p.num >= 4 -> ministerio += p.num to p.part
                }
                else -> vida += p.num to p.part
            }
        }

        return MwbWeek(
            ano = year,
            mes = header.mes,
            diaInicio = header.diaInicio,
            leituraSemanal = header.leituraSemanal,
            canticos = songs.map { it.second },
            tesourosTitulo = tesourosTitulo,
            ministerio = ministerio.sortedBy { it.first }.map { it.second },
            vida = vida.sortedBy { it.first }.map { it.second },
        )
    }

    private fun collectParts(block: List<String>): List<ParsedPart> {
        val out = mutableListOf<ParsedPart>()
        block.forEachIndexed { i, raw ->
            val line = fixAccents(raw)
            val inline = inlinePartRegex.findAll(line).toList()
            inline.forEach { m ->
                out += ParsedPart(
                    num = m.groupValues[1].toInt(),
                    part = MwbPart(cleanTitle(m.groupValues[2]), m.groupValues[3]),
                    line = i,
                )
            }
            // Parte no inicio da linha sem "(min)" inline (titulo que quebra em duas linhas).
            lineStartPartRegex.find(line)?.let { m ->
                val num = m.groupValues[1].toInt()
                if (inline.none { it.groupValues[1].toInt() == num }) {
                    val minutos = block.getOrNull(i + 1)
                        ?.let { fixAccents(it) }
                        ?.takeIf { standaloneMinutesRegex.containsMatchIn(it) }
                        ?.let { minutesRegex.find(it)?.groupValues?.get(1) }
                    out += ParsedPart(num, MwbPart(cleanTitle(m.groupValues[2]), minutos), i)
                }
            }
        }
        return out
    }

    private fun fixAccents(s: String): String = accentFixRegex.replace(s) { it.groupValues[1] }

    private fun cleanTitle(raw: String): String =
        raw.trim().trim('“', '”', '"', '‘', '’', ' ').trim()

    private fun isFixedTesouros(titulo: String): Boolean {
        val t = titulo.lowercase()
        return t.contains("joias espirituais") || t.startsWith("leitura da b")
    }

    private fun isCongregationStudy(titulo: String): Boolean {
        val t = titulo.lowercase()
        return t.contains("estudo b") && t.contains("congrega")
    }
}
