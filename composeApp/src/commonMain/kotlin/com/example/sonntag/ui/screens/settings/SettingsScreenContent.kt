package com.example.sonntag.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.sonntag.ui.components.ScreenScaffold
import com.example.sonntag.ui.screens.cleaninggroups.CleaningGroupsScreenContent
import org.koin.compose.koinInject

private val DIAS_SEMANA = listOf(
    1L to "Segunda",
    2L to "Terça",
    3L to "Quarta",
    4L to "Quinta",
    5L to "Sexta",
    6L to "Sábado",
    7L to "Domingo",
)

@Composable
fun SettingsScreenContent() {
    val tab = remember { mutableStateOf(0) }
    val subtitle = if (tab.value == 0) "Dados gerais e dias de reunião" else "Grupos de limpeza"

    ScreenScaffold(
        title = "Configurações",
        subtitle = subtitle,
    ) {
        TabStrip(
            tabs = listOf("Geral", "Grupos de limpeza"),
            selectedIndex = tab.value,
            onSelected = { tab.value = it },
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (tab.value == 0) {
            SettingsGeneralContent()
        } else {
            CleaningGroupsScreenContent()
        }
    }
}

@Composable
private fun TabStrip(
    tabs: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tabs.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent
            val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            TextButton(
                onClick = { onSelected(index) },
                modifier = Modifier.background(bg, RoundedCornerShape(8.dp)),
            ) {
                Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SettingsGeneralContent() {
    val viewModel = koinInject<SettingsViewModel>()
    val state by viewModel.uiState.collectAsState()

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Carregando configurações...")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.nome,
                onValueChange = viewModel::updateNome,
                label = { Text("Nome da congregação") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = state.endereco,
                onValueChange = viewModel::updateEndereco,
                label = { Text("Endereço") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = state.telefone,
                onValueChange = viewModel::updateTelefone,
                label = { Text("Telefone") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Text(
                "Dias e horários de reunião",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        items(state.meetingDays, key = { it.id }) { item ->
            MeetingDayRow(
                item = item,
                onDiaChange = { dia -> viewModel.updateMeetingDay(item.id, dia, item.hora) },
                onHoraChange = { hora -> viewModel.updateMeetingDay(item.id, item.diaSemana, hora) },
                onRemove = { viewModel.removeMeetingDay(item.id) },
            )
        }

        item {
            OutlinedButton(onClick = viewModel::addMeetingDay) {
                Text("+ Adicionar dia")
            }
        }

        state.errorMessage?.let {
            item {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
        state.successMessage?.let {
            item {
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }

        item {
            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSaving) "Salvando..." else "Salvar alterações")
            }
        }
    }
}

@Composable
private fun MeetingDayRow(
    item: SettingsMeetingDayItem,
    onDiaChange: (Long) -> Unit,
    onHoraChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val expanded = remember { mutableStateOf(false) }
    val diaLabel = DIAS_SEMANA.find { it.first == item.diaSemana }?.second ?: "Dia"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Button(onClick = { expanded.value = true }, modifier = Modifier.fillMaxWidth()) {
                Text(diaLabel)
            }
            DropdownMenu(
                expanded = expanded.value,
                onDismissRequest = { expanded.value = false },
            ) {
                DIAS_SEMANA.forEach { (dia, nome) ->
                    DropdownMenuItem(
                        text = { Text(nome) },
                        onClick = {
                            onDiaChange(dia)
                            expanded.value = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = item.hora,
            onValueChange = { if (it.length <= 5) onHoraChange(it) },
            label = { Text("HH:mm") },
            modifier = Modifier.width(110.dp),
        )

        OutlinedButton(onClick = onRemove) {
            Text("Remover")
        }
    }
}
