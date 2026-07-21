package com.example.sonntag.ui.screens.weekend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sonntag.data.sqldelight.Members
import com.example.sonntag.ui.components.EmptyState
import com.example.sonntag.ui.components.MonthNavigator
import com.example.sonntag.ui.components.ScreenScaffold
import kotlinx.coroutines.yield
import org.koin.compose.koinInject

private val LeftColumnWidth = 280.dp
private val EditorMaxWidth = 720.dp

@Composable
fun WeekendProgramsScreenContent() {
    val viewModel = koinInject<WeekendProgramsViewModel>()
    val state by viewModel.uiState.collectAsState()

    val visibleMeetings = state.allMeetings.filter {
        it.year == state.visibleYear && it.month == state.visibleMonth
    }
    val selected = state.allMeetings.firstOrNull { it.id == state.selectedMeetingId }
    val selectedIsInVisibleMonth = selected != null &&
        selected.year == state.visibleYear && selected.month == state.visibleMonth
    val isViewingCurrentMonth = state.visibleYear == state.today.year &&
        state.visibleMonth == state.today.monthNumber
    val canExportMeeting = !state.isLoading && state.selectedMeetingId != null
    val canExportMonth = !state.isLoading && visibleMeetings.isNotEmpty()

    ScreenScaffold(
        title = "Programações de fim de semana",
        subtitle = "Discurso público e estudo de A Sentinela",
        leadingIcon = Icons.AutoMirrored.Outlined.EventNote,
        actions = {
            if (!isViewingCurrentMonth) {
                TextButton(onClick = viewModel::showCurrentMonth) {
                    Text("Hoje")
                }
            }
            ExportMenu(
                canExportMeeting = canExportMeeting,
                canExportMonth = canExportMonth,
                onMeetingPdf = viewModel::exportSelectedMeetingPdf,
                onMeetingPng = viewModel::exportSelectedMeetingPng,
                onMonthPdf = viewModel::exportVisibleMonthPdf,
                onMonthPng = viewModel::exportVisibleMonthPng,
            )
        },
    ) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@ScreenScaffold
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // Left column
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(LeftColumnWidth),
            ) {
                MonthNavigator(
                    year = state.visibleYear,
                    month = state.visibleMonth,
                    onPrev = viewModel::showPreviousMonth,
                    onNext = viewModel::showNextMonth,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (visibleMeetings.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Nenhuma reunião neste mês",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(visibleMeetings, key = { it.id }) { item ->
                            MeetingListItem(
                                item = item,
                                selected = item.id == state.selectedMeetingId,
                                onClick = { viewModel.selectMeeting(item.id) },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Right column: editor or empty state
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    selected == null || !selectedIsInVisibleMonth -> {
                        EmptyState(
                            icon = Icons.Outlined.EventAvailable,
                            title = "Selecione uma reunião para editar",
                            description = "Escolha uma reunião na lista ao lado para preencher a programação.",
                        )
                    }
                    else -> {
                        ProgramEditor(
                            item = selected,
                            tituloDiscurso = state.tituloDiscurso,
                            oradorId = state.oradorId,
                            oradorNome = state.oradorNome,
                            presidenteId = state.presidenteId,
                            dirigenteId = state.dirigenteId,
                            leitorId = state.leitorId,
                            members = state.members,
                            isReadOnly = state.isReadOnly,
                            onTituloChanged = viewModel::onTituloChanged,
                            onOradorChanged = viewModel::onOradorChanged,
                            onPresidenteChanged = viewModel::onPresidenteChanged,
                            onDirigenteChanged = viewModel::onDirigenteChanged,
                            onLeitorChanged = viewModel::onLeitorChanged,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportMenu(
    canExportMeeting: Boolean,
    canExportMonth: Boolean,
    onMeetingPdf: () -> Unit,
    onMeetingPng: () -> Unit,
    onMonthPdf: () -> Unit,
    onMonthPng: () -> Unit,
) {
    val expanded = remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(expanded.value, pendingAction) {
        val action = pendingAction
        if (!expanded.value && action != null) {
            // Wait one turn so the popup is fully dismissed before opening native file dialog.
            yield()
            pendingAction = null
            action()
        }
    }

    Box {
        OutlinedButton(
            onClick = { expanded.value = true },
            enabled = canExportMeeting || canExportMonth,
        ) {
            Icon(
                imageVector = Icons.Outlined.FileDownload,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Exportar")
        }
        DropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
            DropdownMenuItem(
                text = { Text("Esta reunião (PDF)") },
                enabled = canExportMeeting,
                onClick = {
                    pendingAction = onMeetingPdf
                    expanded.value = false
                },
            )
            DropdownMenuItem(
                text = { Text("Esta reunião (PNG)") },
                enabled = canExportMeeting,
                onClick = {
                    pendingAction = onMeetingPng
                    expanded.value = false
                },
            )
            DropdownMenuItem(
                text = { Text("Este mês (PDF)") },
                enabled = canExportMonth,
                onClick = {
                    pendingAction = onMonthPdf
                    expanded.value = false
                },
            )
            DropdownMenuItem(
                text = { Text("Este mês (PNG)") },
                enabled = canExportMonth,
                onClick = {
                    pendingAction = onMonthPng
                    expanded.value = false
                },
            )
        }
    }
}

@Composable
private fun MeetingListItem(
    item: WeekendMeetingItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val bg = if (selected) colors.primary.copy(alpha = 0.10f) else Color.Transparent
    val titleColor = if (selected) colors.primary else colors.onSurface
    val alphaMod = if (item.isPast) 0.6f else 1f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.dateLabelShort,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor.copy(alpha = alphaMod),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.isPast) {
                Spacer(modifier = Modifier.width(8.dp))
                Badge(text = "Realizada")
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        val secondary = if (item.titleSummary.isNullOrBlank()) {
            "${item.time} — Sem programação"
        } else {
            "${item.time} — ${item.titleSummary}"
        }
        Text(
            text = secondary,
            style = MaterialTheme.typography.bodySmall.copy(
                fontStyle = if (item.titleSummary.isNullOrBlank()) FontStyle.Italic else FontStyle.Normal,
            ),
            color = colors.onSurfaceVariant.copy(alpha = alphaMod),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProgramEditor(
    item: WeekendMeetingItem,
    tituloDiscurso: String,
    oradorId: Long?,
    oradorNome: String,
    presidenteId: Long?,
    dirigenteId: Long?,
    leitorId: Long?,
    members: List<Members>,
    isReadOnly: Boolean,
    onTituloChanged: (String) -> Unit,
    onOradorChanged: (Long?, String) -> Unit,
    onPresidenteChanged: (Long?) -> Unit,
    onDirigenteChanged: (Long?) -> Unit,
    onLeitorChanged: (Long?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = EditorMaxWidth),
    ) {
        Text(
            text = item.dateLabelLong,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Reunião pública às ${item.time}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = EditorMaxWidth),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                if (isReadOnly) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Badge(text = "Realizada")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = tituloDiscurso,
                    onValueChange = onTituloChanged,
                    label = { Text("Título do discurso") },
                    enabled = !isReadOnly,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))

                MemberPicker(
                    label = "Orador",
                    members = members,
                    selectedId = oradorId,
                    selectedNome = oradorNome,
                    enabled = !isReadOnly,
                    onSelected = { id, nome -> onOradorChanged(id, nome) },
                    allowFreeText = true,
                )
                Spacer(modifier = Modifier.height(12.dp))

                MemberPicker(
                    label = "Presidente",
                    members = members,
                    selectedId = presidenteId,
                    enabled = !isReadOnly,
                    onSelected = { id, _ -> onPresidenteChanged(id) },
                )
                Spacer(modifier = Modifier.height(12.dp))

                MemberPicker(
                    label = "Dirigente do estudo",
                    members = members,
                    selectedId = dirigenteId,
                    enabled = !isReadOnly,
                    onSelected = { id, _ -> onDirigenteChanged(id) },
                )
                Spacer(modifier = Modifier.height(12.dp))

                MemberPicker(
                    label = "Leitor",
                    members = members,
                    selectedId = leitorId,
                    enabled = !isReadOnly,
                    onSelected = { id, _ -> onLeitorChanged(id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberPicker(
    label: String,
    members: List<Members>,
    selectedId: Long?,
    enabled: Boolean,
    onSelected: (Long?, String) -> Unit,
    selectedNome: String = "",
    allowFreeText: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val selectedMemberName = members.firstOrNull { it.id == selectedId }
        ?.let { "${it.nome} ${it.sobrenome}" }
        ?: ""

    // Display text: prefer member name when a member is selected; otherwise use selectedNome (free text)
    val committedDisplay = if (selectedId != null) selectedMemberName else selectedNome

    LaunchedEffect(expanded) {
        if (!expanded) query = ""
    }

    val displayValue = if (expanded) query else committedDisplay
    val filtered = remember(query, members) {
        if (query.isBlank()) members
        else {
            val term = query.lowercase().trim()
            members.filter {
                it.nome.lowercase().contains(term) || it.sobrenome.lowercase().contains(term)
            }
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = { newValue ->
                query = newValue
                expanded = true
                // In free-text mode, immediately propagate the typed name with no member ID
                if (allowFreeText) {
                    onSelected(null, newValue)
                }
            },
            label = { Text(label) },
            placeholder = {
                if (allowFreeText) Text("Nome ou selecionar da lista...")
                else Text("Selecionar membro...")
            },
            enabled = enabled,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled),
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Limpar seleção", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                onClick = {
                    onSelected(null, "")
                    expanded = false
                },
            )
            if (filtered.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "Nenhum membro encontrado",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    enabled = false,
                    onClick = {},
                )
            }
            filtered.forEach { member ->
                DropdownMenuItem(
                    text = { Text("${member.nome} ${member.sobrenome}") },
                    onClick = {
                        onSelected(member.id, "${member.nome} ${member.sobrenome}")
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Box(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.tertiaryContainer,
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}
