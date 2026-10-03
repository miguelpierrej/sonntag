package com.example.sonntag.ui.screens.datatransfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonntag.cloud.CloudAutoSync
import com.example.sonntag.cloud.CloudConfig
import com.example.sonntag.cloud.CloudConnectionFile
import com.example.sonntag.cloud.CloudException
import com.example.sonntag.cloud.CloudFailure
import com.example.sonntag.cloud.CloudProbe
import com.example.sonntag.cloud.CloudStartMode
import com.example.sonntag.cloud.CloudState
import com.example.sonntag.cloud.CloudStatus
import com.example.sonntag.cloud.CloudSync
import com.example.sonntag.cloud.CloudSyncResult
import com.example.sonntag.cloud.ConflictSide
import com.example.sonntag.cloud.CONNECTION_EXTENSION
import com.example.sonntag.cloud.deviceLabel
import com.example.sonntag.sync.RecordDiff
import com.example.sonntag.sync.SyncDescriber
import com.example.sonntag.sync.SyncSection
import com.example.sonntag.sync.SyncStore
import com.example.sonntag.i18n.LocaleController
import com.example.sonntag.sync.SyncFileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CloudUiState(
    val active: Boolean = false,
    val host: String = "",
    val port: String = CloudConfig.DEFAULT_PORT.toString(),
    val database: String = CloudConfig.DEFAULT_DATABASE,
    val user: String = "",
    val password: String = "",
    val passphrase: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    /** Resumo da primeira sincronizacao, logo depois de conectar. */
    val summary: String? = null,
    /** Andamento da sincronizacao automatica. */
    val status: CloudStatus = CloudStatus(),
    /** Mensagem do ultimo erro da sincronizacao automatica, ja traduzida. */
    val statusError: String? = null,
    /** Conectou numa nuvem com dados, e aqui tambem ha: falta escolher como juntar. */
    val askMode: Boolean = false,
    val confirmDisconnect: Boolean = false,
    /** Conflitos abertos para revisao; null com o dialogo fechado. */
    val conflicts: List<ConflictItem>? = null,
    /** Versao escolhida em cada conflito (por id). */
    val conflictChoices: Map<Long, ConflictSide> = emptyMap(),
    /** Dialogo pedindo a senha da congregacao para o arquivo de conexao; null fechado. */
    val connectionPrompt: ConnectionPrompt? = null,
    val promptPassphrase: String = "",
    val promptBusy: Boolean = false,
    val promptError: String? = null,
    /** Aviso de sucesso fora do fluxo de conexao ("Arquivo salvo em ..."). */
    val notice: String? = null,
)
 {
    /** O campo de servidor contem uma URL completa: porta, banco e usuario vem dela. */
    val url: CloudConfig?
        get() = CloudConfig.fromUri(host)

    val canConnect: Boolean
        get() = !busy && host.isNotBlank() && passphrase.length >= MIN_PASSWORD &&
            // Com a URL no campo, usuario e senha vem dela.
            url.let { u ->
                if (u != null) u.password.isNotEmpty() || password.isNotEmpty()
                else user.isNotBlank() && password.isNotEmpty() && port.toIntOrNull() != null
            }
}

enum class ConnectionPrompt { EXPORTAR, IMPORTAR }

/** Um conflito pronto para a tela: o registro, os campos que diferem e quem tem cada versao. */
data class ConflictItem(
    val id: Long,
    val section: String?,
    val diff: RecordDiff,
    val localLabel: String,
    val remoteLabel: String,
    val inUse: ConflictSide?,
)

/** Cartao "Sincronização na nuvem" da tela Dados. */
class CloudViewModel(
    private val cloudSync: CloudSync,
    private val autoSync: CloudAutoSync,
    private val localeController: LocaleController,
    private val store: SyncStore,
    private val fileService: SyncFileService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloudUiState())
    val uiState: StateFlow<CloudUiState> = _uiState.asStateFlow()

    /** Guardado entre conectar e escolher como juntar. */
    private var pending: Pair<CloudConfig, CloudProbe>? = null

    /** Arquivo de conexao escolhido, esperando a senha da congregacao. */
    private var pendingFile: ByteArray? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = cloudSync.savedConfig()
            _uiState.update {
                it.copy(
                    active = cloudSync.isActive,
                    host = saved?.host.orEmpty(),
                    port = (saved?.port ?: CloudConfig.DEFAULT_PORT).toString(),
                    database = saved?.database ?: CloudConfig.DEFAULT_DATABASE,
                    user = saved?.user.orEmpty(),
                )
            }
        }
        viewModelScope.launch {
            autoSync.status.collect { status ->
                _uiState.update {
                    it.copy(
                        active = cloudSync.isActive,
                        status = status,
                        statusError = status.failure
                            ?.takeIf { status.state == CloudState.ERRO }
                            ?.let { f -> failureMessage(CloudException(f), status.failureDetail) },
                    )
                }
            }
        }
    }

    // ─── Formulario ──────────────────────────────────────────────────────────

    /**
     * O campo guarda exatamente o que foi colado ou digitado; a URL so e desmontada ao
     * conectar. Reescrever o campo durante a edicao quebrava no celular: o teclado
     * entrega o texto em pedacos, apaga e corrige palavras, e "…@a" ja parece URL.
     */
    fun setHost(value: String) = _uiState.update { it.copy(host = value, error = null) }

    private fun applyUri(state: CloudUiState): CloudUiState {
        val parsed = state.url ?: return state
        return state.copy(
            host = parsed.host,
            port = parsed.port.toString(),
            database = parsed.database,
            user = parsed.user.ifEmpty { state.user },
            password = parsed.password.ifEmpty { state.password },
        )
    }

    fun setPort(value: String) = _uiState.update { it.copy(port = value.filter(Char::isDigit).take(5), error = null) }
    fun setDatabase(value: String) = _uiState.update { it.copy(database = value.trim(), error = null) }
    fun setUser(value: String) = _uiState.update { it.copy(user = value.trim(), error = null) }
    fun setPassword(value: String) = _uiState.update { it.copy(password = value, error = null) }
    fun setPassphrase(value: String) = _uiState.update { it.copy(passphrase = value, error = null) }
    fun dismissSummary() = _uiState.update { it.copy(summary = null) }

    // ─── Conexao ─────────────────────────────────────────────────────────────

    fun connect() {
        val state = applyUri(_uiState.value).also { novo -> _uiState.update { novo } }
        if (!state.canConnect) return
        val config = CloudConfig(
            host = state.host.trim(),
            port = state.port.toInt(),
            database = state.database.ifBlank { CloudConfig.DEFAULT_DATABASE },
            user = state.user,
            password = state.password,
        )
        launchCloud {
            val probe = cloudSync.probe(config, state.passphrase)
            if (probe.cloudHasData && probe.localHasData) {
                pending = config to probe
                _uiState.update { it.copy(busy = false, askMode = true) }
            } else {
                // Um dos lados esta vazio: nao ha o que decidir.
                finish(cloudSync.activate(config, probe, CloudStartMode.COMBINAR))
            }
        }
    }

    fun chooseMode(mode: CloudStartMode) {
        val (config, probe) = pending ?: return
        pending = null
        _uiState.update { it.copy(askMode = false) }
        launchCloud { finish(cloudSync.activate(config, probe, mode)) }
    }

    fun cancelMode() {
        pending = null
        _uiState.update { it.copy(askMode = false) }
    }

    fun syncNow() {
        _uiState.update { it.copy(summary = null, error = null, notice = null) }
        autoSync.requestNow()
    }

    fun askDisconnect() = _uiState.update { it.copy(confirmDisconnect = true) }
    fun cancelDisconnect() = _uiState.update { it.copy(confirmDisconnect = false) }

    fun disconnect() {
        _uiState.update {
            it.copy(active = false, confirmDisconnect = false, password = "", passphrase = "", summary = null)
        }
        viewModelScope.launch(Dispatchers.IO) { cloudSync.disconnect() }
    }

    // ─── Arquivo de conexao ──────────────────────────────────────────────────

    fun askExportConnection() = _uiState.update {
        it.copy(connectionPrompt = ConnectionPrompt.EXPORTAR, promptPassphrase = "", promptError = null, notice = null)
    }

    fun importConnection() {
        val t = localeController.translator
        _uiState.update { it.copy(error = null, notice = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = try {
                fileService.openPackage(t("Abrir conexão da nuvem"), t("Conexão do Sonntag"), CONNECTION_EXTENSION)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = t("Erro ao abrir o arquivo: {0}", e.message)) }
                return@launch
            } ?: return@launch
            if (!CloudConnectionFile.isConnectionFile(bytes)) {
                _uiState.update { it.copy(error = t("Este arquivo não é uma conexão da nuvem do Sonntag.")) }
                return@launch
            }
            pendingFile = bytes
            _uiState.update {
                it.copy(connectionPrompt = ConnectionPrompt.IMPORTAR, promptPassphrase = "", promptError = null)
            }
        }
    }

    fun setPromptPassphrase(value: String) = _uiState.update { it.copy(promptPassphrase = value, promptError = null) }

    fun cancelPrompt() {
        pendingFile = null
        _uiState.update { it.copy(connectionPrompt = null, promptPassphrase = "", promptError = null) }
    }

    fun confirmPrompt() {
        val state = _uiState.value
        val passphrase = state.promptPassphrase
        if (passphrase.isEmpty() || state.promptBusy) return
        when (state.connectionPrompt) {
            ConnectionPrompt.EXPORTAR -> exportConnection(passphrase)
            ConnectionPrompt.IMPORTAR -> openConnection(passphrase)
            null -> Unit
        }
    }

    private fun exportConnection(passphrase: String) {
        val t = localeController.translator
        _uiState.update { it.copy(promptBusy = true, promptError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = cloudSync.connectionFile(passphrase)
                // Fecha antes do seletor de arquivo, que abre por cima.
                _uiState.update { it.copy(connectionPrompt = null, promptPassphrase = "", promptBusy = false) }
                val salvo = fileService.savePackage(
                    defaultName = "conexao-nuvem.$CONNECTION_EXTENSION",
                    dialogTitle = t("Salvar conexão da nuvem"),
                    filterLabel = t("Conexão do Sonntag"),
                    bytes = bytes,
                    extension = CONNECTION_EXTENSION,
                )
                _uiState.update { it.copy(notice = salvo?.let { c -> t("Conexão salva em {0}", c) }) }
            } catch (e: CloudException) {
                _uiState.update { it.copy(promptBusy = false, promptError = failureMessage(e)) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(connectionPrompt = null, promptBusy = false, error = t("Erro ao exportar: {0}", e.message))
                }
            }
        }
    }

    /** Preenche o formulario com o arquivo e conecta de uma vez: e o que quem importa quer. */
    private fun openConnection(passphrase: String) {
        val bytes = pendingFile ?: return
        val config = cloudSync.readConnectionFile(bytes, passphrase)
        if (config == null) {
            _uiState.update {
                it.copy(promptError = localeController.translator("A senha não abre este arquivo."))
            }
            return
        }
        pendingFile = null
        _uiState.update {
            it.copy(
                connectionPrompt = null,
                promptPassphrase = "",
                host = config.host,
                port = config.port.toString(),
                database = config.database,
                user = config.user,
                password = config.password,
                passphrase = passphrase,
            )
        }
        connect()
    }

    // ─── Conflitos ───────────────────────────────────────────────────────────

    fun openConflicts() {
        viewModelScope.launch(Dispatchers.IO) {
            val t = localeController.translator
            val lista = cloudSync.conflicts()
            val describer = SyncDescriber(store, t)
            val itens = lista.map { c ->
                ConflictItem(
                    id = c.id,
                    section = SyncSection.entries.firstOrNull { c.table in it.tables }?.label,
                    diff = describer.diff(c.table, c.local, c.remote),
                    localLabel = t("Este dispositivo ({0})", deviceLabel()),
                    remoteLabel = cloudSync.deviceName(c.remoteDevice) ?: t("Outro dispositivo"),
                    inUse = c.inUse,
                )
            }
            // Os que ja nao tem diferenca (alguem igualou depois) nao pedem decisao.
            val (semDiferenca, abertos) = itens.partition { it.diff.fields.isEmpty() }
            if (semDiferenca.isNotEmpty()) {
                cloudSync.resolveConflicts(semDiferenca.associate { it.id to ConflictSide.LOCAL })
            }
            _uiState.update { st ->
                st.copy(
                    conflicts = abertos,
                    // Comeca pela versao que ja esta valendo: confirmar sem mexer nao muda nada.
                    conflictChoices = abertos.associate { it.id to (it.inUse ?: ConflictSide.REMOTA) },
                )
            }
        }
    }

    fun chooseConflict(id: Long, side: ConflictSide) =
        _uiState.update { it.copy(conflictChoices = it.conflictChoices + (id to side)) }

    fun chooseAllConflicts(side: ConflictSide) =
        _uiState.update { st -> st.copy(conflictChoices = st.conflicts.orEmpty().associate { it.id to side }) }

    fun closeConflicts() = _uiState.update { it.copy(conflicts = null, conflictChoices = emptyMap()) }

    fun applyConflicts() {
        val escolhas = _uiState.value.conflictChoices
        closeConflicts()
        viewModelScope.launch(Dispatchers.IO) {
            cloudSync.resolveConflicts(escolhas)
            autoSync.requestNow()
        }
    }

    // ─── Auxiliares ──────────────────────────────────────────────────────────

    private fun launchCloud(block: suspend () -> Unit) {
        _uiState.update { it.copy(busy = true, error = null, summary = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: CloudException) {
                _uiState.update { it.copy(busy = false, error = failureMessage(e)) }
            } catch (e: Exception) {
                val t = localeController.translator
                _uiState.update { it.copy(busy = false, error = t("Erro inesperado: {0}", e.message)) }
            }
        }
    }

    private fun finish(result: CloudSyncResult) {
        val t = localeController.translator
        val partes = buildList {
            add(t("{0} recebidos, {1} enviados.", result.recebidos, result.enviados))
            if (result.conflitos > 0) {
                add(t("{0} edições simultâneas: ficou a mais recente.", result.conflitos))
            }
            if (result.ignorados > 0) add(t("{0} registros ignorados.", result.ignorados))
        }
        _uiState.update {
            it.copy(
                busy = false,
                active = true,
                password = "",
                passphrase = "",
                summary = partes.joinToString(" "),
            )
        }
    }

    private fun failureMessage(e: CloudException, detail: String? = e.message): String {
        val t = localeController.translator
        return when (e.failure) {
            CloudFailure.SEM_CONEXAO -> t("Não foi possível alcançar o servidor. Confira o endereço, a porta e a internet.")
            CloudFailure.CREDENCIAIS -> t("Usuário ou senha do banco incorretos.")
            CloudFailure.BANCO_INEXISTENTE -> t("O banco de dados informado não existe no servidor.")
            CloudFailure.SEM_PERMISSAO -> t("Este usuário não tem permissão para criar tabelas no banco.")
            CloudFailure.VERSAO_NOVA -> t("Estes dados foram criados por uma versão mais nova do Sonntag. Atualize o aplicativo.")
            CloudFailure.SENHA_CONGREGACAO -> t("A senha da congregação não confere com a usada nesta nuvem.")
            CloudFailure.NAO_CONFIGURADA -> t("A nuvem não está configurada. Conecte-se de novo.")
            CloudFailure.NUVEM_TROCADA -> t("Os dados da nuvem foram apagados ou trocados. Desconecte e conecte-se de novo.")
            CloudFailure.ERRO -> t("Erro no servidor: {0}", detail)
        }
    }
}
