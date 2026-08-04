package com.example.sonntag.sync

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/** Uma linha como texto: nome da coluna -> valor (null continua null). */
typealias RowValues = Map<String, String?>

/**
 * Leitura e escrita genericas das tabelas sincronizaveis, por cima do mesmo driver
 * do app (nao abre segunda conexao: o SQLite nao gosta de dois escritores).
 *
 * Tudo trafega como texto. A afinidade de tipo do SQLite converte "7" de volta para
 * inteiro ao gravar numa coluna INTEGER, o que evita carregar o tipo de cada coluna.
 */
class SyncStore(private val driver: SqlDriver) {

    fun columns(table: String): List<String> = query(
        "SELECT name FROM pragma_table_info('$table')",
    ) { it.getString(0)!! }

    /** Colunas obrigatorias: uma referencia que nao resolve aqui inviabiliza a linha. */
    fun notNullColumns(table: String): Set<String> = query(
        "SELECT name FROM pragma_table_info('$table') WHERE \"notnull\" = 1",
    ) { it.getString(0)!! }.toSet()

    /** Todas as linhas, inclusive as excluidas: a lapide precisa viajar. */
    fun rows(table: String, columns: List<String>): List<RowValues> {
        val projection = columns.joinToString(", ") { "CAST($it AS TEXT)" }
        return query("SELECT $projection FROM $table") { cursor ->
            columns.mapIndexed { index, name -> name to cursor.getString(index) }.toMap()
        }
    }

    /** uuid -> id local, para traduzir referencias de outra instalacao. */
    fun uuidToLocalId(table: String): Map<String, Long> = query(
        "SELECT uuid, id FROM $table",
    ) { it.getString(0)!! to it.getLong(1)!! }.toMap()

    /** id local -> uuid, para converter referencias na exportacao. */
    fun localIdToUuid(table: String): Map<Long, String> = query(
        "SELECT id, uuid FROM $table",
    ) { it.getLong(0)!! to it.getString(1)!! }.toMap()

    /** Linha existente (por uuid) com as colunas pedidas, ou null. */
    fun findByUuid(table: String, uuid: String, columns: List<String>): RowValues? {
        val projection = columns.joinToString(", ") { "CAST($it AS TEXT)" }
        return query("SELECT $projection FROM $table WHERE uuid = ?", uuid) { cursor ->
            columns.mapIndexed { index, name -> name to cursor.getString(index) }.toMap()
        }.firstOrNull()
    }

    fun insert(table: String, values: RowValues) {
        val cols = values.keys.toList()
        val sql = "INSERT INTO $table (${cols.joinToString(", ")}) " +
            "VALUES (${cols.joinToString(", ") { "?" }})"
        driver.execute(null, sql, cols.size) {
            cols.forEachIndexed { index, name -> bindString(index, values[name]) }
        }.value
    }

    /** Atualiza pelo uuid, nunca pelo id: o id do outro lado nao vale aqui. */
    fun updateByUuid(table: String, uuid: String, values: RowValues) {
        val cols = values.keys.filter { it != "id" && it != "uuid" }
        if (cols.isEmpty()) return
        val sql = "UPDATE $table SET ${cols.joinToString(", ") { "$it = ?" }} WHERE uuid = ?"
        driver.execute(null, sql, cols.size + 1) {
            cols.forEachIndexed { index, name -> bindString(index, values[name]) }
            bindString(cols.size, uuid)
        }.value
    }

    private fun <T : Any> query(sql: String, parameter: String? = null, row: (app.cash.sqldelight.db.SqlCursor) -> T): List<T> =
        driver.executeQuery(
            identifier = null,
            sql = sql,
            parameters = if (parameter == null) 0 else 1,
            binders = if (parameter == null) null else { { bindString(0, parameter) } },
            mapper = { cursor ->
                val out = mutableListOf<T>()
                while (cursor.next().value) out += row(cursor)
                QueryResult.Value(out.toList())
            },
        ).value
}
