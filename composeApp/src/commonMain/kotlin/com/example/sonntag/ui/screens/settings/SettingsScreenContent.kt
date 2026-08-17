package com.example.sonntag.ui.screens.settings

import com.example.sonntag.i18n.tr
import com.example.sonntag.i18n.AppLanguage
import com.example.sonntag.i18n.LocaleController

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.sonntag.ui.components.ScreenScaffold
import com.example.sonntag.sync.IncomingPackage
import com.example.sonntag.ui.screens.cleaninggroups.CleaningGroupsScreenContent
import com.example.sonntag.ui.screens.datatransfer.DataTransferScreenContent
import org.koin.compose.koinInject

private val ContentMaxWidth = 640.dp

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
    // Com um pacote esperando, a aba de Dados e o unico destino que faz sentido.
    LaunchedEffect(IncomingPackage.bytes) {
        if (IncomingPackage.bytes != null) tab.value = 2
    }
    val subtitle = when (tab.value) {
        0 -> tr("Dados gerais e dias de reunião")
        1 -> tr("Grupos de limpeza")
        else -> tr("Exportar e importar entre instalações")
    }

    ScreenScaffold(
        title = tr("Configurações"),
        subtitle = subtitle,
    ) {
        TabStrip(
            tabs = listOf(tr("Geral"), tr("Grupos de limpeza"), tr("Dados")),
            selectedIndex = tab.value,
            onSelected = { tab.value = it },
        )

        Spacer(modifier = Modifier.height(20.dp))

        when (tab.value) {
            0 -> SettingsGeneralContent()
            1 -> CleaningGroupsScreenContent()
            else -> DataTransferScreenContent()
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
            Text(tr("Carregando configurações..."))
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier.widthIn(max = ContentMaxWidth),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard(title = tr("Idioma")) {
                LanguageDropdown()
            }

            SettingsCard(title = tr("Congregação")) {
                OutlinedTextField(
                    value = state.nome,
                    onValueChange = viewModel::updateNome,
                    label = { Text(tr("Nome da congregação")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.endereco,
                    onValueChange = viewModel::updateEndereco,
                    label = { Text(tr("Endereço")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.telefone,
                    onValueChange = viewModel::updateTelefone,
                    label = { Text(tr("Telefone")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SettingsCard(title = tr("Dias e horários de reunião")) {
                if (state.meetingDays.isEmpty()) {
                    Text(
                        tr("Adicione pelo menos um dia de reunião"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.meetingDays.forEach { day ->
                    MeetingDayRow(
                        item = day,
                        onDiaChange = { dia -> viewModel.updateMeetingDay(day.id, dia, day.hora) },
                        onHoraChange = { hora -> viewModel.updateMeetingDay(day.id, day.diaSemana, hora) },
                        onRemove = { viewModel.removeMeetingDay(day.id) },
                    )
                }
                OutlinedButton(onClick = viewModel::addMeetingDay) {
                    Text(tr("+ Adicionar dia"))
                }
            }

            state.errorMessage?.let {
                Text(tr(it), color = MaterialTheme.colorScheme.error)
            }
            state.successMessage?.let {
                Text(tr(it), color = MaterialTheme.colorScheme.primary)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving,
                ) {
                    Text(if (state.isSaving) tr("Salvando...") else tr("Salvar alterações"))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown() {
    val localeController = koinInject<LocaleController>()
    val current by localeController.language.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.widthIn(max = 280.dp),
    ) {
        OutlinedTextField(
            value = current.label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppLanguage.entries.forEach { lang ->
                DropdownMenuItem(
                    text = { Text(lang.label) },
                    onClick = {
                        localeController.setLanguage(lang)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeetingDayRow(
    item: SettingsMeetingDayItem,
    onDiaChange: (Long) -> Unit,
    onHoraChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val diaLabel = tr(DIAS_SEMANA.find { it.first == item.diaSemana }?.second ?: "Dia")

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = diaLabel,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(tr("Dia")) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DIAS_SEMANA.forEach { (dia, nome) ->
                    DropdownMenuItem(
                        text = { Text(tr(nome)) },
                        onClick = {
                            onDiaChange(dia)
                            expanded = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = item.hora,
            onValueChange = { if (it.length <= 5) onHoraChange(it) },
            label = { Text(tr("Horário")) },
            placeholder = { Text("HH:mm") },
            singleLine = true,
            modifier = Modifier.width(120.dp),
        )

        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = tr("Remover"),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
