package com.example.sonntag.data.sqldelight

import app.cash.sqldelight.db.SqlDriver

/**
 * Cria as tabelas acrescentadas depois que o app ja estava instalado.
 *
 * O `Schema.create` do SQLDelight so roda em banco novo, e o projeto nao usa
 * arquivos de migracao: sem isto, quem ja tem o app instalado abriria a tela nova e
 * receberia "no such table". No desktop havia migracoes soltas no driver; aqui elas
 * valem para os dois sistemas, porque no celular o problema e o mesmo.
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
    )

    private val INDICES = listOf(
        "preaching_spots", "preaching_groups", "preaching_shifts",
        "preaching_templates", "preaching_notes",
    ).map { "CREATE UNIQUE INDEX IF NOT EXISTS ${it}_uuid ON $it(uuid)" }

    fun run(driver: SqlDriver) {
        (TABELAS + INDICES).forEach { driver.execute(null, it.trimIndent(), 0).value }
    }
}
