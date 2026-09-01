package com.example.sonntag.data.sqldelight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/**
 * Cria as tabelas acrescentadas depois que o app ja estava instalado.
 *
 * O `Schema.create` do SQLDelight so roda em banco novo, e o projeto nao usa
 * arquivos de migracao: sem isto, quem ja tem o app instalado abriria a tela nova e
 * receberia "no such table". No desktop havia migracoes soltas no driver; aqui elas
 * valem para os dois sistemas, porque no celular o problema e o mesmo.
 *
 * Colunas acrescentadas a uma tabela que ja existia entram por [COLUNAS]: o
 * `CREATE TABLE IF NOT EXISTS` nao mexe em tabela criada antes.
 *
 * O DDL repete o de `schema.sq` de proposito — um e a verdade para bancos novos, o
 * outro para os que ja existem. Ao mexer numa destas tabelas, mude os dois.
 */
object SchemaUpgrade {

    private val TABELAS = listOf(
        """
        CREATE TABLE IF NOT EXISTS preaching_spots (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            nome TEXT NOT NULL,
            endereco TEXT,
            tipo TEXT NOT NULL,
            uuid TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            updated_by TEXT NOT NULL,
            deleted INTEGER NOT NULL DEFAULT 0
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS preaching_groups (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            nome TEXT NOT NULL,
            dirigente_id INTEGER,
            auxiliar_id INTEGER,
            spot_id INTEGER,
            ordem INTEGER NOT NULL DEFAULT 0,
            uuid TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            updated_by TEXT NOT NULL,
            deleted INTEGER NOT NULL DEFAULT 0
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS preaching_group_members (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            preaching_group_id INTEGER NOT NULL,
            member_id INTEGER NOT NULL,
            uuid TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            updated_by TEXT NOT NULL,
            deleted INTEGER NOT NULL DEFAULT 0
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS preaching_shifts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            tipo TEXT NOT NULL,
            data TEXT NOT NULL,
            hora_inicio TEXT NOT NULL,
            hora_fim TEXT,
            spot_id INTEGER,
            nota TEXT,
            designado1_id INTEGER,
            designado2_id INTEGER,
            designado3_id INTEGER,
            designado4_id INTEGER,
            ordem INTEGER NOT NULL DEFAULT 0,
            uuid TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            updated_by TEXT NOT NULL,
            deleted INTEGER NOT NULL DEFAULT 0
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS preaching_templates (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            tipo TEXT NOT NULL,
            dia_semana INTEGER NOT NULL,
            hora_inicio TEXT NOT NULL,
            hora_fim TEXT,
            spot_id INTEGER,
            ordem INTEGER NOT NULL DEFAULT 0,
            uuid TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            updated_by TEXT NOT NULL,
            deleted INTEGER NOT NULL DEFAULT 0
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS preaching_notes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            tipo TEXT NOT NULL,
            ano INTEGER NOT NULL,
            mes INTEGER NOT NULL,
            texto TEXT NOT NULL,
            uuid TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            updated_by TEXT NOT NULL,
            deleted INTEGER NOT NULL DEFAULT 0,
            UNIQUE(tipo, ano, mes)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            nome TEXT NOT NULL,
            data TEXT NOT NULL,
            tipo TEXT NOT NULL,
            uuid TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            updated_by TEXT NOT NULL,
            deleted INTEGER NOT NULL DEFAULT 0
        )
        """,
    )

    /** Coluna que nasceu depois da tabela: (tabela, nome, definicao). */
    private val COLUNAS = listOf(
        Triple("members", "anciao", "INTEGER NOT NULL DEFAULT 0"),
        Triple("members", "servo_ministerial", "INTEGER NOT NULL DEFAULT 0"),
        Triple("members", "pioneiro", "INTEGER NOT NULL DEFAULT 0"),
    )

    private val INDICES = listOf(
        "preaching_spots", "preaching_groups", "preaching_group_members",
        "preaching_shifts", "preaching_templates", "preaching_notes", "events",
    ).map { "CREATE UNIQUE INDEX IF NOT EXISTS ${it}_uuid ON $it(uuid)" }

    fun run(driver: SqlDriver) {
        (TABELAS + INDICES).forEach { driver.execute(null, it.trimIndent(), 0).value }
        COLUNAS.forEach { (tabela, coluna, definicao) ->
            if (coluna !in colunasDe(driver, tabela)) {
                driver.execute(null, "ALTER TABLE $tabela ADD COLUMN $coluna $definicao", 0).value
            }
        }
    }

    /** Nomes das colunas que a tabela tem hoje; vazio se a tabela nao existe. */
    private fun colunasDe(driver: SqlDriver, tabela: String): Set<String> =
        driver.executeQuery(
            identifier = null,
            sql = "SELECT name FROM pragma_table_info('$tabela')",
            parameters = 0,
            mapper = { cursor ->
                val nomes = mutableSetOf<String>()
                while (cursor.next().value) cursor.getString(0)?.let { nomes += it }
                QueryResult.Value(nomes.toSet())
            },
        ).value
}
