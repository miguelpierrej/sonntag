package com.example.sonntag.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

/** Junta descoberta e transporte por tras da interface que a tela usa. */
private class LanSyncImpl(config: LanSyncConfig) : LanSync {

    /** Sorteado a cada sessao: vale enquanto o aparelho esta visivel. */
    override val myCode: String = Random.nextInt(1000, 10000).toString()

    private val transport = LanTransport(
        deviceId = config.deviceId,
        nome = config.deviceName,
        meuCodigo = { myCode },
        buildPackage = config.buildPackage,
        lastSyncWith = config.lastSyncWith,
        onPackageReceived = config.onPackageReceived,
    )

    private val discovery = LanDiscovery(
        deviceId = config.deviceId,
        nome = config.deviceName,
        port = { transport.port },
    )

    override val peers: StateFlow<List<LanPeer>> get() = discovery.peers

    override fun start(scope: CoroutineScope) {
        beforeStart()
        transport.start(scope)
        discovery.start(scope)
    }

    override fun stop() {
        discovery.stop()
        transport.stop()
        afterStop()
    }

    override suspend fun syncWith(peer: LanPeer, code: String): ByteArray =
        try {
            transport.sync(peer, code)
        } catch (e: LanException) {
            throw e
        } catch (e: Exception) {
            LanLog.e("troca com ${peer.nome} nao completou", e)
            throw LanException(LanFailure.SEM_RESPOSTA)
        }
}

/** Preparo especifico de plataforma (no Android, o bloqueio de multicast). */
internal expect fun beforeStart()
internal expect fun afterStop()

actual fun createLanSync(config: LanSyncConfig): LanSync = LanSyncImpl(config)
