package com.example.sonntag.ui.screens.datatransfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonntag.data.repos.SettingsRepository
import com.example.sonntag.data.repos.SyncPeersRepository
import com.example.sonntag.i18n.LocaleController
import com.example.sonntag.net.LanException
import com.example.sonntag.net.LanLog
import com.example.sonntag.net.LanFailure
import com.example.sonntag.net.LanPeer
import com.example.sonntag.net.LanSync
import com.example.sonntag.net.LanSyncConfig
import com.example.sonntag.net.createLanSync
import com.example.sonntag.sync.SyncStamp
import com.example.sonntag.sync.ChangeKind
import com.example.sonntag.sync.ImportPreview
import com.example.sonntag.sync.IncomingRow
import com.example.sonntag.sync.PACKAGE_EXTENSION
import com.example.sonntag.sync.SyncFileService
import com.example.sonntag.sync.SyncSection
import com.example.sonntag.sync.SyncService
import com.example.sonntag.sync.requires
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class DataTransferUiState(
    val selectedSections: Set<SyncSection> = setOf(SyncSection.MEMBROS, SyncSection.REUNIOES),
    val protectWithPassword: Boolean = false,
    val exportPassword: String = "",
    val isBusy: Boolean = false,
    val message: String? = null,
    // Importacao
    val preview: ImportPreview? = null,
    val pendingBytes: ByteArray? = null,
    val askPassword: Boolean = false,
    val importPassword: String = "",
    val passwordError: Boolean = false,
    /** Divergencias que o usuario decidiu aceitar do arquivo. */
    val acceptedConflicts: Set<String> = emptySet(),
    // Rede local
    val lanVisible: Boolean = false,
    val myCode: String = "",
    val peers: List<LanPeer> = emptyList(),
    /** Aparelho escolhido, aguardando o codigo dele. */
    val peerAskingCode: LanPeer? = null,
    val peerCode: String = "",
    val peerCodeError: Boolean = false,
    val syncingWith: String? = null,
) {
    val canExport: Boolean
        get() = selectedSections.isNotEmpty() && !isBusy &&
            (!protectWithPassword || exportPassword.length >= MIN_PASSWORD)
}

const val MIN_PASSWORD = 4

class DataTransferViewModel(
    private val syncService: SyncService,
    private val fileService: SyncFileService,
    private val localeController: LocaleController,
    private val peersRepository: SyncPeersRepository,
    private val settingsRepository: SettingsRepository,
    private val stamp: SyncStamp,
) : ViewModel() {

    /** Todas as secoes: numa troca pela rede nao faz sentido escolher parte. */
    private val todasSecoes = SyncSection.entries.toList()

    private val lan: LanSync by lazy {
        createLanSync(
            LanSyncConfig(
                deviceId = stamp.deviceId,
                deviceName = {
                    settingsRepository.getSettingsOnce()?.nome?.takeIf { it.isNotBlank() }
                        ?: localeController.translator("Aparelho")
                },
                buildPackage = { since -> syncService.buildPackage(todasSecoes, null, since) },
                lastSyncWith = { peersRepository.lastSyncAt(it) },
                onPackageReceived = { peerId, peerNome, bytes -> receberDaRede(peerId, peerNome, bytes) },
            ),
        )
    }

    private val _uiState = MutableStateFlow(DataTransferUiState())
    val uiState: StateFlow<DataTransferUiState> = _uiState.asStateFlow()

    // ─── Selecao ─────────────────────────────────────────────────────────────

    /** Marcar um bloco puxa junto o que ele precisa para fazer sentido do outro lado. */
    fun toggleSection(section: SyncSection, checked: Boolean) {
        val current = _uiState.value.selectedSections
        val updated = if (checked) {
            current + section + section.requires
        } else {
            // Nao remove um bloco do qual outro marcado depende.
            if (current.any { section in it.requires }) current else current - section
        }
        _uiState.value = _uiState.value.copy(selectedSections = updated)
    }

    fun setProtectWithPassword(value: Boolean) {
        _uiState.value = _uiState.value.copy(protectWithPassword = value, exportPassword = "")
    }

    fun setExportPassword(value: String) {
        _uiState.value = _uiState.value.copy(exportPassword = value)
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    // ─── Exportacao ──────────────────────────────────────────────────────────

    fun export() {
        val state = _uiState.value
        if (!state.canExport) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = state.copy(isBusy = true, message = null)
            val t = localeController.translator
            try {
                val bytes = syncService.buildPackage(
                    sections = SyncSection.entries.filter { it in state.selectedSections },
                    password = state.exportPassword.takeIf { state.protectWithPassword },
                )
                val saved = fileService.savePackage(
                    defaultName = defaultFileName(),
                    dialogTitle = t("Salvar pacote de dados"),
                    filterLabel = t("Pacote do Sonntag"),
                    bytes = bytes,
                )
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    message = saved?.let { t("Pacote salvo em {0}", it) },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    message = t("Erro ao exportar: {0}", e.message),
                )
            }
        }
    }

    private fun defaultFileName(): String {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return "sonntag-$today.$PACKAGE_EXTENSION"
    }

    // ─── Rede local ──────────────────────────────────────────────────────────

    /** Liga/desliga o anuncio na rede. Enquanto ligado, outros aparelhos nos veem. */
    fun toggleLan(visible: Boolean) {
        if (visible) {
            lan.start(viewModelScope)
            _uiState.value = _uiState.value.copy(lanVisible = true, myCode = lan.myCode)
            viewModelScope.launch {
                lan.peers.collect { lista -> _uiState.value = _uiState.value.copy(peers = lista) }
            }
        } else {
            lan.stop()
            _uiState.value = _uiState.value.copy(lanVisible = false, peers = emptyList(), myCode = "")
        }
    }

    fun askPeerCode(peer: LanPeer) {
        _uiState.value = _uiState.value.copy(peerAskingCode = peer, peerCode = "", peerCodeError = false)
    }

    fun setPeerCode(value: String) {
        _uiState.value = _uiState.value.copy(peerCode = value.filter { it.isDigit() }.take(4), peerCodeError = false)
    }

    fun cancelPeerCode() {
        _uiState.value = _uiState.value.copy(peerAskingCode = null, peerCode = "")
    }

    /** Troca com o aparelho escolhido; o resumo do que chegou aparece em seguida. */
    fun syncWithPeer() {
        val state = _uiState.value
        val peer = state.peerAskingCode ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = state.copy(peerAskingCode = null, syncingWith = peer.nome)
            val t = localeController.translator
            try {
                val bytes = lan.syncWith(peer, state.peerCode)
                peersRepository.remember(peer.deviceId, peer.nome, stamp.now())
                val preview = syncService.preview(bytes, null)
                _uiState.value = _uiState.value.copy(
                    syncingWith = null,
                    preview = preview,
                    acceptedConflicts = emptySet(),
                    message = if (preview != null && preview.rows.isEmpty()) {
                        t("Nada novo de {0}.", peer.nome)
                    } else null,
                )
            } catch (e: LanException) {
                _uiState.value = _uiState.value.copy(
                    syncingWith = null,
                    peerAskingCode = if (e.failure == LanFailure.CODIGO_INCORRETO) peer else null,
                    peerCodeError = e.failure == LanFailure.CODIGO_INCORRETO,
                    message = when (e.failure) {
                        LanFailure.CODIGO_INCORRETO -> null
                        else -> t("Não foi possível falar com {0}.", peer.nome)
                    },
                )
            } catch (e: Exception) {
                // Gravar o parceiro ou ler o pacote tambem podem falhar; sem isto a
                // falha subia pelo escopo e derrubava a tela sem dizer nada.
                LanLog.e("troca com ${peer.nome} falhou depois da rede", e)
                _uiState.value = _uiState.value.copy(
                    syncingWith = null,
                    message = t("Não foi possível falar com {0}.", peer.nome),
                )
            }
        }
    }

    /** Pacote que chegou de outro aparelho, sem termos iniciado. */
    private suspend fun receberDaRede(peerId: String, peerNome: String, bytes: ByteArray) {
        val preview = syncService.preview(bytes, null)
        if (preview == null) {
            LanLog.e("nao consegui ler o pacote de $peerNome (${bytes.size} bytes)")
            return
        }
        peersRepository.remember(peerId, peerNome, stamp.now())
        _uiState.value = _uiState.value.copy(
            preview = preview.takeIf { it.rows.isNotEmpty() },
            acceptedConflicts = emptySet(),
            message = if (preview.rows.isEmpty()) {
                localeController.translator("Nada novo de {0}.", peerNome)
            } else null,
        )
    }

    override fun onCleared() {
        lan.stop()
        super.onCleared()
    }

    // ─── Importacao ──────────────────────────────────────────────────────────

    fun pickFileToImport() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isBusy = true, message = null)
            val t = localeController.translator
            try {
                val bytes = fileService.openPackage(
                    t("Selecionar pacote de dados"),
                    t("Pacote do Sonntag"),
                )
                if (bytes == null) {
                    _uiState.value = _uiState.value.copy(isBusy = false)
                    return@launch
                }
                val header = syncService.readHeader(bytes)
                if (header == null) {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        message = t("Este arquivo não é um pacote do Sonntag."),
                    )
                    return@launch
                }
                if (header.protected) {
                    // Sem a senha nem da para listar o que ha dentro.
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        pendingBytes = bytes,
                        askPassword = true,
                        importPassword = "",
                        passwordError = false,
                    )
                } else {
                    buildPreview(bytes, null)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    message = t("Erro ao importar: {0}", e.message),
                )
            }
        }
    }

    fun setImportPassword(value: String) {
        _uiState.value = _uiState.value.copy(importPassword = value, passwordError = false)
    }

    fun confirmPassword() {
        val state = _uiState.value
        val bytes = state.pendingBytes ?: return
        viewModelScope.launch(Dispatchers.IO) {
            buildPreview(bytes, state.importPassword)
        }
    }

    private fun buildPreview(bytes: ByteArray, password: String?) {
        val preview = syncService.preview(bytes, password)
        if (preview == null) {
            _uiState.value = _uiState.value.copy(isBusy = false, askPassword = true, passwordError = true)
            return
        }
        _uiState.value = _uiState.value.copy(
            isBusy = false,
            askPassword = false,
            pendingBytes = null,
            preview = preview,
            // Por padrao, divergencia nenhuma e aceita: o que esta aqui prevalece.
            acceptedConflicts = emptySet(),
        )
    }

    fun toggleConflict(row: IncomingRow, accept: Boolean) {
        val current = _uiState.value.acceptedConflicts
        _uiState.value = _uiState.value.copy(
            acceptedConflicts = if (accept) current + row.uuid else current - row.uuid,
        )
    }

    fun cancelImport() {
        _uiState.value = _uiState.value.copy(
            preview = null, pendingBytes = null, askPassword = false,
            importPassword = "", passwordError = false, acceptedConflicts = emptySet(),
        )
    }

    fun applyImport() {
        val state = _uiState.value
        val preview = state.preview ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = state.copy(isBusy = true)
            val t = localeController.translator
            try {
                val applied = syncService.apply(preview, state.acceptedConflicts)
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    preview = null,
                    acceptedConflicts = emptySet(),
                    message = t("Importação concluída: {0} registros aplicados.", applied),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    message = t("Erro ao importar: {0}", e.message),
                )
            }
        }
    }
}
