package com.example.sonntag.ui.screens.weekend

import com.example.sonntag.i18n.tr
import com.example.sonntag.i18n.LocalT

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sonntag.data.sqldelight.Members
import com.example.sonntag.domain.models.TalkOutline
import com.example.sonntag.ui.components.EmptyState
import com.example.sonntag.ui.components.EventAnnouncementCard
import com.example.sonntag.ui.components.MonthNavigator
import com.example.sonntag.ui.components.ScreenScaffold
import com.example.sonntag.ui.layout.LocalWindowSize
import kotlinx.coroutines.yield
import org.koin.compose.koinInject

private val CardMaxWidth = 980.dp

/** Cada campo precisa de ~230dp para o nome nao ser cortado. */
private val MIN_WIDTH_FOR_TWO_COLUMNS = 520.dp

@Composable
fun WeekendProgramsScreenContent() {
    val viewModel = koinInject<WeekendProgramsViewModel>()
    val state by viewModel.uiState.collectAsState()

    // Recarrega ao voltar para a tela: um evento cadastrado em Configuracoes muda
    // quais semanas pedem designacao.
    LaunchedEffect(Unit) { viewModel.load() }

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
        title = tr("Programações de fim de semana"),
        subtitle = tr("Discurso público e estudo de A Sentinela"),
        leadingIcon = Icons.AutoMirrored.Outlined.EventNote,
        actions = {
            if (!isViewingCurrentMonth) {
                TextButton(onClick = viewModel::showCurrentMonth) {
                    Text(tr("Hoje"))
                }
            }
            OutlinedButton(
                onClick = viewModel::importS34,
                enabled = !state.importInProgress,
            ) {
                if (state.importInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Outlined.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (state.importInProgress) tr("Importando...") else tr("Importar S-34"))
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
        state.importResult?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::dismissImportResult,
                confirmButton = {
                    TextButton(onClick = viewModel::dismissImportResult) { Text(tr("OK")) }
                },
                title = { Text(tr("Importar S-34")) },
                text = { Text(message) },
            )
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@ScreenScaffold
        }

        MonthNavigator(
            year = state.visibleYear,
            month = state.visibleMonth,
            onPrev = viewModel::showPreviousMonth,
            onNext = viewModel::showNextMonth,
            modifier = Modifier.widthIn(max = CardMaxWidth),
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (visibleMeetings.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.EventAvailable,
                title = tr("Nenhuma reunião neste mês"),
                description = tr("Navegue para outro mês ou cadastre dias de reunião em Configurações."),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visibleMeetings, key = { it.id }) { item ->
                    val event = item.event
                    if (event != null) {
                        EventAnnouncementCard(
                            event = event,
                            dateLabel = item.dateLabelShort,
                            isPast = item.isPast,
                            modifier = Modifier.widthIn(max = CardMaxWidth),
                        )
                    } else {
                        MeetingCard(
                            item = item,
                            talkOutlines = state.talkOutlines,
                            members = state.members,
                            selected = item.id == state.selectedMeetingId,
                            onSelect = { viewModel.selectMeeting(item.id) },
                            onTituloChanged = { viewModel.onTituloChanged(item.id, it) },
                            onOradorChanged = { id, nome -> viewModel.onOradorChanged(item.id, id, nome) },
                            onPresidenteChanged = { viewModel.onPresidenteChanged(item.id, it) },
                            onDirigenteChanged = { viewModel.onDirigenteChanged(item.id, it) },
                            onLeitorChanged = { viewModel.onLeitorChanged(item.id, it) },
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
            Text(tr("Exportar"))
        }
        DropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
            DropdownMenuItem(
                text = { Text(tr("Esta reunião (PDF)")) },
                enabled = canExportMeeting,
                onClick = {
                    pendingAction = onMeetingPdf
                    expanded.value = false
                },
            )
            DropdownMenuItem(
                text = { Text(tr("Esta reunião (PNG)")) },
                enabled = canExportMeeting,
                onClick = {
                    pendingAction = onMeetingPng
                    expanded.value = false
                },
            )
            DropdownMenuItem(
                text = { Text(tr("Este mês (PDF)")) },
                enabled = canExportMonth,
                onClick = {
                    pendingAction = onMonthPdf
                    expanded.value = false
                },
            )
            DropdownMenuItem(
                text = { Text(tr("Este mês (PNG)")) },
                enabled = canExportMonth,
                onClick = {
                    pendingAction = onMonthPng
                    expanded.value = false
                },
            )
        }
    }
}


/**
 * Uma reuniao por cartao, com a programacao dentro — o mesmo formato da tela de
 * audio/video. Os campos se reorganizam conforme a largura disponivel.
 */
@Composable
private fun MeetingCard(
    item: WeekendMeetingItem,
    talkOutlines: List<TalkOutline>,
    members: List<Members>,
    selected: Boolean,
    onSelect: () -> Unit,
    onTituloChanged: (String) -> Unit,
    onOradorChanged: (Long?, String) -> Unit,
    onPresidenteChanged: (Long?) -> Unit,
    onDirigenteChanged: (Long?) -> Unit,
    onLeitorChanged: (Long?) -> Unit,
) {
    val alphaMod = if (item.isPast) 0.6f else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = CardMaxWidth)
            // Sem realce: o cartao inteiro e clicavel e o hover padrao o deixaria cinza.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 3.dp else 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.dateLabelShort,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alphaMod),
                    )
                    Text(
                        text = tr("Reunião pública às {0}", item.time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alphaMod),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.isPast) Badge(text = tr("Realizada"))
                    FilledCountBadge(filled = item.filledCount)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            TalkTitlePicker(
                value = item.tituloDiscurso,
                outlines = talkOutlines,
                enabled = !item.isPast,
                onValueChanged = onTituloChanged,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Duas colunas quando ha largura; uma so quando nao ha.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val porLinha = if (maxWidth >= MIN_WIDTH_FOR_TWO_COLUMNS) 2 else 1
                val campos = listOf<@Composable () -> Unit>(
                    {
                        MemberPicker(
                            label = tr("Orador"),
                            members = members,
                            selectedId = item.oradorId,
                            selectedNome = item.oradorNome,
                            enabled = !item.isPast,
                            onSelected = { id, nome -> onOradorChanged(id, nome) },
                            allowFreeText = true,
                        )
                    },
                    {
                        MemberPicker(
                            label = tr("Presidente"),
                            members = members,
                            selectedId = item.presidenteId,
                            enabled = !item.isPast,
                            onSelected = { id, _ -> onPresidenteChanged(id) },
                        )
                    },
                    {
                        MemberPicker(
                            label = tr("Dirigente do estudo"),
                            members = members,
                            selectedId = item.dirigenteId,
                            enabled = !item.isPast,
                            onSelected = { id, _ -> onDirigenteChanged(id) },
                        )
                    },
                    {
                        MemberPicker(
                            label = tr("Leitor"),
                            members = members,
                            selectedId = item.leitorId,
                            enabled = !item.isPast,
                            onSelected = { id, _ -> onLeitorChanged(id) },
                        )
                    },
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    campos.chunked(porLinha).forEach { linha ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            linha.forEach { campo -> Box(modifier = Modifier.weight(1f)) { campo() } }
                            repeat(porLinha - linha.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** Quantos dos cinco campos ja estao preenchidos. */
@Composable
private fun FilledCountBadge(filled: Int) {
    val completo = filled == 5
    Box(
        modifier = Modifier
            .background(
                if (completo) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = "$filled/5",
            style = MaterialTheme.typography.labelMedium,
            color = if (completo) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Titulo do discurso: aceita texto livre e, quando o S-34 foi importado, oferece a
 * lista de bosquejos (com o numero) filtrada pelo que esta sendo digitado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TalkTitlePicker(
    value: String,
    outlines: List<TalkOutline>,
    enabled: Boolean,
    onValueChanged: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val filtered = remember(value, outlines) {
        val term = value.lowercase().trim()
        if (term.isBlank()) outlines
        else outlines.filter { it.display.lowercase().contains(term) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChanged(it)
                expanded = true
            },
            label = { Text(tr("Título do discurso")) },
            placeholder = { Text(tr("Digite um título ou escolha um bosquejo do S-34...")) },
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
            when {
                outlines.isEmpty() -> DropdownMenuItem(
                    text = {
                        Text(
                            tr("Nenhum bosquejo importado — use \"Importar S-34\""),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    enabled = false,
                    onClick = {},
                )
                filtered.isEmpty() -> DropdownMenuItem(
                    text = {
                        Text(
                            tr("Nenhum bosquejo encontrado"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    enabled = false,
                    onClick = {},
                )
            }
            filtered.forEach { outline ->
                DropdownMenuItem(
                    text = { Text(outline.display) },
                    onClick = {
                        onValueChanged(outline.display)
                        expanded = false
                    },
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
                if (allowFreeText) Text(tr("Nome ou selecionar da lista..."))
                else Text(tr("Selecionar membro..."))
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
                text = { Text(tr("Limpar seleção"), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                onClick = {
                    onSelected(null, "")
                    expanded = false
                },
            )
            if (filtered.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(tr("Nenhum membro encontrado"),
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
