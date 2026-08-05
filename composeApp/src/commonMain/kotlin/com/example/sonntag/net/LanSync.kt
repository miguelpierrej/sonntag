package com.example.sonntag.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Um aparelho visto na rede local. */
data class LanPeer(
    val deviceId: String,
    val nome: String,
    val host: String,
    val port: Int,
)

/** Motivo de uma sincronizacao nao ter acontecido. */
enum class LanFailure { CODIGO_INCORRETO, SEM_RESPOSTA, ERRO }

class LanException(val failure: LanFailure) : Exception(failure.name)

/**
 * Sincronizacao pela rede local: descobre os aparelhos por perto e troca pacotes com
 * eles nos dois sentidos.
 *
 * Quem recebe mostra um codigo de quatro digitos; quem inicia precisa informa-lo. Sem
 * isso, qualquer instalacao na mesma rede seria um destino possivel.
 */
interface LanSync {
    /** Aparelhos anunciando-se agora. */
    val peers: StateFlow<List<LanPeer>>

    /** Codigo que este aparelho exibe enquanto esta visivel. */
    val myCode: String

    fun start(scope: CoroutineScope)
    fun stop()

    /** Troca com [peer]; devolve o pacote recebido. Lanca [LanException] se falhar. */
    suspend fun syncWith(peer: LanPeer, code: String): ByteArray
}

/** O que a rede precisa saber do resto do app. */
class LanSyncConfig(
    val deviceId: String,
    val deviceName: () -> String,
    /** Pacote com o que mudou desde `since` (null = tudo). */
    val buildPackage: (since: String?) -> ByteArray,
    /** Ate quando ja recebemos daquele aparelho. */
    val lastSyncWith: (deviceId: String) -> String?,
    /** Chamado quando um pacote chega de fora, para a tela mostrar o resumo. */
    val onPackageReceived: suspend (peerId: String, peerNome: String, bytes: ByteArray) -> Unit,
)

expect fun createLanSync(config: LanSyncConfig): LanSync
