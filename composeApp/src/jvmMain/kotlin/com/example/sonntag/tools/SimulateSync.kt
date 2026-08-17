package com.example.sonntag.tools

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.example.sonntag.data.repos.PreferencesRepository
import com.example.sonntag.data.repos.SettingsRepository
import com.example.sonntag.data.sqldelight.SchemaUpgrade
import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.sync.SyncSection
import com.example.sonntag.sync.SyncService
import com.example.sonntag.sync.SyncStamp
import com.example.sonntag.sync.SyncStore
import com.example.sonntag.sync.createSyncCrypto
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import app.cash.sqldelight.db.SqlDriver

/**
 * Simula uma troca de dados entre dois bancos e mostra o que a importacao faria,
 * linha por linha, nas tabelas escolhidas. Serve para investigar sobrescritas e
 * duplicacoes sem precisar de dois aparelhos.
 *
 *     DE=/copia/a.db PARA=/copia/b.db TABELAS=meeting_days,meetings \
 *       APLICAR=nao ./gradlew :composeApp:simulateSync
 */
fun main(args: Array<String>) {
    val (deDb, paraDb, tabelas, aplicar) = Args(args)

    val (de, deDriver) = abre(deDb)
    val (para, paraDriver) = abre(paraDb)

    val pacote = de.buildPackage(SyncSection.entries.toList(), password = null)
    println("pacote de $deDb: ${pacote.size} bytes")

    val preview = para.preview(pacote, null) ?: error("nao consegui ler o pacote")
    println("linhas no resumo: ${preview.rows.size}")
    println()

    tabelas.forEach { tabela ->
        val linhas = preview.rows.filter { it.table == tabela }
        println("== $tabela: ${linhas.size} linhas")
        linhas.forEach { linha ->
            val resumo = linha.values.filterKeys { it in CHAVES }.entries
                .joinToString(", ") { "${it.key}=${it.value}" }
            println("   ${linha.kind}  uuid=${linha.uuid.take(8)}  match=${linha.localUuid?.take(8) ?: "-"}  $resumo")
        }
        println("   estado atual em $paraDb:")
        conta(paraDriver, tabela)
        println()
    }

    if (aplicar) {
        // Aplica o padrao da tela: so os novos entram marcados.
        val n = para.apply(preview, preview.aceitasPorPadrao)
        println("aplicadas $n linhas")
        tabelas.forEach { tabela ->
            println("== $tabela depois de aplicar:")
            conta(paraDriver, tabela)
        }
    }
    deDriver.close()
    paraDriver.close()
}

private val CHAVES = setOf("dia_semana", "hora", "data", "tipo", "deleted", "updated_at", "nome")

private class Args(args: Array<String>) {
    val de = args[0]
    val para = args[1]
    val tabelas = args[2].split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val aplicar = args.getOrNull(3)?.equals("sim", ignoreCase = true) == true
    operator fun component1() = de
    operator fun component2() = para
    operator fun component3() = tabelas
    operator fun component4() = aplicar
}

private fun abre(caminho: String): Pair<SyncService, SqlDriver> {
    val fonte = HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:sqlite:$caminho"
        maximumPoolSize = 1
    })
    val driver = fonte.asJdbcDriver()
    SchemaUpgrade.run(driver)
    val db = SonntagDatabase(driver)
    val stamp = SyncStamp(PreferencesRepository(db))
    return SyncService(SyncStore(driver), stamp, createSyncCrypto(), SettingsRepository(db, stamp)) to driver
}

private fun conta(driver: SqlDriver, tabela: String) {
    val colunas = when (tabela) {
        "meeting_days" -> listOf("dia_semana", "hora", "deleted", "substr(uuid,1,8)")
        "meetings" -> listOf("data", "hora", "tipo", "deleted", "substr(uuid,1,8)")
        else -> listOf("substr(uuid,1,8)", "deleted")
    }
    val ordem = if (tabela == "meetings") "data LIMIT 8" else "1"
    driver.executeQuery(
        null,
        "SELECT ${colunas.joinToString(", ")} FROM $tabela ORDER BY $ordem",
        { cursor ->
            while (cursor.next().value) {
                println(
                    "      " + colunas.indices.joinToString("  ") { i ->
                        cursor.getString(i) ?: cursor.getLong(i)?.toString() ?: "null"
                    },
                )
            }
            app.cash.sqldelight.db.QueryResult.Value(Unit)
        },
        0,
    ).value
}
