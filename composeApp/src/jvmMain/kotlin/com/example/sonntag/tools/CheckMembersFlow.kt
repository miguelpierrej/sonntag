package com.example.sonntag.tools

import com.example.sonntag.data.repos.MembersRepository
import com.example.sonntag.data.repos.PreferencesRepository
import com.example.sonntag.data.repos.Responsabilidades
import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.data.sqldelight.observableDriver
import com.example.sonntag.sync.SyncStamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Confere que uma tela que observa os publicadores e avisada quando alguem cadastra
 * ou edita um — no desktop isso depende do driver do app, porque o driver JDBC que
 * vem no SQLDelight tem a escuta vazia.
 *
 *     ./gradlew :composeApp:checkMembersFlow
 */
fun main(args: Array<String>) {
    val arquivo = File(args.getOrElse(0) { System.getProperty("java.io.tmpdir") }, "check-members-flow.db")
    arquivo.delete()
    val (driver, fonte) = observableDriver("jdbc:sqlite:${arquivo.absolutePath}")
    SonntagDatabase.Schema.create(driver)
    val db = SonntagDatabase(driver)
    val repo = MembersRepository(db, SyncStamp(PreferencesRepository(db)))

    val visto = mutableListOf<String>()
    runBlocking {
        val tela = launch {
            repo.getAll().collect { lista ->
                visto += lista.joinToString { "${it.nome} ${it.sobrenome}${if (it.anciao != 0L) " (AN)" else ""}" }
            }
        }
        delay(200)
        repo.insert("Novo", "Publicador", Responsabilidades(anciao = true))
        delay(300)
        repo.update(repo.getAllOnce().first().id, "Novo", "Publicador", Responsabilidades())
        delay(300)
        tela.cancel()
    }
    driver.close()
    fonte.close()
    arquivo.delete()

    visto.forEachIndexed { i, linha -> println("a tela viu [$i]: \"$linha\"") }
    println(if (visto.size >= 3) "OK: cadastrar e editar chegam na tela sem reabrir o app" else "FALHOU: a tela ficou parada")
}
