package com.example.sonntag.ui.screens.preaching

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sonntag.data.repos.SpotKind
import com.example.sonntag.data.sqldelight.Members
import com.example.sonntag.data.sqldelight.Preaching_groups
import com.example.sonntag.data.sqldelight.Preaching_spots
import com.example.sonntag.i18n.tr
import org.koin.compose.koinInject

/**
 * Cadastros que o calendario de pregacao consome: os pontos (onde o carrinho fica e
 * de onde os grupos saem) e os grupos, com dirigente e ponto de encontro.
 */
@Composable
fun PreachingSetupScreenContent() {
    val viewModel = koinInject<PreachingSetupViewModel>()
    val state by viewModel.uiState.collectAsState()

    var editingSpot by remember { mutableStateOf<Preaching_spots?>(null) }
    var showSpotDialog by remember { mutableStateOf(false) }
    var deletingSpot by remember { mutableStateOf<Preaching_spots?>(null) }

    var editingGroup by remember { mutableStateOf<Preaching_groups?>(null) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var deletingGroup by remember { mutableStateOf<Preaching_groups?>(null) }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader(
            title = tr("Pontos de pregação"),
            description = tr("Onde o carrinho fica e de onde os grupos saem."),
            actionLabel = tr("Novo ponto"),
            onAction = {
                editingSpot = null
                showSpotDialog = true
            },
        )

        if (state.spots.isEmpty()) {
            EmptyLine(tr("Nenhum ponto cadastrado"))
        } else {
            state.spots.forEach { spot ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(spot.nome, style = MaterialTheme.typography.bodyLarge)
                        val detalhe = listOfNotNull(
                            spot.endereco?.takeIf { it.isNotBlank() },
                            tr(spotKindLabel(spot.tipo)),
                        ).joinToString(" · ")
                        Text(
                            detalhe,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = {
                        editingSpot = spot
                        showSpotDialog = true
                    }) { Text(tr("Editar")) }
                    TextButton(onClick = { deletingSpot = spot }) { Text(tr("Excluir")) }
                }
                HorizontalDivider()
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        SectionHeader(
            title = tr("Grupos de pregação"),
            description = tr("Saem no rodapé do programa, na ordem desta lista."),
            actionLabel = tr("Novo grupo"),
            onAction = {
                editingGroup = null
                showGroupDialog = true
            },
        )

        if (state.groups.isEmpty()) {
            EmptyLine(tr("Nenhum grupo cadastrado"))
        } else {
            state.groups.forEachIndexed { index, group ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(group.nome, style = MaterialTheme.typography.bodyLarge)
                        val dirigente = state.members.nomeDe(group.dirigente_id).ifBlank { null }
                        val ponto = state.spots.firstOrNull { it.id == group.spot_id }?.nome
                        val detalhe = listOfNotNull(dirigente, ponto).joinToString(" · ")
                        Text(
                            detalhe.ifBlank { tr("Sem dirigente") },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = { viewModel.moveGroup(group.id, -1) },
                        enabled = index > 0,
                    ) { Text("↑") }
                    TextButton(
                        onClick = { viewModel.moveGroup(group.id, 1) },
                        enabled = index < state.groups.lastIndex,
                    ) { Text("↓") }
                    TextButton(onClick = {
                        editingGroup = group
                        showGroupDialog = true
                    }) { Text(tr("Editar")) }
                    TextButton(onClick = { deletingGroup = group }) { Text(tr("Excluir")) }
                }
                HorizontalDivider()
            }
        }

        state.errorMessage?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(tr(it), color = MaterialTheme.colorScheme.error)
        }
    }

    if (showSpotDialog) {
        SpotFormDialog(
            initial = editingSpot,
            onDismiss = { showSpotDialog = false },
            onConfirm = { nome, endereco, tipo ->
                val editando = editingSpot
                if (editando == null) viewModel.addSpot(nome, endereco, tipo)
                else viewModel.updateSpot(editando.id, nome, endereco, tipo)
                showSpotDialog = false
            },
        )
    }

    if (showGroupDialog) {
        GroupFormDialog(
            initial = editingGroup,
            members = state.members,
            spots = state.spots,
            onDismiss = { showGroupDialog = false },
            onConfirm = { nome, dirigente, auxiliar, spot ->
                val editando = editingGroup
                if (editando == null) viewModel.addGroup(nome, dirigente, auxiliar, spot)
                else viewModel.updateGroup(editando.id, nome, dirigente, auxiliar, spot)
                showGroupDialog = false
            },
        )
    }

    deletingSpot?.let { spot ->
        ConfirmDelete(
            texto = tr("Deseja realmente remover este ponto?"),
            onConfirm = {
                viewModel.deleteSpot(spot.id)
                deletingSpot = null
            },
            onDismiss = { deletingSpot = null },
        )
    }

    deletingGroup?.let { group ->
        ConfirmDelete(
            texto = tr("Deseja realmente remover este grupo?"),
            onConfirm = {
                viewModel.deleteGroup(group.id)
                deletingGroup = null
            },
            onDismiss = { deletingGroup = null },
        )
    }
}

/** Chave de traducao do tipo de ponto guardado no banco. */
private fun spotKindLabel(tipo: String): String = when (tipo) {
    SpotKind.CARRITO.name -> "Carrinho"
    SpotKind.PREDICACION.name -> "Pregação"
    else -> "Carrinho e pregação"
}

@Composable
private fun SectionHeader(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun EmptyLine(texto: String) {
    Text(
        texto,
        modifier = Modifier.padding(vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ConfirmDelete(texto: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Confirmar exclusão")) },
        text = { Text(texto) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(tr("Excluir")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar")) } },
    )
}

@Composable
private fun SpotFormDialog(
    initial: Preaching_spots?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, SpotKind) -> Unit,
) {
    var nome by remember(initial?.id) { mutableStateOf(initial?.nome.orEmpty()) }
    var endereco by remember(initial?.id) { mutableStateOf(initial?.endereco.orEmpty()) }
    var tipo by remember(initial?.id) {
        mutableStateOf(
            initial?.tipo?.let { salvo -> SpotKind.entries.firstOrNull { it.name == salvo } } ?: SpotKind.AMBOS,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) tr("Novo ponto") else tr("Editar ponto")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text(tr("Nome do ponto")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = endereco,
                    onValueChange = { endereco = it },
                    label = { Text(tr("Endereço")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                PickerField(
                    label = tr("Usado em"),
                    value = tr(spotKindLabel(tipo.name)),
                    options = SpotKind.entries.map { it to tr(spotKindLabel(it.name)) },
                    onSelected = { tipo = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(nome, endereco, tipo) }, enabled = nome.isNotBlank()) {
                Text(tr("Salvar"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar")) } },
    )
}

@Composable
private fun GroupFormDialog(
    initial: Preaching_groups?,
    members: List<Members>,
    spots: List<Preaching_spots>,
    onDismiss: () -> Unit,
    onConfirm: (String, Long?, Long?, Long?) -> Unit,
) {
    var nome by remember(initial?.id) { mutableStateOf(initial?.nome.orEmpty()) }
    var dirigente by remember(initial?.id) { mutableStateOf(initial?.dirigente_id) }
    var auxiliar by remember(initial?.id) { mutableStateOf(initial?.auxiliar_id) }
    var spot by remember(initial?.id) { mutableStateOf(initial?.spot_id) }

    val opcoesMembro = members.comoOpcoes()
    val opcoesPonto: List<Pair<Long?, String>> =
        listOf<Pair<Long?, String>>(null to tr("Limpar seleção")) +
            spots.map { it.id as Long? to it.nome }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) tr("Novo grupo") else tr("Editar grupo")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text(tr("Nome do grupo")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                PickerField(
                    label = tr("Dirigente"),
                    value = members.nomeDe(dirigente),
                    options = opcoesMembro,
                    onSelected = { dirigente = it },
                )
                PickerField(
                    label = tr("Auxiliar"),
                    value = members.nomeDe(auxiliar),
                    options = opcoesMembro,
                    onSelected = { auxiliar = it },
                )
                PickerField(
                    label = tr("Ponto de encontro"),
                    value = spots.firstOrNull { it.id == spot }?.nome.orEmpty(),
                    options = opcoesPonto,
                    onSelected = { spot = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(nome, dirigente, auxiliar, spot) },
                enabled = nome.isNotBlank(),
            ) { Text(tr("Salvar")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar")) } },
    )
}
