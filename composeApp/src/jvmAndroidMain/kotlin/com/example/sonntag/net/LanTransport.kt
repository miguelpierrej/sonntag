package com.example.sonntag.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket

/**
 * Troca de pacotes entre dois aparelhos na rede local.
 *
 * O protocolo e simetrico e cabe em duas rodadas, porque cada lado precisa saber a
 * partir de quando o outro ja tem os seus dados:
 *
 * ```
 * A -> B   deviceId, nome, desde-quando-A-ja-tem-de-B
 * B -> A   deviceId, nome, desde-quando-B-ja-tem-de-A, pacote de B
 * A -> B   pacote de A
 * ```
 *
 * Cada lado devolve o seu proprio resumo para o usuario conferir antes de gravar; o
 * envio nao aplica nada sozinho.
 */
class LanTransport(
    private val deviceId: String,
    private val nome: () -> String,
    /** Codigo exibido por este aparelho; quem inicia precisa acerta-lo. */
    private val meuCodigo: () -> String,
    /** Pacote com o que mudou desde [since] (null = tudo). */
    private val buildPackage: (since: String?) -> ByteArray,
    /** Ate quando ja recebemos daquele aparelho. */
    private val lastSyncWith: (deviceId: String) -> String?,
    /** Chamado com o pacote que chegou; devolve true se o usuario aplicou. */
    private val onPackageReceived: suspend (peerId: String, peerNome: String, bytes: ByteArray) -> Unit,
) {
    var port: Int = 0
        private set

    private var server: ServerSocket? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (server != null) return
        val s = ServerSocket(0)   // porta livre escolhida pelo sistema
        server = s
        port = s.localPort
        LanLog.i("atendendo na porta $port")
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { s.accept() }.getOrNull()?.let { cliente ->
                    // Uma troca que falha nao pode derrubar o laco de accept, senao o
                    // aparelho fica visivel mas nao atende mais ninguem.
                    launch(Dispatchers.IO) {
                        runCatching { atende(cliente) }
                            .onFailure { LanLog.e("falhei ao atender ${cliente.inetAddress}", it) }
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { server?.close() }
        server = null
        port = 0
    }

    /** Lado que recebe a conexao. */
    private suspend fun atende(socket: Socket) = socket.use {
        val entrada = DataInputStream(socket.getInputStream().buffered())
        val saida = DataOutputStream(socket.getOutputStream().buffered())

        val peerId = entrada.readUTF()
        val peerNome = entrada.readUTF()
        val codigo = entrada.readUTF()
        val desdeQuandoElesTem = entrada.readUTF().ifEmpty { null }
        LanLog.i("$peerNome quer trocar; ja tem o meu ate ${desdeQuandoElesTem ?: "nunca"}")

        if (codigo != meuCodigo()) {
            LanLog.i("codigo errado de $peerNome, recusando")
            saida.writeUTF("NEGADO")
            saida.flush()
            return
        }
        saida.writeUTF("OK")

        // Rodada 1: mandamos o nosso, a partir do que eles ja tem
        saida.writeUTF(deviceId)
        saida.writeUTF(nome())
        saida.writeUTF(lastSyncWith(peerId).orEmpty())
        val meu = buildPackage(desdeQuandoElesTem)
        saida.writeInt(meu.size)
        saida.write(meu)
        saida.flush()
        LanLog.i("mandei ${meu.size} bytes para $peerNome")

        // Rodada 2: recebemos o deles
        val tamanho = entrada.readInt()
        val deles = ByteArray(tamanho).also { entrada.readFully(it) }
        LanLog.i("recebi ${deles.size} bytes de $peerNome")
        onPackageReceived(peerId, peerNome, deles)
    }

    /** Lado que inicia. Devolve o pacote recebido do outro. */
    suspend fun sync(peer: LanPeer, codigo: String): ByteArray = Socket().use { socket ->
        LanLog.i("conectando em ${peer.nome} (${peer.host}:${peer.port})")
        socket.connect(java.net.InetSocketAddress(peer.host, peer.port), 5000)
        val saida = DataOutputStream(socket.getOutputStream().buffered())
        val entrada = DataInputStream(socket.getInputStream().buffered())

        saida.writeUTF(deviceId)
        saida.writeUTF(nome())
        saida.writeUTF(codigo)
        saida.writeUTF(lastSyncWith(peer.deviceId).orEmpty())
        saida.flush()

        val resposta = entrada.readUTF()
        if (resposta != "OK") {
            LanLog.i("${peer.nome} respondeu $resposta")
            throw LanException(LanFailure.CODIGO_INCORRETO)
        }

        entrada.readUTF()  // deviceId deles, ja conhecido pelo anuncio
        entrada.readUTF()  // nome
        val desdeQuandoElesTem = entrada.readUTF().ifEmpty { null }
        val tamanho = entrada.readInt()
        val deles = ByteArray(tamanho).also { entrada.readFully(it) }

        LanLog.i("recebi ${deles.size} bytes de ${peer.nome}")

        val meu = buildPackage(desdeQuandoElesTem)
        saida.writeInt(meu.size)
        saida.write(meu)
        saida.flush()
        LanLog.i("mandei ${meu.size} bytes para ${peer.nome}")

        deles
    }
}
