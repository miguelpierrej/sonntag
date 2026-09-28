package com.example.sonntag.sync

import com.example.sonntag.domain.usecases.EventType
import com.example.sonntag.i18n.Translator
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** Um campo que difere entre as duas versoes de um registro, ja em texto de tela. */
data class FieldDiff(val label: String, val local: String, val remote: String)

/** Um registro divergente pronto para mostrar: o que e, e o que muda nele. */
data class RecordDiff(val title: String, val fields: List<FieldDiff>)

/** Colunas que nao sao conteudo: identidade e carimbo. */
private val TECNICAS = setOf("id", "uuid", "updated_at", "updated_by")

/**
 * Transforma linhas cruas (referencias como uuid) em texto que a pessoa reconhece:
 * "Presidente: Jose Vega" em vez de `presidente_id = 3f2a…`.
 *
 * Sem isso, uma divergencia so dizia a data e a hora de cada versao — e com varias
 * delas ninguem sabia o que estava escolhendo.
 */
class SyncDescriber(
    private val store: SyncStore,
    private val t: Translator,
    /** Linhas que ainda nao estao no banco (vieram no pacote), para achar os nomes delas. */
    extra: Map<String, List<RowValues>> = emptyMap(),
    /**
     * uuid de fora -> uuid daqui, para a mesma reuniao gerada nos dois lados. Sem isso
     * um programa pareceria apontar para outra reuniao so porque o uuid e outro.
     */
    private val aliases: Map<String, String> = emptyMap(),
) {
    /** tabela -> uuid -> linha, das tabelas que outras referenciam. */
    private val porUuid: Map<String, Map<String, RowValues>> =
        listOf("members", "meetings", "cleaning_groups", "preaching_spots", "preaching_groups").associateWith { tabela ->
            val colunas = store.columns(tabela)
            val locais = store.rows(tabela, colunas).mapNotNull { r -> r["uuid"]?.let { it to r } }.toMap()
            locais + extra[tabela].orEmpty().mapNotNull { r -> r["uuid"]?.let { it to r } }
        }

    /** Registro com as diferencas entre [local] e [remote]; so os campos que mudam. */
    fun diff(table: String, local: RowValues?, remoteRaw: RowValues): RecordDiff {
        val remote = remoteRaw.mapValues { (c, v) ->
            if (referencedTable(c) != null && v != null) aliases[v] ?: v else v
        }
        // O titulo e como o registro se chama aqui: e o que a pessoa reconhece. So um
        // registro novo usa o nome que chegou.
        val base = local ?: remote
        val campos = if (local == null) emptyList() else buildList {
            if ((local["deleted"] == "1") != (remote["deleted"] == "1")) {
                add(FieldDiff(t("Situação"), situacao(local), situacao(remote)))
            }
            val colunas = (local.keys + remote.keys).filter { it !in TECNICAS && it != "deleted" }
                .distinct()
                // Coluna que so um lado conhece (versao diferente do app) nao e divergencia.
                .filter { it in local && it in remote }
            colunas.forEach { c ->
                if (local[c].orEmpty() != remote[c].orEmpty()) {
                    add(FieldDiff(label(table, c), value(table, c, local[c]), value(table, c, remote[c])))
                }
            }
        }
        return RecordDiff(title(table, base), campos)
    }

    /** Nome do registro: a pessoa, a reuniao, o grupo… */
    fun title(table: String, row: RowValues): String = when (table) {
        "settings" -> t("Dados da congregação")
        "members" -> pessoa(row)
        "meetings" -> reuniao(row)
        "weekend_programs" -> t("Programa de fim de semana · {0}", ref("meetings", row["meeting_id"]))
        "midweek_programs" -> t("Programa de meio de semana · {0}", ref("meetings", row["meeting_id"]))
        "av_assignments" -> t("Áudio/vídeo · {0}", ref("meetings", row["meeting_id"]))
        "cleaning_groups" -> t("Grupo de limpeza {0}", row["nome"].orEmpty())
        "cleaning_assignments" -> t("Limpeza · semana {0} de {1}", row["semana_iso"].orEmpty(), row["ano"].orEmpty())
        "events" -> "${row["nome"].orEmpty()} · ${data(row["data"])}"
        "preaching_spots", "preaching_groups" -> row["nome"].orEmpty()
        "preaching_group_members" ->
            "${ref("members", row["member_id"])} · ${ref("preaching_groups", row["preaching_group_id"])}"
        "preaching_templates" -> "${tipo(row["tipo"])} · ${diaSemana(row["dia_semana"])} ${row["hora_inicio"].orEmpty()}"
        "preaching_shifts" -> "${tipo(row["tipo"])} · ${data(row["data"])} ${row["hora_inicio"].orEmpty()}"
        "preaching_notes" -> "${tipo(row["tipo"])} · ${t.monthYearLabel(row["mes"]?.toIntOrNull() ?: 1, row["ano"]?.toIntOrNull() ?: 0)}"
        else -> row["uuid"]?.take(8).orEmpty()
    }.trim().ifEmpty { t("(sem nome)") }

    private fun value(table: String, column: String, raw: String?): String {
        if (raw.isNullOrEmpty()) return t("(vazio)")
        referencedTable(column)?.let { return ref(it, raw) }
        return when {
            column in BOOLEANAS -> if (raw == "1") t("Sim") else t("Não")
            column == "data" -> data(raw)
            column == "tipo" -> tipo(raw)
            column == "dia_semana" -> diaSemana(raw)
            column == "mes" -> t.monthNameCapitalized(raw.toIntOrNull() ?: 0)
            table == "cleaning_assignments" && column == "semana_iso" -> t("semana {0}", raw)
            else -> raw
        }
    }

    private fun ref(table: String, uuid: String?): String {
        if (uuid.isNullOrEmpty()) return t("(vazio)")
        val linhas = porUuid[table]
        val row = linhas?.get(uuid) ?: aliases[uuid]?.let { linhas?.get(it) } ?: return t("(registro desconhecido)")
        return when (table) {
            "members" -> pessoa(row)
            "meetings" -> reuniao(row)
            else -> row["nome"].orEmpty()
        }
    }

    private fun pessoa(row: RowValues) = "${row["nome"].orEmpty()} ${row["sobrenome"].orEmpty()}".trim()

    private fun reuniao(row: RowValues) = "${data(row["data"])} (${tipo(row["tipo"])})"

    private fun situacao(row: RowValues) = if (row["deleted"] == "1") t("Excluído") else t("Ativo")

    private fun data(raw: String?): String =
        raw?.let { runCatching { t.longDateWithYear(LocalDate.parse(it)) }.getOrNull() ?: it }.orEmpty()

    private fun diaSemana(raw: String?): String =
        raw?.toIntOrNull()?.takeIf { it in 1..7 }?.let { t.dayName(DayOfWeek(it)) } ?: raw.orEmpty()

    private fun tipo(raw: String?): String = when (raw) {
        "WEEKDAY" -> t("Meio de semana")
        "WEEKEND" -> t("Fim de semana")
        "CARRITO" -> t("Carrinho")
        "PREDICACION" -> t("Pregação")
        "AMBOS" -> t("Carrinho e pregação")
        null -> ""
        else -> EventType.entries.firstOrNull { it.id == raw }?.let { t(it.label) } ?: raw
    }

    private fun label(table: String, column: String): String =
        (ROTULOS["$table.$column"] ?: ROTULOS[column])?.let { t(it) }
            ?: column.removeSuffix("_id").replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private val BOOLEANAS = setOf("anciao", "servo_ministerial", "pioneiro")

/**
 * Nome de cada coluna na tela (chave de traducao em portugues). `tabela.coluna` vence
 * `coluna` quando o mesmo nome significa coisas diferentes em tabelas diferentes.
 */
private val ROTULOS = mapOf(
    "nome" to "Nome",
    "sobrenome" to "Sobrenome",
    "endereco" to "Endereço",
    "telefone" to "Telefone",
    "anciao" to "Ancião",
    "servo_ministerial" to "Servo ministerial",
    "pioneiro" to "Pioneiro",
    "data" to "Data",
    "hora" to "Horário",
    "tipo" to "Tipo",
    "meeting_id" to "Reunião",
    "titulo_discurso" to "Tema do discurso",
    "orador_id" to "Orador",
    "orador_nome" to "Orador visitante",
    "presidente_id" to "Presidente",
    "dirigente_id" to "Dirigente",
    "leitor_id" to "Leitor",
    "leitura_semanal" to "Leitura da semana",
    "conselheiro_id" to "Conselheiro",
    "cantico_inicial" to "Cântico inicial",
    "cantico_meio" to "Cântico do meio",
    "cantico_final" to "Cântico final",
    "oracao_inicial_id" to "Oração inicial",
    "oracao_final_id" to "Oração final",
    "tesouros_titulo" to "Tesouros: tema",
    "tesouros_orador_id" to "Tesouros: orador",
    "joias_id" to "Joias espirituais",
    "leitura_biblia_id" to "Leitura da Bíblia",
    "min1_titulo" to "Ministério 1: tema", "min1_minutos" to "Ministério 1: minutos",
    "min1_estudante_id" to "Ministério 1: estudante", "min1_ajudante_id" to "Ministério 1: ajudante",
    "min2_titulo" to "Ministério 2: tema", "min2_minutos" to "Ministério 2: minutos",
    "min2_estudante_id" to "Ministério 2: estudante", "min2_ajudante_id" to "Ministério 2: ajudante",
    "min3_titulo" to "Ministério 3: tema", "min3_minutos" to "Ministério 3: minutos",
    "min3_estudante_id" to "Ministério 3: estudante", "min3_ajudante_id" to "Ministério 3: ajudante",
    "min4_titulo" to "Ministério 4: tema", "min4_minutos" to "Ministério 4: minutos",
    "min4_estudante_id" to "Ministério 4: estudante", "min4_ajudante_id" to "Ministério 4: ajudante",
    "vida1_titulo" to "Vida cristã 1: tema", "vida1_minutos" to "Vida cristã 1: minutos",
    "vida1_id" to "Vida cristã 1: designado",
    "vida2_titulo" to "Vida cristã 2: tema", "vida2_minutos" to "Vida cristã 2: minutos",
    "vida2_id" to "Vida cristã 2: designado",
    "estudo_dirigente_id" to "Estudo bíblico: dirigente",
    "estudo_leitor_id" to "Estudo bíblico: leitor",
    "audio_id" to "Áudio",
    "video_id" to "Vídeo",
    "plataforma1_id" to "Plataforma 1",
    "plataforma2_id" to "Plataforma 2",
    "microfone1_id" to "Microfone 1",
    "microfone2_id" to "Microfone 2",
    "acomodador1_id" to "Acomodador 1",
    "acomodador2_id" to "Acomodador 2",
    "semana_iso" to "Semana",
    "ano" to "Ano",
    "mes" to "Mês",
    "group_id" to "Grupo",
    "auxiliar_id" to "Auxiliar",
    "spot_id" to "Local",
    "ordem" to "Ordem",
    "preaching_group_id" to "Grupo",
    "member_id" to "Publicador",
    "dia_semana" to "Dia da semana",
    "hora_inicio" to "Início",
    "hora_fim" to "Fim",
    "nota" to "Observação",
    "texto" to "Texto",
    "designado1_id" to "Designado 1",
    "designado2_id" to "Designado 2",
    "designado3_id" to "Designado 3",
    "designado4_id" to "Designado 4",
)
