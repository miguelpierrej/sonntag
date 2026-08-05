package com.example.sonntag.ui.screens.datatransfer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sonntag.i18n.tr
import com.example.sonntag.net.LanPeer
import com.example.sonntag.sync.ChangeKind
import com.example.sonntag.sync.IncomingRow
import com.example.sonntag.sync.SyncSection
import com.example.sonntag.sync.requires
import org.koin.compose.koinInject

private val CardMaxWidth = 640.dp

@Composable
fun DataTransferScreenContent() {
    val viewModel = koinInject<DataTransferViewModel>()
    val state by viewModel.uiState.collectAsState()

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text(tr("OK")) } },
            title = { Text(tr("Dados")) },
            text = { Text(message) },
        )
    }

    state.peerAskingCode?.let { peer ->
        PeerCodeDialog(
            peerNome = peer.nome,
            code = state.peerCode,
            error = state.peerCodeError,
            onChange = viewModel::setPeerCode,
            onConfirm = viewModel::syncWithPeer,
            onCancel = viewModel::cancelPeerCode,
        )
    }

    if (state.askPassword) {
        PasswordDialog(
            password = state.importPassword,
            error = state.passwordError,
            onChange = viewModel::setImportPassword,
            onConfirm = viewModel::confirmPassword,
            onCancel = viewModel::cancelImport,
        )
    }

    state.preview?.let { preview ->
        ImportPreviewDialog(
            novos = preview.novos,
            atualizacoes = preview.atualizacoes,
            ignorados = preview.ignorados,
            divergencias = preview.divergencias,
            aceitos = state.acceptedConflicts,
            onToggle = viewModel::toggleConflict,
            onConfirm = viewModel::applyImport,
            onCancel = viewModel::cancelImport,
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ExportCard(
            selected = state.selectedSections,
            protect = state.protectWithPassword,
            password = state.exportPassword,
            enabled = state.canExport,
            busy = state.isBusy,
            onToggleSection = viewModel::toggleSection,
            onToggleProtect = viewModel::setProtectWithPassword,
            onPasswordChange = viewModel::setExportPassword,
            onExport = viewModel::export,
        )
        ImportCard(busy = state.isBusy, onImport = viewModel::pickFileToImport)
        LanCard(
            visible = state.lanVisible,
            myCode = state.myCode,
            peers = state.peers,
            syncingWith = state.syncingWith,
            onToggle = viewModel::toggleLan,
            onPeerClick = viewModel::askPeerCode,
        )
    }
}

@Composable
private fun ExportCard(
    selected: Set<SyncSection>,
    protect: Boolean,
    password: String,
    enabled: Boolean,
    busy: Boolean,
    onToggleSection: (SyncSection, Boolean) -> Unit,
    onToggleProtect: (Boolean) -> Unit,
    onPasswordChange: (String) -> Unit,
    onExport: () -> Unit,
) {
    SectionCard(title = tr("Exportar dados"), subtitle = tr("Escolha o que vai no arquivo")) {
        SyncSection.entries.forEach { section ->
            val required = SyncSection.entries.any { it in selected && section in it.requires }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = section in selected,
                    onCheckedChange = { onToggleSection(section, it) },
                    enabled = !required,
                )
                Column {
                    Text(tr(section.label), style = MaterialTheme.typography.bodyMedium)
                    if (required) {
                        Text(
                            tr("Necessário para os blocos escolhidos"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = protect, onCheckedChange = onToggleProtect)
            Text(tr("Proteger com senha"), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = if (protect) {
                tr("Quem importar precisará da senha. Combine-a por outro caminho, não junto do arquivo.")
            } else {
                tr("Sem senha o arquivo abre em qualquer instalação do app — não protege os nomes.")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (protect) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(tr("Senha")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = { Text(tr("Mínimo de {0} caracteres", MIN_PASSWORD)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onExport, enabled = enabled) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(tr("Exportar arquivo"))
        }
    }
}

@Composable
private fun ImportCard(busy: Boolean, onImport: () -> Unit) {
    SectionCard(
        title = tr("Importar dados"),
        subtitle = tr("Abra um pacote recebido de outra instalação"),
    ) {
        Text(
            tr("Nada é gravado antes de você conferir o resumo das mudanças."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onImport, enabled = !busy) {
            Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(tr("Escolher arquivo"))
        }
    }
}

/**
 * Rede local: enquanto visivel, este aparelho se anuncia e enxerga os outros. A troca
 * e nos dois sentidos, e quem inicia precisa do codigo exibido pelo outro.
 */
@Composable
private fun LanCard(
    visible: Boolean,
    myCode: String,
    peers: List<LanPeer>,
    syncingWith: String?,
    onToggle: (Boolean) -> Unit,
    onPeerClick: (LanPeer) -> Unit,
) {
    SectionCard(
        title = tr("Rede local"),
        subtitle = tr("Trocar dados com quem está na mesma rede"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = visible, onCheckedChange = onToggle)
            Spacer(modifier = Modifier.width(12.dp))
            Text(tr("Ficar visível na rede"), style = MaterialTheme.typography.bodyMedium)
        }

        if (!visible) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                tr("Os dois aparelhos precisam estar visíveis, na mesma rede."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(tr("Seu código"), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = myCode,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            tr("Informe-o a quem for iniciar a troca."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(12.dp))

        if (syncingWith != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(tr("Trocando com {0}...", syncingWith), style = MaterialTheme.typography.bodyMedium)
            }
            return@SectionCard
        }

        if (peers.isEmpty()) {
            Text(
                tr("Procurando aparelhos..."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            peers.forEach { peer ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPeerClick(peer) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(peer.nome, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            peer.host,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(tr("Sincronizar"), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun PeerCodeDialog(
    peerNome: String,
    code: String,
    error: Boolean,
    onChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = code.length == 4) { Text(tr("Sincronizar")) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(tr("Cancelar")) } },
        title = { Text(tr("Código de {0}", peerNome)) },
        text = {
            Column {
                Text(tr("Digite os quatro dígitos que aparecem no outro aparelho."))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = onChange,
                    label = { Text(tr("Código")) },
                    singleLine = true,
                    isError = error,
                    supportingText = if (error) {{ Text(tr("Código incorreto.")) }} else null,
                )
            }
        },
    )
}

@Composable
private fun PasswordDialog(
    password: String,
    error: Boolean,
    onChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = password.isNotEmpty()) { Text(tr("Abrir")) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(tr("Cancelar")) } },
        title = { Text(tr("Arquivo protegido")) },
        text = {
            Column {
                Text(tr("Este pacote foi exportado com senha."))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = onChange,
                    label = { Text(tr("Senha")) },
                    singleLine = true,
                    isError = error,
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = if (error) {
                        { Text(tr("Senha incorreta ou arquivo alterado.")) }
                    } else null,
                )
            }
        },
    )
}

@Composable
private fun ImportPreviewDialog(
    novos: Int,
    atualizacoes: Int,
    ignorados: Int,
    divergencias: List<IncomingRow>,
    aceitos: Set<String>,
    onToggle: (IncomingRow, Boolean) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = { TextButton(onClick = onConfirm) { Text(tr("Aplicar")) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(tr("Cancelar")) } },
        title = { Text(tr("Conferir antes de aplicar")) },
        text = {
            Column(modifier = Modifier.widthIn(max = 560.dp)) {
                Text(tr("{0} registros novos", novos), style = MaterialTheme.typography.bodyMedium)
                Text(tr("{0} atualizações", atualizacoes), style = MaterialTheme.typography.bodyMedium)
                if (ignorados > 0) {
                    Text(
                        tr("{0} ignorados por referência ausente", ignorados),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (divergencias.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        tr("Estes registros mudaram dos dois lados. Marque os que devem vir do arquivo; os demais mantêm o que está aqui."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(divergencias, key = { it.uuid }) { row ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = row.uuid in aceitos,
                                    onCheckedChange = { onToggle(row, it) },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        row.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        tr("aqui: {0} · arquivo: {1}", row.localUpdatedAt.orEmpty(), row.remoteUpdatedAt.orEmpty()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun SectionCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = CardMaxWidth),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}
