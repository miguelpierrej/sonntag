package com.example.sonntag.ui.screens.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sonntag.domain.usecases.CongregationEvent
import com.example.sonntag.domain.usecases.EventType
import com.example.sonntag.i18n.LocalT
import com.example.sonntag.i18n.tr
import com.example.sonntag.ui.components.EmptyState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import org.koin.compose.koinInject

@Composable
fun EventsScreenContent() {
    val viewModel = koinInject<EventsViewModel>()
    val state by viewModel.uiState.collectAsState()
    val editing = remember { mutableStateOf<CongregationEvent?>(null) }
    val showForm = remember { mutableStateOf(false) }
    val pendingDeleteId = remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                tr("Assembleias, congressos e comemorações substituem as reuniões da semana."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            Button(onClick = {
                editing.value = null
                showForm.value = true
            }) {
                Text(tr("Novo evento"))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.events.isEmpty() -> {
                EmptyState(
                    icon = Icons.Outlined.Event,
                    title = tr("Nenhum evento cadastrado"),
                    description = tr("Cadastre assembleias, congressos e comemorações para que as semanas afetadas não peçam designações."),
                    actionLabel = tr("Cadastrar primeiro evento"),
                    onAction = {
                        editing.value = null
                        showForm.value = true
                    },
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.events, key = { it.id }) { event ->
                        EventRow(
                            event = event,
                            isPast = event.date < state.today,
                            onEdit = {
                                editing.value = event
                                showForm.value = true
                            },
                            onDelete = { pendingDeleteId.value = event.id },
                        )
                    }
                }
            }
        }

        state.errorMessage?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(tr(it), color = MaterialTheme.colorScheme.error)
        }
    }

    if (showForm.value) {
        EventFormDialog(
            initial = editing.value,
            onDismiss = { showForm.value = false },
            onConfirm = { nome, date, tipo ->
                val atual = editing.value
                if (atual == null) viewModel.addEvent(nome, date, tipo)
                else viewModel.updateEvent(atual.id, nome, date, tipo)
                showForm.value = false
            },
        )
    }

    pendingDeleteId.value?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId.value = null },
            title = { Text(tr("Confirmar exclusão")) },
            text = { Text(tr("Deseja realmente remover este evento? As reuniões da semana voltam a pedir designações.")) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEvent(id)
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
private fun EventRow(
    event: CongregationEvent,
    isPast: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val fg = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(event.nome, style = MaterialTheme.typography.titleSmall, color = fg)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${tr(event.tipo.label)} · ${LocalT.current.longDateWithYear(event.date)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    effectDescription(event.tipo, event.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(onClick = onEdit) { Text(tr("Editar")) }
            TextButton(onClick = onDelete) { Text(tr("Excluir")) }
        }
    }
}

/** O que o evento faz com as reunioes, dito na propria tela de cadastro. */
@Composable
private fun effectDescription(tipo: EventType, date: LocalDate?): String = when (tipo) {
    EventType.ASSEMBLEIA, EventType.CONGRESSO ->
        tr("Sem reunião de meio de semana nem de fim de semana nesta semana.")
    EventType.COMEMORACAO ->
        if (date != null && date.dayOfWeek.isoDayNumber >= 6) {
            tr("Substitui a reunião deste mesmo dia; a de meio de semana acontece normalmente.")
        } else {
            tr("Sem reunião de meio de semana nesta semana; o fim de semana é normal.")
        }
    EventType.OUTRO -> tr("Apenas anunciado: nenhuma reunião é cancelada.")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventFormDialog(
    initial: CongregationEvent?,
    onDismiss: () -> Unit,
    onConfirm: (String, LocalDate, EventType) -> Unit,
) {
    val nome = remember(initial?.id) { mutableStateOf(initial?.nome.orEmpty()) }
    val dataTexto = remember(initial?.id) { mutableStateOf(initial?.date?.toDisplayText().orEmpty()) }
    val tipo = remember(initial?.id) { mutableStateOf(initial?.tipo ?: EventType.COMEMORACAO) }
    var expanded by remember { mutableStateOf(false) }

    val data = parseDisplayDate(dataTexto.value)
    val dataInvalida = dataTexto.value.isNotBlank() && data == null
    val isValid = nome.value.trim().isNotBlank() && data != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) tr("Novo evento") else tr("Editar evento")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nome.value,
                    onValueChange = { nome.value = it },
                    label = { Text(tr("Nome do evento")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dataTexto.value,
                    onValueChange = { if (it.length <= 10) dataTexto.value = it },
                    label = { Text(tr("Data")) },
                    placeholder = { Text("dd/mm/aaaa") },
                    isError = dataInvalida,
                    supportingText = if (dataInvalida) {
                        { Text(tr("Use o formato dd/mm/aaaa")) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.widthIn(max = 320.dp),
                ) {
                    OutlinedTextField(
                        value = tr(tipo.value.label),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(tr("Tipo")) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        EventType.entries.forEach { opcao ->
                            DropdownMenuItem(
                                text = { Text(tr(opcao.label)) },
                                onClick = {
                                    tipo.value = opcao
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                Text(
                    effectDescription(tipo.value, data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(nome.value, data!!, tipo.value) },
                enabled = isValid,
            ) {
                Text(tr("Salvar"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Cancelar")) }
        },
    )
}

private fun Int.pad2(): String = if (this < 10) "0$this" else toString()

/** "dd/mm/aaaa": o formato que o usuario digita, nos dois idiomas. */
fun LocalDate.toDisplayText(): String = "${dayOfMonth.pad2()}/${monthNumber.pad2()}/$year"

fun parseDisplayDate(text: String): LocalDate? {
    val partes = text.trim().split("/", "-", ".")
    if (partes.size != 3) return null
    val dia = partes[0].toIntOrNull() ?: return null
    val mes = partes[1].toIntOrNull() ?: return null
    val ano = partes[2].toIntOrNull() ?: return null
    if (ano < 1900 || ano > 2999) return null
    return runCatching { LocalDate(ano, mes, dia) }.getOrNull()
}
