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
import java.io.File

/**
 * Monta um pacote .sonntag a partir de um banco, sem abrir o app — serve para testar
 * a importacao (inclusive a abertura pelo gerenciador de arquivos no celular).
 *
 *     DB=/caminho/data.db OUT=/tmp/pacote.sonntag ./gradlew :composeApp:makePackage
 */
fun main(args: Array<String>) {
    val fonte = HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:sqlite:${args[0]}"
        maximumPoolSize = 1
    })
    val driver = fonte.asJdbcDriver()
    SchemaUpgrade.run(driver)
    val db = SonntagDatabase(driver)
    val stamp = SyncStamp(PreferencesRepository(db))
    val service = SyncService(SyncStore(driver), stamp, createSyncCrypto(), SettingsRepository(db, stamp))

    val bytes = service.buildPackage(SyncSection.entries.toList(), password = null)
    File(args[1]).writeBytes(bytes)
    println("pacote: ${args[1]} (${bytes.size} bytes, ${SyncSection.entries.size} blocos)")
    driver.close()
}
