package com.example.sonntag.cloud

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile

/** Intervalo da checagem periodica, quando nenhum aviso chega antes. */
private const val INTERVALO_MS = 10_000L

/** Espera depois de uma edicao local: varias seguidas sobem juntas. */
private const val AGRUPAR_MS = 2_000L

/** Prazo de cada espera do ouvinte; ao vencer, ele confere se ainda deve escutar. */
private const val ESCUTA_MS = 25_000

/** Sem conexao, espacar as tentativas poupa bateria e dados. */
private val ESPERAS_SEM_CONEXAO = listOf(10_000L, 30_000L, 60_000L)

enum class CloudState { DESLIGADA, SINCRONIZANDO, SINCRONIZADA, SEM_CONEXAO, ERRO }

data class CloudStatus(
    val state: CloudState = CloudState.DESLIGADA,
    /** Instante (UTC, ISO) da ultima sincronizacao completa. */
    val lastSyncAt: String? = null,
    /** Ultima volta que trouxe ou levou alguma coisa. */
    val lastExchange: CloudSyncResult? = null,
    val failure: CloudFailure? = null,
    val failureDetail: String? = null,
    val pending: Int = 0,
    val conflicts: Int = 0,
)

/**
 * Mantem a nuvem em dia sozinha enquanto o app esta aberto.
 *
 * Tres coisas disparam uma sincronizacao: uma edicao local (depois de [AGRUPAR_MS]), um
 * aviso do servidor de que outro aparelho gravou (LISTEN/NOTIFY) e o relogio, a cada
 * [INTERVALO_MS], para quando os avisos nao chegam. No Android, para quando o app vai
 * para segundo plano e retoma ao voltar.
 */
class CloudAutoSync(
    private val cloudSync: CloudSync,
    private val driver: SqlDriver,
) {
    private val _status = MutableStateFlow(CloudStatus())
    val status: StateFlow<CloudStatus> = _status.asStateFlow()

    private val foreground = MutableStateFlow(true)
    private val gatilhos = Channel<Unit>(Channel.CONFLATED)

    /** Houve edicao local desde a ultima varredura. Comeca verdadeiro: pode ter havido offline. */
    @Volatile
    private var sujo = true

    private var scope: CoroutineScope? = null
    private var agrupar: Job? = null

    /**
     * Chamado na thread de quem gravou, as vezes ainda com a conexao do banco em uso:
     * nada de consultar aqui, so marcar e agendar.
     */
    private val vigia = Query.Listener {
        if (cloudSync.applyingRemote || !cloudSync.isActive) return@Listener
        sujo = true
        agrupar?.cancel()
        agrupar = scope?.launch {
            atualizarContagens()
            delay(AGRUPAR_MS)
            gatilhos.trySend(Unit)
        }
    }

    /** Chamado uma vez, na abertura do app. */
    fun start(visible: Boolean = true) {
        if (scope != null) return
        foreground.value = visible
        val novo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = novo
        driver.addListener(*CLOUD_TABLES.toTypedArray(), listener = vigia)
        novo.launch { laco() }
        novo.launch { escuta() }
    }

    /** O Android avisa quando o app aparece ou some da tela. */
    fun setForeground(value: Boolean) {
        foreground.value = value
        if (value) gatilhos.trySend(Unit)
    }

    /** "Sincronizar agora". */
    fun requestNow() {
        sujo = true
        gatilhos.trySend(Unit)
    }

    private suspend fun esperarLigada() {
        combine(cloudSync.active, foreground) { ativa, frente -> ativa && frente }.first { it }
    }

    private suspend fun laco() {
        var semConexao = 0
        while (true) {
            if (!cloudSync.isActive || !foreground.value) {
                if (!cloudSync.isActive) _status.value = CloudStatus()
                esperarLigada()
                sujo = true
            }

            val varrer = sujo
            sujo = false
            // A checagem periodica e silenciosa: piscar "Sincronizando..." a cada 10 s
            // so distrai. O estado aparece quando ha algo daqui subindo ou depois de falha.
            _status.update {
                if (varrer || it.state != CloudState.SINCRONIZADA) it.copy(state = CloudState.SINCRONIZANDO) else it
            }
            val espera = try {
                val result = cloudSync.syncNow(collectLocal = varrer)
                semConexao = 0
                _status.update {
                    it.copy(
                        state = CloudState.SINCRONIZADA,
                        lastSyncAt = cloudSync.lastSyncAt,
                        lastExchange = if (result.recebidos + result.enviados > 0) result else it.lastExchange,
                        failure = null,
                        failureDetail = null,
                    )
                }
                INTERVALO_MS
            } catch (e: CloudException) {
                if (varrer) sujo = true
                if (!cloudSync.isActive) continue
                val offline = e.failure == CloudFailure.SEM_CONEXAO
                _status.update {
                    it.copy(
                        state = if (offline) CloudState.SEM_CONEXAO else CloudState.ERRO,
                        failure = e.failure,
                        failureDetail = e.message,
                    )
                }
                if (offline) ESPERAS_SEM_CONEXAO[minOf(semConexao++, ESPERAS_SEM_CONEXAO.lastIndex)] else 60_000L
            } catch (e: Exception) {
                if (varrer) sujo = true
                _status.update {
                    it.copy(state = CloudState.ERRO, failure = CloudFailure.ERRO, failureDetail = e.message)
                }
                60_000L
            }
            atualizarContagens()
            withTimeoutOrNull(espera) { gatilhos.receive() }
        }
    }

    /** Fica escutando os avisos do servidor enquanto a nuvem estiver ligada e o app visivel. */
    private suspend fun escuta() {
        var falhas = 0
        while (true) {
            esperarLigada()
            val config = cloudSync.savedConfig() ?: run { delay(INTERVALO_MS); continue }
            try {
                openCloudListener(config).use { ouvinte ->
                    falhas = 0
                    while (scope?.isActive == true && cloudSync.isActive && foreground.value &&
                        cloudSync.savedConfig() == config
                    ) {
                        val origens = ouvinte.await(ESCUTA_MS)
                        if (origens.any { it != cloudSync.deviceId }) gatilhos.trySend(Unit)
                    }
                }
            } catch (e: Exception) {
                // Sem ouvinte a checagem periodica continua valendo; so espera para tentar de novo.
                delay(ESPERAS_SEM_CONEXAO[minOf(falhas++, ESPERAS_SEM_CONEXAO.lastIndex)])
            }
        }
    }

    private fun atualizarContagens() {
        val pendentes = runCatching { cloudSync.pendingCount() }.getOrDefault(0)
        val conflitos = runCatching { cloudSync.conflictCount() }.getOrDefault(0)
        _status.update { it.copy(pending = pendentes, conflicts = conflitos) }
    }
}
