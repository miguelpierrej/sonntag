package com.example.sonntag.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface

/**
 * Descoberta na rede local por multicast UDP.
 *
 * Cada instalacao anuncia "estou aqui, sou fulano, atendo na porta X" a cada poucos
 * segundos, e escuta os anuncios dos outros. Nao ha servidor nem configuracao: quem
 * esta na mesma rede se ve.
 *
 * Preferimos multicast simples a mDNS para nao arrastar dependencia nova — o que se
 * anuncia aqui e uma linha de texto, nao um servico DNS completo.
 */
/** Nomes de interface que costumam ser ponte virtual, e nao a rede de verdade. */
private val VIRTUAIS = listOf("docker", "br-", "veth", "virbr", "tun", "tap")

/** So para descobrir a rota de saida; nenhum pacote e enviado para ca. */
private const val HOST_DE_ROTA = "8.8.8.8"

class LanDiscovery(
    private val deviceId: String,
    private val nome: () -> String,
    private val port: () -> Int,
) {
    private val _peers = MutableStateFlow<List<LanPeer>>(emptyList())
    val peers: StateFlow<List<LanPeer>> = _peers.asStateFlow()

    private var jobs = mutableListOf<Job>()
    private var socket: MulticastSocket? = null

    /** Ultima vez que cada aparelho foi visto, para esquecer quem sumiu. */
    private val vistos = mutableMapOf<String, Pair<LanPeer, Long>>()

    fun start(scope: CoroutineScope) {
        if (jobs.isNotEmpty()) return
        val grupo = InetAddress.getByName(DISCOVERY_GROUP)
        val iface = interfaceParaMulticast()
        LanLog.i("interface do multicast: ${iface?.name ?: "padrao do sistema"}")
        val s = try {
            MulticastSocket(DISCOVERY_PORT).apply {
                reuseAddress = true
                // Sem isto, em maquinas com varias interfaces o anuncio some.
                iface?.let { networkInterface = it }
                joinGroup(InetSocketAddress(grupo, DISCOVERY_PORT), iface)
            }
        } catch (e: Exception) {
            LanLog.e("nao consegui entrar no grupo multicast", e)
            return
        }
        socket = s
        LanLog.i("anunciando em $DISCOVERY_GROUP:$DISCOVERY_PORT")

        jobs += scope.launch(Dispatchers.IO) {
            var falhasSeguidas = 0
            while (isActive) {
                runCatching {
                    val texto = "$ANNOUNCE_PREFIX|$deviceId|${nome()}|${port()}"
                    val bytes = texto.encodeToByteArray()
                    s.send(DatagramPacket(bytes, bytes.size, grupo, DISCOVERY_PORT))
                }.onFailure {
                    // So o primeiro de cada rajada, para nao encher o log de dois em dois segundos.
                    if (falhasSeguidas++ == 0) LanLog.e("falhei ao anunciar", it)
                }.onSuccess { falhasSeguidas = 0 }
                delay(2000)
            }
        }

        jobs += scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(1024)
            while (isActive) {
                runCatching {
                    val pacote = DatagramPacket(buffer, buffer.size)
                    s.receive(pacote)
                    val partes = pacote.data.decodeToString(0, pacote.length).split("|")
                    if (partes.size == 4 && partes[0] == ANNOUNCE_PREFIX && partes[1] != deviceId) {
                        val peer = LanPeer(
                            deviceId = partes[1],
                            nome = partes[2],
                            host = pacote.address.hostAddress ?: return@runCatching,
                            port = partes[3].toIntOrNull() ?: return@runCatching,
                        )
                        synchronized(vistos) {
                            val novo = peer.deviceId !in vistos
                            vistos[peer.deviceId] = peer to nowMillis()
                            esqueceOsSumidos()
                            if (novo) LanLog.i("achei ${peer.nome} em ${peer.host}:${peer.port}")
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        runCatching { socket?.close() }
        socket = null
        synchronized(vistos) { vistos.clear() }
        _peers.value = emptyList()
    }

    /** Quem nao se anuncia ha 8 segundos saiu da rede. */
    private fun esqueceOsSumidos() {
        val limite = nowMillis() - 8000
        vistos.entries.removeAll { it.value.second < limite }
        _peers.value = vistos.values.map { it.first }.sortedBy { it.nome }
    }

    private fun nowMillis(): Long = System.currentTimeMillis()

    /**
     * Primeira interface ativa com endereco IPv4 e suporte a multicast.
     *
     * Filtrar por IPv4 e essencial: interfaces virtuais (docker, tun) e as so-IPv6
     * aparecem antes na lista e fazem o joinGroup falhar com "Network interface not
     * configured for IPv4".
     */
    /**
     * A interface que realmente chega na rede local.
     *
     * Pegar a primeira que serve multicast escolhe, numa maquina com Docker ou VPN,
     * uma ponte virtual que nao leva a lugar nenhum: o anuncio sai e ninguem ve.
     * Perguntamos ao sistema por onde ele sairia para a rede e usamos aquela.
     */
    private fun interfaceParaMulticast(): NetworkInterface? =
        interfaceDaRota() ?: primeiraUtil()

    private fun interfaceDaRota(): NetworkInterface? = runCatching {
        DatagramSocket().use { sonda ->
            // "Conectar" em UDP nao envia pacote nenhum: so resolve a rota de saida.
            sonda.connect(InetAddress.getByName(HOST_DE_ROTA), 53)
            NetworkInterface.getByInetAddress(sonda.localAddress)
        }?.takeIf { it.isUp && !it.isLoopback && it.supportsMulticast() }
    }.getOrNull()

    /** Sem rota conhecida, a primeira util — pontes virtuais por ultimo. */
    private fun primeiraUtil(): NetworkInterface? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { iface ->
                iface.isUp && !iface.isLoopback && iface.supportsMulticast() &&
                    iface.inetAddresses.toList().any { it is Inet4Address }
            }
            .minByOrNull { if (ehVirtual(it.name)) 1 else 0 }
    }.getOrNull()

    private fun ehVirtual(nome: String): Boolean =
        VIRTUAIS.any { nome.startsWith(it) }
}
