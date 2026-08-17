package com.example.sonntag.sync

import com.example.sonntag.data.repos.SettingsRepository

/**
 * Colunas terminadas em `_id` apontam para outra tabela. Como o id e local, elas
 * viajam como uuid e sao traduzidas de volta na importacao.
 *
 * A convencao cobre o schema inteiro: `meeting_id` -> meetings, `group_id` ->
 * cleaning_groups, qualquer outro `_id` -> members. Uma coluna futura que fuja
 * disso precisa entrar aqui.
 */
internal fun referencedTable(column: String): String? = when {
    !column.endsWith("_id") -> null
    column == "meeting_id" -> "meetings"
    column == "group_id" -> "cleaning_groups"
    column == "spot_id" -> "preaching_spots"
    else -> "members"
}

/** Tabelas para as quais outras apontam; podem ser arrastadas num envio incremental. */
private val REFERENCIADAS = setOf("meetings", "members", "cleaning_groups", "preaching_spots")

/** Colunas que nunca viajam: o id e local e o uuid identifica a linha. */
private const val LOCAL_ID = "id"

/**
 * Colunas que identificam a linha quando o uuid ainda nao coincide entre duas
 * instalacoes.
 *
 * Reunioes, dias e semanas de limpeza sao **derivados**: cada instalacao gera os
 * seus a partir da mesma configuracao, com uuids diferentes. Sem esta chave, a
 * importacao acha que sao linhas novas e duplica a agenda inteira — uma copia com
 * programa e outra vazia.
 *
 * `members` fica de fora de proposito: dois homonimos sao pessoas diferentes.
 */
internal fun naturalKey(table: String): List<String>? = when (table) {
    // A hora nao entra na chave: ela e o que o usuario edita. Se entrasse, mudar o
    // horario num aparelho criaria uma segunda reuniao no outro em vez de corrigir a
    // que ja existe. O proprio app identifica reuniao por data|tipo e dia por
    // dia_semana (ver regenerateFutureMeetings).
    "meetings" -> listOf("data", "tipo")
    "meeting_days" -> listOf("dia_semana")
    "cleaning_groups" -> listOf("nome")
    "cleaning_assignments" -> listOf("semana_iso", "ano")
    "weekend_programs", "midweek_programs", "av_assignments" -> listOf("meeting_id")
    // Pontos e grupos sao reconhecidos pelo nome; turnos e padrao, pelo lugar que
    // ocupam no calendario — os dois aparelhos podem ter gerado o mesmo mes.
    "preaching_spots", "preaching_groups" -> listOf("nome")
    "preaching_shifts" -> listOf("tipo", "data", "hora_inicio")
    "preaching_templates" -> listOf("tipo", "dia_semana", "hora_inicio")
    "preaching_notes" -> listOf("tipo", "ano", "mes")
    "settings" -> emptyList() // singleton: a linha e sempre a mesma
    else -> null
}

/**
 * Como a mudanca aparece na revisao. Nasce de [ChangeKind], mas separa o que **apaga**
 * do que so atualiza: era a exclusao, disfarcada de atualizacao, que sumia com
 * reunioes sem o usuario perceber.
 */
enum class ImportCategory { NOVOS, ATUALIZACOES, EXCLUSOES, DIVERGENCIAS }

/** Categoria de uma linha que chegou. Null quando nada ha a fazer com ela. */
fun IncomingRow.category(): ImportCategory? = when {
    kind == ChangeKind.IGNORADO || kind == ChangeKind.IGUAL -> null
    values["deleted"] == "1" -> ImportCategory.EXCLUSOES
    kind == ChangeKind.NOVO -> ImportCategory.NOVOS
    kind == ChangeKind.ATUALIZA -> ImportCategory.ATUALIZACOES
    else -> ImportCategory.DIVERGENCIAS
}

/** O que a importacao fara com uma linha. */
enum class ChangeKind {
    NOVO,
    ATUALIZA,
    DIVERGE,
    IGUAL,

    /**
     * Linha inaproveitavel aqui: uma referencia obrigatoria dela aponta para algo
     * que esta instalacao nao tem (o bloco correspondente nao foi exportado, ou a
     * origem ja tinha a referencia quebrada).
     */
    IGNORADO,
}

/**
 * Uma linha do arquivo confrontada com o que existe aqui. [kind] `DIVERGE` significa
 * que os dois lados tem versoes diferentes e quem decide e o usuario.
 */
data class IncomingRow(
    val table: String,
    val uuid: String,
    /**
     * Preenchido quando a linha foi reconhecida pela chave natural, e nao pelo uuid.
     * A gravacao entao atualiza essa linha (adotando o uuid que chegou) em vez de
     * inserir outra.
     */
    val localUuid: String? = null,
    val kind: ChangeKind,
    val description: String,
    val localUpdatedAt: String?,
    val remoteUpdatedAt: String?,
    val values: RowValues,
)

data class ImportPreview(
    val header: PackageHeader,
    val rows: List<IncomingRow>,
    /**
     * uuid que chegou -> uuid daqui, para toda linha reconhecida pela chave natural.
     *
     * Inclui as linhas identicas, que ficam fora de [rows]: mesmo sem nada a gravar,
     * elas dizem a que reuniao daqui um programa que chegou se refere.
     */
    val aliases: Map<String, String> = emptyMap(),
) {
    val novos: Int get() = rows.count { it.kind == ChangeKind.NOVO }
    val atualizacoes: Int get() = rows.count { it.kind == ChangeKind.ATUALIZA }
    val divergencias: List<IncomingRow> get() = rows.filter { it.kind == ChangeKind.DIVERGE }
    val ignorados: Int get() = rows.count { it.kind == ChangeKind.IGNORADO }

    /** Uuids que ja vem marcados: o que so acrescenta, e nunca destroi. */
    val aceitasPorPadrao: Set<String>
        get() = rows.filter { it.category() == ImportCategory.NOVOS }.map { it.uuid }.toSet()
}

class SyncService(
    private val store: SyncStore,
    private val stamp: SyncStamp,
    private val crypto: SyncCrypto,
    private val settingsRepository: SettingsRepository,
) {

    // ─── Exportacao ──────────────────────────────────────────────────────────

    /**
     * Monta o pacote cifrado com as secoes escolhidas. Senha nula = sem protecao real.
     *
     * Com [since], envia so o que mudou depois daquele instante — o caso comum entre
     * dois aparelhos que ja sincronizaram e tem uma unica alteracao a passar.
     */
    fun buildPackage(
        sections: List<SyncSection>,
        password: String?,
        since: String? = null,
        skipTables: Set<String> = emptySet(),
    ): ByteArray {
        val tabelas = sections.flatMap { it.tables }.distinct() - skipTables

        // Tudo lido primeiro: o recorte precisa poder buscar as linhas referenciadas
        // que nao mudaram, mas sem as quais as que mudaram nao fazem sentido.
        val completo = tabelas.associateWith { table ->
            val columns = store.columns(table).filter { it != LOCAL_ID }
            val referenceMaps = columns.mapNotNull { column ->
                referencedTable(column)?.let { column to store.localIdToUuid(it) }
            }.toMap()
            columns to store.rows(table, columns).map { row ->
                row.mapValues { (column, value) -> toUuid(referenceMaps, column, value) }
            }
        }

        val tables = tabelas.map { table ->
            val (columns, todas) = completo.getValue(table)
            PackageTable(table, columns, if (since == null) todas else recorte(table, todas, completo, since))
        }

        val salt = crypto.randomBytes(SALT_SIZE)
        val iv = crypto.randomBytes(IV_SIZE)
        val header = PackageHeader(
            version = PACKAGE_VERSION,
            protected = password != null,
            exportedAt = stamp.now(),
            exportedBy = stamp.deviceId,
            congregacao = settingsRepository.getSettingsOnce()?.nome,
            sections = sections.map { it.id },
            salt = salt,
            iv = iv,
        )
        val cipher = crypto.encrypt(
            encodePayload(tables).encodeToByteArray(),
            password ?: BUILT_IN_PASSPHRASE,
            salt,
            iv,
        )
        return frame(encodeHeader(header), cipher)
    }

    /**
     * Linhas alteradas depois de [since], mais aquelas a que elas se referem.
     *
     * Sem esse arrasto, um programa alterado chegaria sozinho e o outro lado nao teria
     * a reuniao correspondente para ligar — o mesmo NOT NULL que ja nos mordeu.
     */
    private fun recorte(
        table: String,
        todas: List<RowValues>,
        completo: Map<String, Pair<List<String>, List<RowValues>>>,
        since: String,
    ): List<RowValues> {
        val alteradas = todas.filter { (it["updated_at"] ?: "") > since }
        if (table !in REFERENCIADAS) return alteradas

        // Quais uuids desta tabela alguem que mudou precisa?
        val necessarios = completo.entries.flatMap { (outra, dados) ->
            val (colunas, linhas) = dados
            val referentes = colunas.filter { referencedTable(it) == table }
            if (referentes.isEmpty()) emptyList()
            else linhas.filter { (it["updated_at"] ?: "") > since }
                .flatMap { linha -> referentes.mapNotNull { linha[it] } }
        }.toSet()

        val jaIncluidos = alteradas.mapNotNull { it["uuid"] }.toSet()
        val arrastadas = todas.filter { it["uuid"] in necessarios && it["uuid"] !in jaIncluidos }
        return alteradas + arrastadas
    }

    // ─── Importacao ──────────────────────────────────────────────────────────

    /** Le so o cabecalho: diz o que ha no arquivo e se pede senha. */
    fun readHeader(bytes: ByteArray): PackageHeader? = runCatching {
        decodeHeader(unframeHeader(bytes))
    }.getOrNull()

    /**
     * Confronta o arquivo com o banco local sem gravar nada. Devolve null quando a
     * senha esta errada ou o arquivo foi adulterado.
     */
    fun preview(bytes: ByteArray, password: String?): ImportPreview? {
        val header = readHeader(bytes) ?: return null
        val plain = crypto.decrypt(
            unframePayload(bytes),
            password ?: BUILT_IN_PASSPHRASE,
            header.salt,
            header.iv,
        ) ?: return null

        val tables = decodePayload(plain.decodeToString())

        // Uma referencia e resolvivel se a linha apontada ja existe aqui **ou** vem
        // no proprio arquivo — as reunioes chegam junto com os programas.
        val chegando = tables.associate { table ->
            table.name to table.rows.mapNotNull { it["uuid"] }.toSet()
        }
        val disponiveis = mutableMapOf<String, Set<String>>()
        fun uuidsDisponiveis(table: String): Set<String> = disponiveis.getOrPut(table) {
            store.uuidToLocalId(table).keys + chegando[table].orEmpty()
        }

        // Preenchido tabela a tabela; as reunioes chegam antes dos programas, entao
        // quando um programa e avaliado o apelido da sua reuniao ja existe.
        val apelidos = mutableMapOf<String, String>()

        val rows = tables.flatMap { table ->
            val localByUuid = store.uuidToLocalId(table.name)
            val obrigatorias = store.notNullColumns(table.name)
            val referencias = table.columns.mapNotNull { column ->
                referencedTable(column)?.let { column to uuidsDisponiveis(it) }
            }.toMap()
            // A linha daqui guarda ids; a do arquivo guarda uuids. Sem traduzir, toda
            // linha com referencia pareceria diferente a cada importacao.
            val paraUuid = table.columns.mapNotNull { column ->
                referencedTable(column)?.let { column to store.localIdToUuid(it) }
            }.toMap()
            // Indice pela chave natural, com as referencias convertidas para uuid,
            // para comparar na mesma moeda do arquivo.
            val chave = naturalKey(table.name)
            val porChaveNatural: Map<String, String> = if (chave == null) emptyMap() else {
                store.rows(table.name, table.columns).mapNotNull { linha ->
                    val u = linha["uuid"] ?: return@mapNotNull null
                    chaveDe(linha, chave, paraUuid)?.let { it to u }
                }.toMap()
            }

            table.rows.mapNotNull { row ->
                val uuid = row["uuid"] ?: return@mapNotNull null
                // Sem par por uuid, tenta a chave natural: a mesma reuniao gerada dos
                // dois lados nasce com uuids diferentes.
                val uuidLocal = if (localByUuid.containsKey(uuid)) null
                    else chave?.let { chaveDe(row, it, emptyMap()) }?.let { porChaveNatural[it] }
                val uuidBusca = uuidLocal ?: uuid
                val local = (localByUuid[uuidBusca] ?: uuidLocal?.let { 1L })
                    ?.let { store.findByUuid(table.name, uuidBusca, table.columns) }
                // Referencia obrigatoria sem destino torna a linha inaproveitavel:
                // inseri-la violaria o NOT NULL.
                val referenciaQuebrada = referencias.any { (column, disponiveis) ->
                    column in obrigatorias && row[column].let { it == null || it !in disponiveis }
                }
                val excluidaChegando = row["deleted"] == "1"
                val kind = when {
                    referenciaQuebrada -> ChangeKind.IGNORADO
                    // Exclusao que casou pela chave natural, e nao pelo uuid: do outro
                    // lado ela e a duplicata que morreu; aqui ela e a linha que ficou
                    // viva. Aplicar apagaria justamente a sobrevivente — com o
                    // programa preenchido dentro dela.
                    excluidaChegando && uuidLocal != null -> ChangeKind.IGNORADO
                    local == null -> ChangeKind.NOVO
                    // Quando o par veio da chave natural, os uuids sao diferentes por
                    // definicao — comparar por eles transformaria toda a agenda em
                    // divergencia. O que importa e o resto do conteudo.
                    sameContent(
                        local.mapValues { (c, v) -> toUuid(paraUuid, c, v) },
                        // A referencia pode apontar para uma linha que aqui tem outro
                        // uuid (mesma reuniao gerada dos dois lados): sem traduzir, o
                        // programa pareceria diferente a cada importacao.
                        row.mapValues { (c, v) ->
                            if (referencedTable(c) != null && v != null) apelidos[v] ?: v else v
                        },
                        if (uuidLocal != null) table.columns - "uuid" else table.columns,
                    ) -> ChangeKind.IGUAL
                    // Nada aqui prova quem viu o que: se os dois lados mudaram, a
                    // decisao e do usuario. So o carimbo mais novo sugere o padrao.
                    else -> if (local["updated_by"] == row["updated_by"]) ChangeKind.ATUALIZA
                    else ChangeKind.DIVERGE
                }
                uuidLocal?.let { apelidos[uuid] = it }
                IncomingRow(
                    table = table.name,
                    uuid = uuid,
                    localUuid = uuidLocal,
                    kind = kind,
                    description = describe(table.name, row),
                    localUpdatedAt = local?.get("updated_at"),
                    remoteUpdatedAt = row["updated_at"],
                    values = row,
                )
            }
        }
        return ImportPreview(
            header = header,
            rows = rows.filter { it.kind != ChangeKind.IGUAL },
            aliases = apelidos.toMap(),
        )
    }

    /**
     * Grava o que foi aceito, traduzindo as referencias para os ids locais.
     *
     * Recebe o preview inteiro, e nao so as linhas escolhidas, porque uma linha que o
     * usuario decidiu **nao** aplicar ainda assim diz a que linha local o uuid que
     * chegou corresponde — sem isso, um programa nao acha a sua reuniao.
     */
    fun apply(preview: ImportPreview, aceitas: Set<String>): Int {
        val apelido = preview.aliases

        // Grava exatamente o que foi aceito. Antes, novos e atualizacoes entravam
        // sozinhos e so a divergencia era escolhida — o que sobrescrevia sem pedir.
        val aGravar = preview.rows.filter {
            it.kind != ChangeKind.IGNORADO && it.uuid in aceitas
        }

        var aplicadas = 0
        aGravar.groupBy { it.table }.forEach { (table, tableRows) ->
            val columns = store.columns(table).filter { it != LOCAL_ID }
            val obrigatorias = store.notNullColumns(table)
            val referenceMaps = columns.mapNotNull { column ->
                referencedTable(column)?.let { column to store.uuidToLocalId(it) }
            }.toMap()

            tableRows.forEach { incoming ->
                val values = columns.associateWith { column ->
                    val raw = incoming.values[column]
                    if (referenceMaps.containsKey(column)) {
                        // A referencia chegou como uuid; pode apontar para uma linha que
                        // aqui tem outro uuid (mesma reuniao, gerada dos dois lados).
                        raw?.let { referenceMaps.getValue(column)[apelido[it] ?: it]?.toString() }
                    } else {
                        raw
                    }
                }
                // Referencia obrigatoria sem destino: pular em vez de estourar.
                if (obrigatorias.any { it in referenceMaps && values[it] == null }) return@forEach

                val alvo = incoming.localUuid ?: incoming.uuid
                if (store.uuidToLocalId(table).containsKey(alvo)) {
                    store.updateByUuid(table, alvo, values, allowUuidChange = incoming.localUuid != null)
                } else {
                    store.insert(table, values)
                }
                aplicadas++
            }
        }
        return aplicadas
    }

    /** Chave natural em texto. Referencias entram ja convertidas para uuid. */
    private fun chaveDe(
        row: RowValues,
        colunas: List<String>,
        paraUuid: Map<String, Map<Long, String>>,
    ): String? {
        if (colunas.isEmpty()) return "singleton"
        val partes = colunas.map { coluna ->
            val bruto = row[coluna]
            if (paraUuid.containsKey(coluna)) toUuid(paraUuid, coluna, bruto) else bruto
        }
        // Chave incompleta nao identifica nada — melhor tratar como linha nova.
        if (partes.any { it == null }) return null
        return partes.joinToString("\u0000")
    }

    /** Converte o id local de uma referencia no uuid correspondente. */
    private fun toUuid(maps: Map<String, Map<Long, String>>, column: String, value: String?): String? =
        if (maps.containsKey(column)) value?.toLongOrNull()?.let { maps.getValue(column)[it] } else value

    private fun sameContent(local: RowValues, remote: RowValues, columns: List<String>): Boolean =
        columns.filter { it != "updated_at" && it != "updated_by" }
            .all { local[it] == remote[it] }

    /** Texto curto para a lista de divergencias. */
    private fun describe(table: String, row: RowValues): String = when (table) {
        "members" -> "${row["nome"].orEmpty()} ${row["sobrenome"].orEmpty()}".trim()
        "meetings" -> "${row["data"].orEmpty()} ${row["hora"].orEmpty()}".trim()
        "cleaning_groups" -> row["nome"].orEmpty()
        "settings" -> row["nome"].orEmpty()
        "meeting_days" -> "${row["dia_semana"].orEmpty()} ${row["hora"].orEmpty()}".trim()
        "weekend_programs" -> row["titulo_discurso"].orEmpty()
        "midweek_programs" -> row["leitura_semanal"].orEmpty()
        "cleaning_assignments" -> "${row["ano"].orEmpty()}/${row["semana_iso"].orEmpty()}"
        "preaching_spots", "preaching_groups" -> row["nome"].orEmpty()
        "preaching_shifts" -> "${row["data"].orEmpty()} ${row["hora_inicio"].orEmpty()}".trim()
        "preaching_notes" -> "${row["ano"].orEmpty()}/${row["mes"].orEmpty()}"
        else -> ""
    }.ifBlank { row["uuid"]?.take(8).orEmpty() }
}
