package com.example.sonntag.ui.screens.members

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
import androidx.compose.material.icons.outlined.PersonOutline
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
import com.example.sonntag.data.sqldelight.Members
import com.example.sonntag.ui.components.EmptyState
import com.example.sonntag.ui.components.ScreenScaffold
import org.koin.compose.koinInject

@Composable
fun MembersScreenContent() {
    val viewModel = koinInject<MembersViewModel>()
    val state by viewModel.uiState.collectAsState()
    val editingMember = remember { mutableStateOf<Members?>(null) }
    val showFormDialog = remember { mutableStateOf(false) }
    val pendingDeleteId = remember { mutableStateOf<Long?>(null) }

    val filteredMembers = state.members.filter {
        val term = state.search.trim().lowercase()
        term.isBlank() ||
            it.nome.lowercase().contains(term) ||
            it.sobrenome.lowercase().contains(term)
    }

    ScreenScaffold(
        title = "Membros",
        subtitle = "${state.members.size} cadastrados",
        actions = {
            Button(onClick = {
                editingMember.value = null
                showFormDialog.value = true
            }) {
                Text("Novo membro")
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.search,
                onValueChange = viewModel::onSearchChanged,
                label = { Text("Buscar por nome/sobrenome") },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.members.isEmpty() -> {
                EmptyState(
                    icon = Icons.Outlined.PersonOutline,
                    title = "Nenhum membro cadastrado",
                    description = "Adicione membros para escalá-los nas reuniões e atribuições.",
                    actionLabel = "Cadastrar primeiro membro",
                    onAction = {
                        editingMember.value = null
                        showFormDialog.value = true
                    },
                )
            }
            filteredMembers.isEmpty() -> {
                EmptyState(
                    icon = Icons.Outlined.PersonOutline,
                    title = "Sem resultados",
                    description = "Nenhum membro encontrado para o termo \"${state.search.trim()}\".",
                )
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) {
                        Text(
                            "Nome",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Sobrenome",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Ações",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredMembers, key = { it.id }) { member ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    member.nome,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    member.sobrenome,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    TextButton(onClick = {
                                        editingMember.value = member
                                        showFormDialog.value = true
                                    }) {
                                        Text("Editar")
                                    }
                                    TextButton(onClick = { pendingDeleteId.value = member.id }) {
                                        Text("Excluir")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        state.errorMessage?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }

    if (showFormDialog.value) {
        MemberFormDialog(
            initial = editingMember.value,
            onDismiss = { showFormDialog.value = false },
            onConfirm = { nome, sobrenome ->
                val editing = editingMember.value
                if (editing == null) {
                    viewModel.addMember(nome, sobrenome)
                } else {
                    viewModel.updateMember(editing.id, nome, sobrenome)
                }
                showFormDialog.value = false
            },
        )
    }

    if (pendingDeleteId.value != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId.value = null },
            title = { Text("Confirmar exclusão") },
            text = { Text("Deseja realmente remover este membro?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMember(pendingDeleteId.value!!)
                    pendingDeleteId.value = null
                }) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId.value = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun MemberFormDialog(
    initial: Members?,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    val nome = remember(initial?.id) { mutableStateOf(initial?.nome.orEmpty()) }
    val sobrenome = remember(initial?.id) { mutableStateOf(initial?.sobrenome.orEmpty()) }
    val isValid = nome.value.trim().isNotBlank() && sobrenome.value.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Novo membro" else "Editar membro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nome.value,
                    onValueChange = { nome.value = it },
                    label = { Text("Nome") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = sobrenome.value,
                    onValueChange = { sobrenome.value = it },
                    label = { Text("Sobrenome") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(nome.value, sobrenome.value) },
                enabled = isValid,
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}
