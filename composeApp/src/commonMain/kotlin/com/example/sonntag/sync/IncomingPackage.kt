package com.example.sonntag.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Pacote que chegou de fora do app — no Android, tocar num arquivo `.sonntag` e
 * escolher o Sonntag para abrir.
 *
 * Fica aqui, e nao num parametro de tela, porque quem recebe o arquivo e a Activity,
 * antes de existir navegacao: a tela de Dados consome quando aparece.
 */
object IncomingPackage {

    var bytes by mutableStateOf<ByteArray?>(null)
        private set

    /** Nome do arquivo, quando o sistema informa; so serve para mostrar ao usuario. */
    var nome by mutableStateOf<String?>(null)
        private set

    fun oferecer(bytes: ByteArray, nome: String?) {
        this.bytes = bytes
        this.nome = nome
    }

    /** Devolve o pacote uma unica vez: a tela que consumir fica responsavel por ele. */
    fun consumir(): ByteArray? {
        val atual = bytes
        bytes = null
        nome = null
        return atual
    }
}
