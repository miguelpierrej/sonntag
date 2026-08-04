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
 * da apostila "Nossa Vida e Ministerio Cristao" / "Vida y Ministerio Cristianos"
 * (mwb_T_AAAAMM.pdf em portugues, mwb_S_AAAAMM.pdf em espanhol).
 *
 * Espera o texto ja separado por coluna e sem os artefatos da fonte -- e o que o
 * MwbTextExtractor entrega. Uma extracao ingenua concatena as duas colunas na mesma
 * linha e este parser nao teria como se defender disso.
 *
 * O idioma e detectado pelo conteudo do PDF, nao pelo idioma do app: um usuario
 * com o app em portugues pode importar a apostila em espanhol e vice-versa.
 *
 * Particularidades tratadas:
 *  - o divisor Tesouros/Ministerio x Vida Crista e o titulo da secao, com o 2o
 *    cantico como reserva; nenhum dos dois e confiavel sozinho, entao a decisao
 *    final e pelo numero da parte -> [vidaStartNumber];
 *  - a duracao aparece como "(5 min)" em portugues e "(5 mins.)" em espanhol;
 *  - titulo longo quebra em duas linhas, levando a duracao junto -> [continuationOf];
 *  - a leitura semanal pode cair na linha seguinte ao cabecalho;
 *  - pagina de apendice repete um cabecalho sem leitura -> descartada.
 */
object MwbParser {

    /** Termos que mudam entre as edicoes da apostila. */
    private data class Vocab(
        val months: Map<String, Int>,
        val songWord: String,
        /** Titulo da secao "Vida Crista", ja sem espacos e em maiusculas. */
        val vidaMarker: String,
        val isJoias: (String) -> Boolean,
        val isBibleReading: (String) -> Boolean,
        val isCongregationStudy: (String) -> Boolean,
    )

    private val PT = Vocab(
        months = linkedMapOf(
            "JANEIRO" to 1, "FEVEREIRO" to 2, "MARÇO" to 3, "MARCO" to 3, "ABRIL" to 4,
            "MAIO" to 5, "JUNHO" to 6, "JULHO" to 7, "AGOSTO" to 8, "SETEMBRO" to 9,
            "OUTUBRO" to 10, "NOVEMBRO" to 11, "DEZEMBRO" to 12,
        ),
        songWord = "CÂNTICO|CANTICO",
        vidaMarker = "VIDACRIST",
        isJoias = { it.contains("joias espirituais") },
        isBibleReading = { it.startsWith("leitura da b") },
        isCongregationStudy = { it.contains("estudo b") && it.contains("congrega") },
    )

    private val ES = Vocab(
        months = linkedMapOf(
            "ENERO" to 1, "FEBRERO" to 2, "MARZO" to 3, "ABRIL" to 4,
            "MAYO" to 5, "JUNIO" to 6, "JULIO" to 7, "AGOSTO" to 8, "SEPTIEMBRE" to 9,
            "OCTUBRE" to 10, "NOVIEMBRE" to 11, "DICIEMBRE" to 12,
        ),
        songWord = "CANCIÓN|CANCION",
        vidaMarker = "VIDACRISTIANA",
        isJoias = { it.contains("perlas escondidas") },
        isBibleReading = { it.startsWith("lectura de la b") },
        isCongregationStudy = { it.contains("estudio b") && it.contains("congrega") },
    )

    private val inlinePartRegex = Regex("(\\d{1,2})\\.\\s+([^()]*?)\\s*\\((\\d+)\\s*mins?\\.?\\)")
    private val lineStartPartRegex = Regex("^(\\d{1,2})\\.\\s+(.*)$")
    private val minutesRegex = Regex("\\((\\d+)\\s*mins?\\.?\\)")

    private data class Header(val diaInicio: Int, val mes: Int, val leituraSemanal: String?)
    private data class ParsedPart(val num: Int, val part: MwbPart, val line: Int)

    fun parse(rawText: String): List<MwbWeek> {
        val year = Regex("20\\d{2}").find(rawText)?.value?.toIntOrNull() ?: return emptyList()
        val vocab = detectVocab(rawText)
        val lines = rawText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        val headerIndices = lines.indices.filter { isWeekHeader(vocab, lines[it]) }
        if (headerIndices.isEmpty()) return emptyList()

        val weeks = mutableListOf<MwbWeek>()
        headerIndices.forEachIndexed { idx, start ->
            val end = headerIndices.getOrNull(idx + 1) ?: lines.size
            val header = parseHeader(vocab, lines[start], lines.getOrNull(start + 1))
                ?: return@forEachIndexed
            weeks += parseWeek(vocab, lines.subList(start + 1, end), year, header)
        }
        // Descarta cabecalhos sem leitura real (ex.: pagina de apendice "24-30 DE AGOSTO 16").
        return weeks.filter { it.leituraSemanal?.any(Char::isLetter) == true }
    }

    /**
     * O espanhol e reconhecido pelos seus proprios marcadores; qualquer outra coisa
     * cai no portugues, que era o unico idioma suportado antes.
     */
    private fun detectVocab(rawText: String): Vocab {
        val head = rawText.take(20_000).uppercase()
        val esMarkers = listOf("CANCIÓN", "CANCION", "TESOROS", "VIDA Y MINISTERIO")
        return if (esMarkers.any { head.contains(it) }) ES else PT
    }

    private fun headerRegex(vocab: Vocab) =
        Regex("(\\d{1,2})(?:-(\\d{1,2}))?DE(${vocab.months.keys.joinToString("|")})")

    private fun collapse(line: String): String =
        line.replace("–", "-").replace("—", "-").replace(Regex("\\s+"), "").uppercase()

    private fun isWeekHeader(vocab: Vocab, line: String): Boolean {
        val m = headerRegex(vocab).find(collapse(line)) ?: return false
        return m.range.first <= 4 // tolera um numero de pagina prefixado
    }

    private fun parseHeader(vocab: Vocab, line: String, nextLine: String?): Header? {
        val m = headerRegex(vocab).find(collapse(line)) ?: return null
        var dia = m.groupValues[1].toIntOrNull() ?: return null
        val fim = m.groupValues[2].toIntOrNull()
        // Numero da pagina pode grudar no dia (ex.: pag. 3 + "9-15" -> "39-15"): inicio > fim.
        if (fim != null && dia > fim) dia %= 10
        val mes = vocab.months[m.groupValues[3]] ?: return null
        val monthOnOriginal = Regex("DE\\s+(${vocab.months.keys.joinToString("|")})")
        val lastMonth = monthOnOriginal.findAll(line.uppercase()).lastOrNull()
        val leitura = lastMonth?.let { readingFromTail(line.substring(it.range.last + 1)) }
        // A leitura pode cair na linha seguinte ("16 -22 DE NOVEMBRO" / "LAMENTACOES 1-2").
        val completa = leitura?.takeIf { it.any(Char::isLetter) }
            ?: nextLine?.takeIf { isReadingLine(vocab, it) }?.let { readingFromTail(it) }
        return Header(dia, mes, completa)
    }

    /**
     * Uma linha e a continuacao da leitura se so tiver o nome do livro e os capitulos:
     * exclui o cantico/comentarios iniciais e qualquer parte numerada.
     */
    private fun isReadingLine(vocab: Vocab, line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty() || !t.any(Char::isDigit) || !t.any(Char::isLetter)) return false
        if (minutesRegex.containsMatchIn(t) || lineStartPartRegex.containsMatchIn(t)) return false
        if (Regex("(?:${vocab.songWord})", RegexOption.IGNORE_CASE).containsMatchIn(collapse(t))) return false
        return t.length <= 40
    }

    /** " JEREMIAS 13 - 15" -> "Jeremias 13-15". */
    private fun readingFromTail(tail: String): String? = prettifyReading(tail.replace(" ", ""))

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

    private fun parseWeek(vocab: Vocab, block: List<String>, year: Int, header: Header): MwbWeek {
        val songRegex = Regex("(?:${vocab.songWord})(\\d+)")
        val songs = block.flatMapIndexed { i, line ->
            songRegex.findAll(collapse(line)).map { i to it.groupValues[1] }.toList()
        }
        // Divisor Ministerio x Vida Crista: o titulo da secao, desenhado com sublinhados
        // entre as letras ("V___I___D___A___C___R___I___S___T___Ã"). O 2o cantico e a
        // reserva, mas falha quando a extracao perde algum cantico.
        val marker = block.indexOfFirst {
            collapse(it).replace("_", "").contains(vocab.vidaMarker)
        }
        val boundary = marker.takeIf { it >= 0 } ?: songs.getOrNull(1)?.first ?: Int.MAX_VALUE

        val parts = collectParts(block)

        val livres = parts.filterNot {
            isFixedTesouros(vocab, it.part.titulo) ||
                vocab.isCongregationStudy(it.part.titulo.lowercase())
        }
        val tesourosTitulo = livres.firstOrNull { it.num == 1 }?.part?.titulo
        // Partes 2 e 3 (joias e leitura da Biblia) sao fixas e ja saem em [livres].
        val numeradas = livres.filter { it.num >= 4 }
        val vidaStart = vidaStartNumber(numeradas, boundary)
        val ministerio = numeradas.filter { it.num < vidaStart }.map { it.num to it.part }
        val vida = numeradas.filter { it.num >= vidaStart }.map { it.num to it.part }

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

    /**
     * As partes sao numeradas em sequencia (ministerio primeiro, depois Vida Crista),
     * entao basta achar o numero em que a Vida Crista comeca. A posicao na pagina nao
     * serve sozinha: com as duas colunas concatenadas, uma parte do ministerio pode
     * cair depois da linha do 2o cantico.
     *
     * Escolhe o corte que mais concorda com a evidencia das linhas -- assim uma parte
     * fora de lugar e vencida pelas demais, em vez de arrastar a classificacao.
     */
    private fun vidaStartNumber(parts: List<ParsedPart>, boundary: Int): Int {
        if (parts.isEmpty()) return Int.MAX_VALUE
        val nums = parts.map { it.num }.distinct().sorted()
        val cortes = nums + (nums.last() + 1) // +1 = nenhuma parte na Vida Crista
        return cortes.maxBy { corte ->
            parts.count { (it.num < corte) == (it.line < boundary) }
        }
    }

    private fun collectParts(block: List<String>): List<ParsedPart> {
        val out = mutableListOf<ParsedPart>()
        block.forEachIndexed { i, line ->
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
                    val titulo = m.groupValues[2]
                    val wrap = block.getOrNull(i + 1)
                        ?.let { continuationOf(titulo, it, block.getOrNull(i + 2)) }
                    out += ParsedPart(num, MwbPart(cleanTitle(wrap?.first ?: titulo), wrap?.second), i)
                }
            }
        }
        return out
    }

    /**
     * Um titulo longo quebra em duas linhas e leva a duracao junto ("1. ...buenos pasto-"
     * / "res! (10 mins.)"). Devolve o titulo completo e os minutos, ou null se a linha
     * seguinte nao for continuacao. Hifen no fim da linha e emenda de silaba, nao texto.
     */
    private fun continuationOf(titulo: String, next: String, afterNext: String?): Pair<String, String?>? {
        val m = minutesRegex.find(next)
        if (m == null) {
            // Titulo quebrado no hifen: emenda mesmo sem a duracao nesta linha, que pode
            // ter ficado na seguinte ("...El Cuerpo Go-" / "bernante..." / "(15 mins.)").
            if (!titulo.endsWith("-")) return null
            if (next.isEmpty() || next.contains(Regex("\\d{1,2}\\.\\s"))) return null
            val minutos = afterNext?.let { minutesRegex.find(it) }?.groupValues?.get(1)
            return (titulo.dropLast(1) + next) to minutos
        }
        // A duracao vem logo depois do titulo; mais para a direita ja e outra coisa.
        if (m.range.first > 60) return null
        val cont = next.substring(0, m.range.first).trim()
        if (cont.contains(Regex("\\d{1,2}\\.\\s"))) return null // ja e a proxima parte
        val full = when {
            cont.isEmpty() -> titulo
            titulo.endsWith("-") -> titulo.dropLast(1) + cont
            else -> "$titulo $cont"
        }
        return full to m.groupValues[1]
    }

    private fun cleanTitle(raw: String): String =
        raw.trim().trim('“', '”', '"', '‘', '’', ' ').trim()

    private fun isFixedTesouros(vocab: Vocab, titulo: String): Boolean {
        val t = titulo.lowercase()
        return vocab.isJoias(t) || vocab.isBibleReading(t)
    }
}
