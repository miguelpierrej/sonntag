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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.sonntag.sync.ImportCategory
import com.example.sonntag.sync.ImportPreview
import com.example.sonntag.sync.IncomingPackage
import com.example.sonntag.sync.category
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
            preview = preview,
            aceitos = state.acceptedRows,
            onToggleRow = viewModel::toggleRow,
            onToggleGroup = viewModel::toggleGroup,
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
        // Pacote que chegou de fora: abre o resumo sem passar pelo seletor.
        LaunchedEffect(IncomingPackage.bytes) {
            IncomingPackage.consumir()?.let(viewModel::openPackageBytes)
        }

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
    preview: ImportPreview,
    aceitos: Set<String>,
    onToggleRow: (IncomingRow, Boolean) -> Unit,
    onToggleGroup: (List<IncomingRow>, Boolean) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    // Um grupo por bloco e categoria: com centenas de linhas, decidir uma a uma seria
    // inviavel — quem quiser desce ao detalhe abrindo o grupo.
    val grupos = remember(preview) {
        preview.rows.mapNotNull { row ->
            val categoria = row.category() ?: return@mapNotNull null
            val secao = SyncSection.entries.firstOrNull { row.table in it.tables }
            Triple(secao, categoria, row)
        }
            .groupBy { it.first to it.second }
            .toList()
            .sortedWith(
                compareBy(
                    { SyncSection.entries.indexOfFirst { s -> s == it.first.first } },
                    { it.first.second.ordinal },
                ),
            )
            .map { (chave, linhas) -> ImportGroup(chave.first, chave.second, linhas.map { it.third }) }
    }
    val abertos = remember { mutableStateOf(emptySet<String>()) }
    val total = aceitos.count { uuid -> preview.rows.any { it.uuid == uuid } }

    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = total > 0) {
                Text(tr("Aplicar {0}", total))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(tr("Cancelar")) } },
        title = { Text(tr("Conferir antes de aplicar")) },
        text = {
            Column(modifier = Modifier.widthIn(max = 560.dp)) {
                Text(
                    tr("Só os registros novos vêm marcados. Atualizações e exclusões mudam o que já existe aqui — marque o que quiser aceitar."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    grupos.forEach { grupo ->
                        item(key = grupo.chave) {
                            GroupHeader(
                                grupo = grupo,
                                aceitos = aceitos,
                                aberto = grupo.chave in abertos.value,
                                onToggle = { onToggleGroup(grupo.rows, it) },
                                onExpand = {
                                    abertos.value = if (grupo.chave in abertos.value) {
                                        abertos.value - grupo.chave
                                    } else {
                                        abertos.value + grupo.chave
                                    }
                                },
                            )
                        }
                        if (grupo.chave in abertos.value) {
                            items(grupo.rows, key = { it.uuid }) { row ->
                                RowLine(row = row, aceito = row.uuid in aceitos, onToggle = onToggleRow)
                            }
                        }
                    }
                }

                if (preview.ignorados > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        tr("{0} ignorados: referência ausente ou duplicata já apagada aqui", preview.ignorados),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

/** Um bloco e uma categoria de mudanca, com as linhas que caem ali. */
private data class ImportGroup(
    val secao: SyncSection?,
    val categoria: ImportCategory,
    val rows: List<IncomingRow>,
) {
    val chave: String get() = "${secao?.id ?: "outros"}/${categoria.name}"
}

@Composable
private fun GroupHeader(
    grupo: ImportGroup,
    aceitos: Set<String>,
    aberto: Boolean,
    onToggle: (Boolean) -> Unit,
    onExpand: () -> Unit,
) {
    val marcadas = grupo.rows.count { it.uuid in aceitos }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = marcadas == grupo.rows.size,
            onCheckedChange = onToggle,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${tr(grupo.secao?.label ?: "Outros")} · ${tr(categoriaLabel(grupo.categoria), grupo.rows.size)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (marcadas in 1 until grupo.rows.size) {
                Text(
                    tr("{0} de {1} marcados", marcadas, grupo.rows.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onExpand) { Text(if (aberto) tr("Fechar") else tr("Revisar")) }
    }
}

@Composable
private fun RowLine(row: IncomingRow, aceito: Boolean, onToggle: (IncomingRow, Boolean) -> Unit) {
    Row(
        modifier = Modifier.padding(start = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = aceito, onCheckedChange = { onToggle(row, it) })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.description,
                style = MaterialTheme.typography.bodySmall,
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

private fun categoriaLabel(categoria: ImportCategory): String = when (categoria) {
    ImportCategory.NOVOS -> "{0} novos"
    ImportCategory.ATUALIZACOES -> "{0} atualizações"
    ImportCategory.EXCLUSOES -> "{0} exclusões"
    ImportCategory.DIVERGENCIAS -> "{0} divergências"
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
