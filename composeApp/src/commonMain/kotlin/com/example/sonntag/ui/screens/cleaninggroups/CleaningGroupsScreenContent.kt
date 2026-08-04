package com.example.sonntag.ui.screens.cleaninggroups

import com.example.sonntag.i18n.tr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GroupWork
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sonntag.data.sqldelight.Cleaning_groups
import com.example.sonntag.ui.components.EmptyState
import org.koin.compose.koinInject

@Composable
fun CleaningGroupsScreenContent() {
    val viewModel = koinInject<CleaningGroupsViewModel>()
    val state by viewModel.uiState.collectAsState()
    val editingGroup = remember { mutableStateOf<Cleaning_groups?>(null) }
    val showFormDialog = remember { mutableStateOf(false) }
    val pendingDeleteId = remember { mutableStateOf<Long?>(null) }

    val filteredGroups = state.groups.filter {
        val term = state.search.trim().lowercase()
        term.isBlank() || it.nome.lowercase().contains(term)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.search,
                onValueChange = viewModel::onSearchChanged,
                label = { Text(tr("Buscar grupo")) },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = {
                editingGroup.value = null
                showFormDialog.value = true
            }) {
                Text(tr("Novo grupo"))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.groups.isEmpty() -> {
                EmptyState(
                    icon = Icons.Outlined.GroupWork,
                    title = tr("Nenhum grupo de limpeza"),
                    description = tr("Crie grupos para escalar os responsáveis pela limpeza semanal."),
                    actionLabel = tr("Criar primeiro grupo"),
                    onAction = {
                        editingGroup.value = null
                        showFormDialog.value = true
                    },
                )
            }
            filteredGroups.isEmpty() -> {
                EmptyState(
                    icon = Icons.Outlined.GroupWork,
                    title = tr("Sem resultados"),
                    description = "${tr("Nenhum grupo encontrado para o termo")} \"${state.search.trim()}\".",
                )
            }
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Text(tr("Nome"),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(tr("Ações"),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredGroups, key = { it.id }) { group ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                group.nome,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TextButton(onClick = {
                                    editingGroup.value = group
                                    showFormDialog.value = true
                                }) {
                                    Text(tr("Editar"))
                                }
                                TextButton(onClick = { pendingDeleteId.value = group.id }) {
                                    Text(tr("Excluir"))
                                }
                            }
                        }
                    }
                }
            }
        }

        state.errorMessage?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(tr(it), color = MaterialTheme.colorScheme.error)
        }
    }

    if (showFormDialog.value) {
        GroupFormDialog(
            initial = editingGroup.value,
            onDismiss = { showFormDialog.value = false },
            onConfirm = { nome ->
                val editing = editingGroup.value
                if (editing == null) {
                    viewModel.addGroup(nome)
                } else {
                    viewModel.updateGroup(editing.id, nome)
                }
                showFormDialog.value = false
            },
        )
    }

    if (pendingDeleteId.value != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId.value = null },
            title = { Text(tr("Confirmar exclusão")) },
            text = { Text(tr("Deseja realmente remover este grupo?")) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup(pendingDeleteId.value!!)
                    pendingDeleteId.value = null
                }) {
                    Text(tr("Excluir"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId.value = null }) {
                    Text(tr("Cancelar"))
                }
            },
        )
    }
}

@Composable
private fun GroupFormDialog(
    initial: Cleaning_groups?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val nome = remember(initial?.id) { mutableStateOf(initial?.nome.orEmpty()) }
    val isValid = nome.value.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) tr("Novo grupo") else tr("Editar grupo")) },
        text = {
            OutlinedTextField(
                value = nome.value,
                onValueChange = { nome.value = it },
                label = { Text(tr("Nome do grupo")) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(nome.value) },
                enabled = isValid,
            ) {
                Text(tr("Salvar"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Cancelar"))
            }
        },
    )
}
