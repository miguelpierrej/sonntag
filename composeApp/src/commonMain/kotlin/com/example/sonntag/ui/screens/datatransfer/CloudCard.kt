package com.example.sonntag.ui.screens.datatransfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.example.sonntag.cloud.ConflictSide
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.sonntag.cloud.CloudStartMode
import com.example.sonntag.cloud.CloudState
import com.example.sonntag.cloud.CloudStatus
import com.example.sonntag.i18n.tr
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.koin.compose.koinInject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Sincronizacao pela internet, num PostgreSQL que a propria congregacao providencia
 * (Supabase, Neon ou servidor proprio). As tabelas sao criadas ao conectar.
 */
@Composable
fun CloudCard() {
    val viewModel = koinInject<CloudViewModel>()
    val state by viewModel.uiState.collectAsState()

    if (state.askMode) {
        ModeDialog(onChoose = viewModel::chooseMode, onCancel = viewModel::cancelMode)
    }
    state.conflicts?.let { itens ->
        ConflictsDialog(
            items = itens,
            choices = state.conflictChoices,
            onChoose = viewModel::chooseConflict,
            onChooseAll = viewModel::chooseAllConflicts,
            onApply = viewModel::applyConflicts,
            onCancel = viewModel::closeConflicts,
        )
    }
    if (state.confirmDisconnect) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDisconnect,
            title = { Text(tr("Desconectar da nuvem?")) },
            text = {
                Text(
                    tr(
                        "Os dados continuam neste aparelho, mas deixam de ser sincronizados. " +
                            "Para reconectar, será preciso digitar as senhas de novo.",
                    ),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::disconnect) { Text(tr("Desconectar")) } },
            dismissButton = { TextButton(onClick = viewModel::cancelDisconnect) { Text(tr("Cancelar")) } },
        )
    }

    SectionCard(
        title = tr("Sincronização na nuvem"),
        subtitle = tr("Compartilhar os dados com outros aparelhos pela internet"),
    ) {
        if (state.active) ConnectedContent(state, viewModel) else FormContent(state, viewModel)
    }
}

@Composable
private fun ConnectedContent(state: CloudUiState, viewModel: CloudViewModel) {
    val status = state.status
    val sincronizando = status.state == CloudState.SINCRONIZANDO
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = when (status.state) {
                CloudState.SEM_CONEXAO -> Icons.Outlined.CloudOff
                CloudState.ERRO -> Icons.Outlined.ErrorOutline
                CloudState.SINCRONIZANDO -> Icons.Outlined.CloudSync
                else -> Icons.Outlined.CloudDone
            },
            contentDescription = null,
            tint = when (status.state) {
                CloudState.ERRO -> MaterialTheme.colorScheme.error
                CloudState.SEM_CONEXAO -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(tr("Conectado a {0}", state.host), style = MaterialTheme.typography.bodyMedium)
            Text(
                statusLine(status),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (status.conflicts > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (status.conflicts == 1) tr("1 edição simultânea para revisar")
                else tr("{0} edições simultâneas para revisar", status.conflicts),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::openConflicts) { Text(tr("Revisar")) }
        }
    }
    val detalhes = buildList {
        state.summary?.let { add(it) }
        if (status.pending == 1) add(tr("1 alteração aguardando envio"))
        if (status.pending > 1) add(tr("{0} alterações aguardando envio", status.pending))
        status.lastExchange?.takeIf { state.summary == null }?.let {
            add(tr("Última troca: {0} recebidos, {1} enviados", it.recebidos, it.enviados))
        }
    }
    detalhes.forEach {
        Spacer(modifier = Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall)
    }
    ErrorText(state.error ?: state.statusError)

    Spacer(modifier = Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = viewModel::syncNow, enabled = !sincronizando) {
            if (sincronizando) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(tr("Sincronizar agora"))
        }
        OutlinedButton(onClick = viewModel::askDisconnect) {
            Text(tr("Desconectar"))
        }
    }
}

/** "Sincronizado há 5 s", que anda sozinho enquanto a tela esta aberta. */
@Composable
private fun statusLine(status: CloudStatus): String {
    var agora by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            agora = Clock.System.now()
        }
    }
    val ultima = status.lastSyncAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val quando = ultima?.let { relativo(agora - it) }
    return when (status.state) {
        CloudState.DESLIGADA -> tr("Iniciando...")
        CloudState.SINCRONIZANDO -> tr("Sincronizando...")
        CloudState.SINCRONIZADA -> quando?.let { tr("Sincronizado {0}", it) } ?: tr("Sincronizado")
        CloudState.SEM_CONEXAO -> tr("Sem conexão. As alterações ficam guardadas e sobem quando a conexão voltar.")
        CloudState.ERRO -> quando?.let { tr("Falha ao sincronizar. Última vez {0}", it) } ?: tr("Falha ao sincronizar")
    }
}

@Composable
private fun relativo(d: Duration): String = when {
    d < 10.seconds -> tr("agora mesmo")
    d < 1.minutes -> tr("há {0} s", d.inWholeSeconds)
    d < 1.hours -> tr("há {0} min", d.inWholeMinutes)
    d < 1.days -> tr("há {0} h", d.inWholeHours)
    else -> tr("há {0} dias", d.inWholeDays)
}

@Composable
private fun FormContent(state: CloudUiState, viewModel: CloudViewModel) {
    Text(
        tr("Cole a URL de conexão do PostgreSQL (Supabase, Neon ou servidor próprio) ou preencha os campos."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(12.dp))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.host,
            onValueChange = viewModel::setHost,
            label = { Text(tr("Servidor ou URL de conexão")) },
            singleLine = true,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.port,
                onValueChange = viewModel::setPort,
                label = { Text(tr("Porta")) },
                singleLine = true,
                enabled = !state.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(0.35f),
            )
            OutlinedTextField(
                value = state.database,
                onValueChange = viewModel::setDatabase,
                label = { Text(tr("Banco")) },
                singleLine = true,
                enabled = !state.busy,
                modifier = Modifier.weight(0.65f),
            )
        }
        OutlinedTextField(
            value = state.user,
            onValueChange = viewModel::setUser,
            label = { Text(tr("Usuário")) },
            singleLine = true,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::setPassword,
            label = { Text(tr("Senha do banco")) },
            singleLine = true,
            enabled = !state.busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.passphrase,
            onValueChange = viewModel::setPassphrase,
            label = { Text(tr("Senha da congregação")) },
            supportingText = {
                Text(
                    tr(
                        "Cifra os dados na nuvem. Todos os aparelhos usam a mesma; sem ela " +
                            "ninguém lê os dados, nem quem administra o servidor.",
                    ),
                )
            },
            singleLine = true,
            enabled = !state.busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    ErrorText(state.error)
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = viewModel::connect, enabled = state.canConnect) {
        if (state.busy) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(tr("Conectando..."))
        } else {
            Text(tr("Conectar"))
        }
    }
}

@Composable
private fun ErrorText(error: String?) {
    error ?: return
    Spacer(modifier = Modifier.height(12.dp))
    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

/**
 * Primeira conexao com os dois lados cheios. Nao e uma revisao linha a linha: numa
 * base compartilhada, recusar uma linha nao a tira dos outros aparelhos.
 */
@Composable
private fun ModeDialog(onChoose: (CloudStartMode) -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(tr("A nuvem já tem dados")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Este aparelho também tem. Como juntar?"))
                Text(
                    tr("Usar os dados da nuvem: o que só existe neste aparelho é descartado. Recomendado para quem está entrando numa nuvem já em uso."),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    tr("Combinar: junta os dois lados e, em cada registro, fica a versão mais recente. Se as bases foram criadas separadamente, os membros podem ficar duplicados."),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onCancel) { Text(tr("Cancelar")) }
                TextButton(onClick = { onChoose(CloudStartMode.COMBINAR) }) { Text(tr("Combinar")) }
                Button(onClick = { onChoose(CloudStartMode.USAR_NUVEM) }) { Text(tr("Usar os dados da nuvem")) }
            }
        },
    )
}

/**
 * Revisao das edicoes simultaneas: para cada registro, as duas versoes com os valores de
 * cada campo que difere, e quem tem cada uma. A marcada de inicio e a que esta valendo.
 */
@Composable
private fun ConflictsDialog(
    items: List<ConflictItem>,
    choices: Map<Long, ConflictSide>,
    onChoose: (Long, ConflictSide) -> Unit,
    onChooseAll: (ConflictSide) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(tr("Edições simultâneas")) },
        text = {
            Column(modifier = Modifier.widthIn(max = 560.dp)) {
                if (items.isEmpty()) {
                    Text(tr("Nada a revisar: as versões já coincidem."))
                    return@Column
                }
                Text(
                    tr("Dois aparelhos mudaram o mesmo registro. Escolha a versão que deve ficar em todos."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (items.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onChooseAll(ConflictSide.LOCAL) }) { Text(tr("Todas deste dispositivo")) }
                        TextButton(onClick = { onChooseAll(ConflictSide.REMOTA) }) { Text(tr("Todas do outro")) }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(items, key = { it.id }) { item ->
                        val escolha = choices[item.id]
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            item.section?.let {
                                Text(tr(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(item.diff.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            VersionOption(
                                label = emUso(item.localLabel, item.inUse == ConflictSide.LOCAL),
                                values = item.diff.fields.map { it.label to it.local },
                                selected = escolha == ConflictSide.LOCAL,
                                onSelect = { onChoose(item.id, ConflictSide.LOCAL) },
                            )
                            VersionOption(
                                label = emUso(item.remoteLabel, item.inUse == ConflictSide.REMOTA),
                                values = item.diff.fields.map { it.label to it.remote },
                                selected = escolha == ConflictSide.REMOTA,
                                onSelect = { onChoose(item.id, ConflictSide.REMOTA) },
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text(tr("Aplicar")) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(tr("Cancelar")) } },
    )
}

@Composable
private fun emUso(label: String, emUso: Boolean): String = if (emUso) tr("{0} · em uso", label) else label
