package com.example.sonntag.tools

import com.example.sonntag.cloud.CloudAutoSync
import com.example.sonntag.cloud.CloudConfig
import com.example.sonntag.cloud.CloudStartMode
import com.example.sonntag.cloud.CloudSync
import com.example.sonntag.cloud.createSecretBox
import com.example.sonntag.data.repos.PreferencesRepository
import com.example.sonntag.data.sqldelight.SchemaUpgrade
import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.data.sqldelight.observableDriver
import com.example.sonntag.sync.SyncStamp
import com.example.sonntag.sync.SyncStore
import com.example.sonntag.sync.createSyncCrypto
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

/**
 * Um aparelho de mentira, para testar a nuvem contra o app de verdade (celular ou
 * emulador). O estado fica no proprio banco, entao da para encadear chamadas:
 *
 * - `entrar`: vira outra instalacao e entra na nuvem trocando os dados pelos dela;
 * - `editar`: renomeia [MEMBRO] para [NOVO] e sincroniza;
 * - `editar-local`: so renomeia, sem sincronizar (para montar um pacote divergente);
 * - `vigiar`: liga a sincronizacao automatica e mostra o nome de [MEMBRO] por [SEGUNDOS].
 *
 * Uso: DB=copia.db PG=postgresql://... ACAO=editar MEMBRO=Nome NOVO="Nome novo" ./gradlew cloudPeer
 */
fun main(args: Array<String>): Unit = runBlocking {
    val arquivo = File(args[0])
    val config = CloudConfig.fromUri(args[1]) ?: error("URL invalida")
    val acao = args[2]
    val membro = args.getOrElse(3) { "" }
    val novo = args.getOrElse(4) { "" }
    val segundos = args.getOrElse(5) { "90" }.toIntOrNull() ?: 90

    val driver = observableDriver("jdbc:sqlite:${arquivo.absolutePath}").first.also { SchemaUpgrade.run(it) }
    val db = SonntagDatabase(driver)
    val prefs = PreferencesRepository(db)
    val store = SyncStore(driver)
    if (acao == "entrar") {
        // Outra instalacao, mesmo sendo copia de um banco que ja existe.
        store.execute("UPDATE app_prefs SET valor = ? WHERE chave = 'device_id'", UUID.randomUUID().toString())
    }
    val stamp = SyncStamp(prefs)
    val sync = CloudSync(db, store, stamp, createSyncCrypto(), prefs, createSecretBox(File(arquivo.path + ".key")))

    fun renomear(): Long {
        val linha = db.schemaQueries.getAllMembers().executeAsList().first { it.nome == membro }
        db.schemaQueries.updateMember(
            novo, linha.sobrenome, linha.anciao, linha.servo_ministerial, linha.pioneiro,
            stamp.now(), stamp.deviceId, linha.id,
        )
        println("renomeado: $membro -> $novo")
        return linha.id
    }

    when (acao) {
        "entrar" -> {
            val probe = sync.probe(config, "congregacao-teste")
            println(sync.activate(config, probe, CloudStartMode.USAR_NUVEM))
        }
        "editar" -> {
            renomear()
            println(sync.syncNow())
        }
        "editar-local" -> renomear()
        "vigiar" -> {
            val auto = CloudAutoSync(sync, driver)
            auto.start()
            var ultimo = ""
            repeat(segundos) {
                val nomes = db.schemaQueries.getAllMembers().executeAsList().map { it.nome }
                val st = auto.status.value
                val texto = "estado=${st.state} pendentes=${st.pending} conflitos=${st.conflicts} membros=${nomes.take(4)}"
                if (texto != ultimo) println("[${it}s] $texto")
                ultimo = texto
                delay(1_000)
            }
        }
        else -> error("acao desconhecida: $acao")
    }
    kotlin.system.exitProcess(0)
}
