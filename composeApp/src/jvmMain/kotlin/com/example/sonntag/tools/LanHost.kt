package com.example.sonntag.tools

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.example.sonntag.data.repos.PreferencesRepository
import com.example.sonntag.data.repos.SettingsRepository
import com.example.sonntag.data.sqldelight.SchemaUpgrade
import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.net.LanSyncConfig
import com.example.sonntag.net.createLanSync
import com.example.sonntag.sync.ImportCategory
import com.example.sonntag.sync.SyncSection
import com.example.sonntag.sync.SyncService
import com.example.sonntag.sync.SyncStamp
import com.example.sonntag.sync.SyncStore
import com.example.sonntag.sync.category
import com.example.sonntag.sync.createSyncCrypto
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Poe este banco visivel na rede local, sem abrir a interface — o mesmo transporte que
 * o app usa. Serve para testar a sincronizacao com o celular a partir da linha de
 * comando.
 *
 *     DB=/copia/data.db SEGUNDOS=180 ./gradlew :composeApp:lanHost
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
    val nome = SettingsRepository(db, stamp).getSettingsOnce()?.nome ?: "Desktop"

    // Os dias de reuniao nao viajam pela rede — mesma regra da tela.
    val foraDaRede = setOf("meeting_days")

    val lan = createLanSync(
        LanSyncConfig(
            deviceId = stamp.deviceId,
            deviceName = { "$nome (desktop)" },
            buildPackage = { since ->
                val bytes = service.buildPackage(SyncSection.entries.toList(), null, since, foraDaRede)
                println("→ enviando pacote de ${bytes.size} bytes (since=$since)")
                bytes
            },
            lastSyncWith = { null },
            onPackageReceived = { _, peerNome, bytes ->
                println("← recebi ${bytes.size} bytes de $peerNome")
                val preview = service.preview(bytes, null)
                if (preview == null) {
                    println("   nao consegui ler o pacote")
                } else {
                    val porCategoria = preview.rows.groupingBy { it.category() }.eachCount()
                    println("   ${preview.rows.size} linhas: $porCategoria")
                    println("   marcados por padrao (novos): ${preview.aceitasPorPadrao.size}")
                    println("   NADA foi aplicado: este e um teste, o banco daqui nao muda")
                }
            },
        ),
    )

    val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lan.start(escopo)
    println("visivel na rede como \"$nome (desktop)\"")
    println("CODIGO DESTE APARELHO: ${lan.myCode}")
    println("esperando ${args[1]}s...")

    runBlocking {
        repeat(args[1].toInt() / 5) {
            delay(5_000)
            val vistos = lan.peers.value
            if (vistos.isNotEmpty()) println("   aparelhos vistos: ${vistos.joinToString { "${it.nome} (${it.host})" }}")
        }
    }
    lan.stop()
    driver.close()
    println("fim")
}
