package com.example.sonntag.ui.screens.datatransfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonntag.i18n.LocaleController
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
) : ViewModel() {

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
                val rows = preview.rows.filter {
                    when (it.kind) {
                        ChangeKind.NOVO, ChangeKind.ATUALIZA -> true
                        ChangeKind.DIVERGE -> it.uuid in state.acceptedConflicts
                        else -> false
                    }
                }
                val applied = syncService.apply(rows)
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
