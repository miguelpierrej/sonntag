package com.example.sonntag.platform

import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ponte entre o seletor de arquivos do Android, que e assincrono e preso ao ciclo de
 * vida da Activity, e as chamadas `suspend` dos servicos de importacao/exportacao.
 *
 * Os launchers precisam ser registrados antes da Activity iniciar; por isso quem os
 * cria e a MainActivity, e aqui so guardamos a referencia e a espera pendente.
 */
object FilePicker {

    lateinit var openLauncher: ActivityResultLauncher<Array<String>>
    lateinit var createLauncher: ActivityResultLauncher<String>

    private var pending: CompletableDeferred<Uri?>? = null

    /** Abre o seletor de leitura e espera a escolha. Null = usuario cancelou. */
    suspend fun open(mimeTypes: Array<String>): Uri? {
        if (!::openLauncher.isInitialized) return null
        val waiter = CompletableDeferred<Uri?>()
        pending = waiter
        // Os ViewModels chamam a partir de Dispatchers.IO, mas o launcher exige a
        // thread principal — sem isto o app quebra ao abrir o seletor.
        withContext(Dispatchers.Main) { openLauncher.launch(mimeTypes) }
        return waiter.await()
    }

    /** Abre o seletor de gravacao com um nome sugerido. */
    suspend fun create(suggestedName: String): Uri? {
        if (!::createLauncher.isInitialized) return null
        val waiter = CompletableDeferred<Uri?>()
        pending = waiter
        withContext(Dispatchers.Main) { createLauncher.launch(suggestedName) }
        return waiter.await()
    }

    /** Chamado pela Activity quando o seletor retorna (inclusive ao cancelar). */
    fun deliver(uri: Uri?) {
        pending?.complete(uri)
        pending = null
    }
}
