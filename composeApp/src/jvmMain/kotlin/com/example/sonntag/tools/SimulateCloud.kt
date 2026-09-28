package com.example.sonntag.tools

import com.example.sonntag.cloud.CloudAutoSync
import com.example.sonntag.cloud.CloudConfig
import com.example.sonntag.cloud.CloudState
import com.example.sonntag.data.sqldelight.observableDriver
import com.example.sonntag.cloud.CloudStartMode
import com.example.sonntag.cloud.CloudSync
import com.example.sonntag.cloud.ConflictSide
import com.example.sonntag.i18n.AppLanguage
import com.example.sonntag.i18n.Translator
import com.example.sonntag.sync.SyncDescriber
import com.example.sonntag.cloud.createSecretBox
import com.example.sonntag.data.repos.PreferencesRepository
import com.example.sonntag.data.sqldelight.SchemaUpgrade
import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.sync.SyncSection
import com.example.sonntag.sync.SyncStamp
import com.example.sonntag.sync.SyncStore
import com.example.sonntag.sync.createSyncCrypto
import com.example.sonntag.sync.referencedTable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID
import kotlin.system.exitProcess

/**
 * Tres aparelhos contra um PostgreSQL de verdade, a partir de copias de um banco real.
 *
 * B gera as proprias reunioes (uuids diferentes, como na vida real); C entra por ultimo
 * trocando tudo pelo que esta na nuvem. No fim, os tres precisam ter o mesmo conteudo.
 *
 * Uso: DB=... PG=postgresql://postgres:senha@localhost:5432/postgres ./gradlew simulateCloud
 */
fun main(args: Array<String>): Unit = runBlocking {
    val origem = File(args[0])
    val config = CloudConfig.fromUri(args[1]) ?: error("URL invalida")
    val pasta = File(args.getOrElse(2) { "/tmp/nuvem" }).apply { mkdirs() }
    val frase = "congregacao-teste"
    var falhas = 0
    fun confere(ok: Boolean, oque: String) {
        println((if (ok) "  ok   " else "  FALHA ") + oque)
        if (!ok) falhas++
    }

    val a = Aparelho("A", origem.copyTo(File(pasta, "a.db"), overwrite = true))
    val b = Aparelho("B", origem.copyTo(File(pasta, "b.db"), overwrite = true))
    val c = Aparelho("C", origem.copyTo(File(pasta, "c.db"), overwrite = true))
    // B e C sao outras instalacoes: outro id, e reunioes/semanas geradas por conta propria.
    listOf(b, c).forEach { ap ->
        ap.store.execute("UPDATE app_prefs SET valor = ? WHERE chave = 'device_id'", UUID.randomUUID().toString())
        listOf("meetings", "cleaning_assignments", "settings").forEach { t ->
            ap.store.select("SELECT uuid FROM $t", 1).forEach { (u) ->
                ap.store.execute("UPDATE $t SET uuid = ? WHERE uuid = ?", UUID.randomUUID().toString(), u)
            }
        }
    }

    println("== A conecta numa nuvem vazia")
    val pa = a.sync.probe(config, frase)
    confere(!pa.cloudHasData, "nuvem vazia")
    println("  " + a.sync.activate(config, pa, CloudStartMode.COMBINAR))

    println("== senha errada")
    confere(runCatching { b.sync.probe(config, "outra") }.isFailure, "senha errada recusada")

    println("== B combina")
    val pb = b.sync.probe(config, frase)
    confere(pb.cloudHasData && pb.localHasData, "os dois lados tem dados")
    println("  " + b.sync.activate(config, pb, CloudStartMode.COMBINAR))
    confere(b.duplicadas() == 0, "B sem reunioes duplicadas (${b.duplicadas()})")

    println("== A sincroniza (recebe os apelidos)")
    println("  " + a.sync.syncNow())
    println("  " + b.sync.syncNow())
    println("  " + a.sync.syncNow())
    confere(a.duplicadas() == 0, "A sem reunioes duplicadas (${a.duplicadas()})")
    confere(a.retrato() == b.retrato(), "A e B iguais")

    println("== edicao em A chega em B")
    val membro = a.store.select("SELECT uuid FROM members WHERE deleted = 0 LIMIT 1", 1).first()[0]!!
    a.store.execute(
        "UPDATE members SET nome = 'Editado A', updated_at = ?, updated_by = ? WHERE uuid = ?",
        a.stamp.now(), a.stamp.deviceId, membro,
    )
    println("  " + a.sync.syncNow())
    println("  " + b.sync.syncNow())
    confere(b.store.select("SELECT nome FROM members WHERE uuid = ?", 1, membro).first()[0] == "Editado A", "nome chegou")

    println("== edicao simultanea: vence a mais nova, a outra vira conflito")
    a.store.execute("UPDATE members SET nome = 'Velho', updated_at = '2030-01-01T00:00:00Z', updated_by = ? WHERE uuid = ?", a.stamp.deviceId, membro)
    b.store.execute("UPDATE members SET nome = 'Novo', updated_at = '2030-01-01T00:00:05Z', updated_by = ? WHERE uuid = ?", b.stamp.deviceId, membro)
    println("  " + a.sync.syncNow())
    val rb = b.sync.syncNow()
    println("  $rb")
    println("  " + a.sync.syncNow())
    confere(a.store.select("SELECT nome FROM members WHERE uuid = ?", 1, membro).first()[0] == "Novo", "A ficou com a mais nova")
    confere(b.store.select("SELECT COUNT(*) FROM sync_conflicts", 1).first()[0] != "0", "B registrou o conflito")

    println("== revisao do conflito: mostra os dados, e a escolha vale em todos")
    val conflito = b.sync.conflicts().first { it.uuid == membro }
    val diff = SyncDescriber(b.store, Translator(AppLanguage.ES)).diff(conflito.table, conflito.local, conflito.remote)
    println("  ${diff.title}: " + diff.fields.joinToString { "${it.label}: este=${it.local} / outro=${it.remote}" })
    confere(diff.fields.any { it.label == "Nombre" && it.local == "Novo" && it.remote == "Velho" }, "campo Nombre com os dois valores")
    confere(conflito.inUse == ConflictSide.LOCAL, "B sabe que a versao dele e a que esta em uso")
    b.sync.resolveConflicts(mapOf(conflito.id to ConflictSide.REMOTA))
    println("  " + b.sync.syncNow())
    println("  " + a.sync.syncNow())
    listOf(a, b).forEach { ap ->
        val nome = ap.store.select("SELECT nome FROM members WHERE uuid = ?", 1, membro).first()[0]
        confere(nome == "Velho", "${ap.nome} ficou com a versao escolhida ($nome)")
    }
    confere(b.sync.conflictCount() == 0, "B sem conflitos pendentes")

    println("== exclusao em B chega em A")
    val reuniao = b.store.select("SELECT uuid FROM meetings WHERE deleted = 0 ORDER BY data DESC LIMIT 1", 1).first()[0]!!
    b.store.execute("UPDATE meetings SET deleted = 1, updated_at = ?, updated_by = ? WHERE uuid = ?", b.stamp.now(), b.stamp.deviceId, reuniao)
    println("  " + b.sync.syncNow())
    println("  " + a.sync.syncNow())
    confere(a.store.select("SELECT deleted FROM meetings WHERE uuid = ?", 1, reuniao).firstOrNull()?.get(0) == "1", "reuniao apagada em A")

    println("== C troca os dados locais pelos da nuvem")
    c.store.execute(
        "INSERT INTO members (nome, sobrenome, uuid, updated_at, updated_by) VALUES ('So', 'Local', ?, '2020-01-01T00:00:00Z', 'x')",
        UUID.randomUUID().toString(),
    )
    val pc = c.sync.probe(config, frase)
    println("  " + c.sync.activate(config, pc, CloudStartMode.USAR_NUVEM))
    confere(c.store.select("SELECT COUNT(*) FROM members WHERE nome = 'So' AND deleted = 0", 1).first()[0] == "0", "o que so existia em C sumiu")
    confere(c.duplicadas() == 0, "C sem reunioes duplicadas (${c.duplicadas()})")

    println("== todos sincronizam duas vezes")
    repeat(2) { listOf(a, b, c).forEach { println("  ${it.nome}: " + it.sync.syncNow()) } }
    val ra = a.retrato()
    listOf(b, c).forEach { ap ->
        val r = ap.retrato()
        val iguais = ra == r
        if (!iguais) TABELAS.forEach { t ->
            val x = ra[t].orEmpty(); val y = r[t].orEmpty()
            if (x != y) println("    $t: A-${ap.nome}=${(x - y).take(2)} ${ap.nome}-A=${(y - x).take(2)}")
        }
        confere(iguais, "A e ${ap.nome} com o mesmo conteudo")
    }

    println("== automatica: edicao em A aparece em B sem ninguem apertar nada")
    // Outro membro: o de antes ganhou uma edicao datada de 2030 no teste de conflito,
    // e uma edicao de agora perderia dela, com razao.
    val outro = a.store.select("SELECT uuid FROM members WHERE deleted = 0 AND uuid <> ? LIMIT 1", 1, membro).first()[0]!!
    a.auto.start(); b.auto.start()
    delay(3_000)
    fun editaEmA(nome: String) {
        val linha = a.db.schemaQueries.getAllMembers().executeAsList().first { it.uuid == outro }
        a.db.schemaQueries.updateMember(
            nome, linha.sobrenome, linha.anciao, linha.servo_ministerial, linha.pioneiro,
            a.stamp.now(), a.stamp.deviceId, linha.id,
        )
    }
    suspend fun esperaEmB(nome: String, limiteMs: Long): Long? {
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < limiteMs) {
            if (b.store.select("SELECT nome FROM members WHERE uuid = ?", 1, outro).first()[0] == nome) {
                return System.currentTimeMillis() - t0
            }
            delay(100)
        }
        return null
    }
    editaEmA("Automatico 1")
    val t1 = esperaEmB("Automatico 1", 15_000)
    println("  chegou em ${t1} ms")
    confere(t1 != null && t1 < 6_000, "chegou em menos de 6 s (aviso do servidor, nao o relogio)")
    confere(a.auto.status.value.pending == 0, "A sem pendencias (${a.auto.status.value.pending})")

    println("== automatica: servidor fora do ar, a edicao espera e sobe depois")
    ProcessBuilder("docker", "pause", "sonntag-pg").inheritIO().start().waitFor()
    editaEmA("Offline")
    val t2 = System.currentTimeMillis()
    while (a.auto.status.value.state != CloudState.SEM_CONEXAO && System.currentTimeMillis() - t2 < 90_000) delay(200)
    println("  estado de A: ${a.auto.status.value.state}, pendentes: ${a.auto.status.value.pending}")
    confere(a.auto.status.value.state == CloudState.SEM_CONEXAO, "A percebeu que esta sem conexao")
    confere(a.auto.status.value.pending > 0, "A mostra alteracao pendente")
    ProcessBuilder("docker", "unpause", "sonntag-pg").inheritIO().start().waitFor()
    val t3 = esperaEmB("Offline", 120_000)
    println("  chegou ${t3} ms depois de o servidor voltar")
    confere(t3 != null, "a edicao feita sem conexao chegou em B")
    delay(1_000)
    confere(a.auto.status.value.pending == 0, "A sem pendencias depois (${a.auto.status.value.pending})")

    println(if (falhas == 0) "TUDO CERTO" else "$falhas FALHA(S)")
    exitProcess(if (falhas == 0) 0 else 1)
}

private val TABELAS = SyncSection.entries.flatMap { it.tables }.distinct() - "meeting_days"

private class Aparelho(val nome: String, arquivo: File) {
    // O mesmo driver do app: avisa quem observa, que e como a sincronizacao
    // automatica percebe uma edicao.
    private val driver = observableDriver("jdbc:sqlite:${arquivo.absolutePath}").first.also { SchemaUpgrade.run(it) }
    val db = SonntagDatabase(driver)
    private val prefs = PreferencesRepository(db)
    val stamp = SyncStamp(prefs)
    val store = SyncStore(driver)
    val sync = CloudSync(db, store, stamp, createSyncCrypto(), prefs, createSecretBox(File(arquivo.path + ".key")))
    val auto = CloudAutoSync(sync, driver)

    fun duplicadas(): Int = store.select(
        "SELECT COUNT(*) FROM (SELECT data, tipo FROM meetings WHERE deleted = 0 GROUP BY data, tipo HAVING COUNT(*) > 1)",
        1,
    ).first()[0]!!.toInt()

    /** Linhas vivas de cada tabela, com referencias como uuid e sem o id local. */
    fun retrato(): Map<String, Set<String>> = TABELAS.associateWith { t ->
        val colunas = store.columns(t).filter { it != "id" && it != "updated_by" }
        val mapas = colunas.mapNotNull { c -> referencedTable(c)?.let { c to store.localIdToUuid(it) } }.toMap()
        // Filhas de reuniao apagada sao invisiveis e o banco real tem algumas.
        val reunioesVivas = store.select("SELECT id FROM meetings WHERE deleted = 0", 1).map { it[0] }.toSet()
        store.rows(t, colunas + listOfNotNull("id".takeIf { "meeting_id" in colunas }))
            .filter { it["deleted"] == "0" }
            .filter { "meeting_id" !in colunas || it["meeting_id"] in reunioesVivas }
            .map { row ->
            colunas.joinToString("|") { c -> mapas[c]?.let { m -> row[c]?.toLongOrNull()?.let { m[it] } } ?: row[c].orEmpty() }
        }.toSet()
    }
}
