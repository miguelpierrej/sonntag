package com.example.sonntag.data.sqldelight

import app.cash.sqldelight.db.SqlDriver
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object DatabaseFactory {
    /** Driver e banco sao expostos separados: a sincronizacao precisa do driver
     *  para ler e gravar tabelas de forma generica, na mesma conexao. */
    fun createDriver(): SqlDriver = createDatabaseDriver()

    fun createDatabase(driver: SqlDriver): SonntagDatabase {
        val hoje = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        // As tabelas novas vem antes de tudo: quem ja tinha o app instalado nao
        // recebeu o `Schema.create`, e sem elas as telas novas nao abrem.
        runCatching { SchemaUpgrade.run(driver) }
            .onFailure { println("[SchemaUpgrade] ERRO: ${it::class.simpleName}: ${it.message}") }
        // Antes de qualquer tela ler a agenda, com o schema ja no lugar. Um reparo que
        // falha nao pode impedir o app de abrir, mas tem de aparecer no log.
        runCatching { DataRepair.run(driver, hoje) }
            .onFailure { println("[DataRepair] ERRO: ${it::class.simpleName}: ${it.message}") }
        return SonntagDatabase(driver)
    }

    fun createDatabase(): SonntagDatabase = createDatabase(createDriver())
}
