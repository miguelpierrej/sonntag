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
    else -> "members"
}

/** Colunas que nunca viajam: o id e local e o uuid identifica a linha. */
private const val LOCAL_ID = "id"

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
    val kind: ChangeKind,
    val description: String,
    val localUpdatedAt: String?,
    val remoteUpdatedAt: String?,
    val values: RowValues,
)

data class ImportPreview(
    val header: PackageHeader,
    val rows: List<IncomingRow>,
) {
    val novos: Int get() = rows.count { it.kind == ChangeKind.NOVO }
    val atualizacoes: Int get() = rows.count { it.kind == ChangeKind.ATUALIZA }
    val divergencias: List<IncomingRow> get() = rows.filter { it.kind == ChangeKind.DIVERGE }
    val ignorados: Int get() = rows.count { it.kind == ChangeKind.IGNORADO }
}

class SyncService(
    private val store: SyncStore,
    private val stamp: SyncStamp,
    private val crypto: SyncCrypto,
    private val settingsRepository: SettingsRepository,
) {

    // ─── Exportacao ──────────────────────────────────────────────────────────

    /** Monta o pacote cifrado com as secoes escolhidas. Senha nula = sem protecao real. */
    fun buildPackage(sections: List<SyncSection>, password: String?): ByteArray {
        val tables = sections.flatMap { it.tables }.distinct().map { table ->
            val columns = store.columns(table).filter { it != LOCAL_ID }
            val referenceMaps = columns.mapNotNull { column ->
                referencedTable(column)?.let { column to store.localIdToUuid(it) }
            }.toMap()

            PackageTable(
                name = table,
                columns = columns,
                // Referencia sai como uuid; id local nao significa nada la fora.
                rows = store.rows(table, columns).map { row ->
                    row.mapValues { (column, value) -> toUuid(referenceMaps, column, value) }
                },
            )
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
            table.rows.mapNotNull { row ->
                val uuid = row["uuid"] ?: return@mapNotNull null
                val local = localByUuid[uuid]?.let { store.findByUuid(table.name, uuid, table.columns) }
                // Referencia obrigatoria sem destino torna a linha inaproveitavel:
                // inseri-la violaria o NOT NULL.
                val referenciaQuebrada = referencias.any { (column, disponiveis) ->
                    column in obrigatorias && row[column].let { it == null || it !in disponiveis }
                }
                val kind = when {
                    referenciaQuebrada -> ChangeKind.IGNORADO
                    local == null -> ChangeKind.NOVO
                    sameContent(local.mapValues { (c, v) -> toUuid(paraUuid, c, v) }, row, table.columns) ->
                        ChangeKind.IGUAL
                    // Nada aqui prova quem viu o que: se os dois lados mudaram, a
                    // decisao e do usuario. So o carimbo mais novo sugere o padrao.
                    else -> if (local["updated_by"] == row["updated_by"]) ChangeKind.ATUALIZA
                    else ChangeKind.DIVERGE
                }
                IncomingRow(
                    table = table.name,
                    uuid = uuid,
                    kind = kind,
                    description = describe(table.name, row),
                    localUpdatedAt = local?.get("updated_at"),
                    remoteUpdatedAt = row["updated_at"],
                    values = row,
                )
            }
        }
        return ImportPreview(header, rows.filter { it.kind != ChangeKind.IGUAL })
    }

    /** Grava as linhas aceitas, traduzindo as referencias para os ids locais. */
    fun apply(rows: List<IncomingRow>): Int {
        var aplicadas = 0
        rows.filter { it.kind != ChangeKind.IGNORADO }.groupBy { it.table }.forEach { (table, tableRows) ->
            val columns = store.columns(table).filter { it != LOCAL_ID }
            val referenceMaps = columns.mapNotNull { column ->
                referencedTable(column)?.let { column to store.uuidToLocalId(it) }
            }.toMap()

            tableRows.forEach { incoming ->
                val values = columns.associateWith { column ->
                    val raw = incoming.values[column]
                    // Referencia chegou como uuid: vira o id daqui, ou null se a
                    // linha apontada nao existe nesta instalacao.
                    if (referenceMaps.containsKey(column)) {
                        raw?.let { referenceMaps.getValue(column)[it]?.toString() }
                    } else {
                        raw
                    }
                }
                if (store.uuidToLocalId(table).containsKey(incoming.uuid)) {
                    store.updateByUuid(table, incoming.uuid, values)
                } else {
                    store.insert(table, values)
                }
                aplicadas++
            }
        }
        return aplicadas
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
        else -> ""
    }.ifBlank { row["uuid"]?.take(8).orEmpty() }
}
