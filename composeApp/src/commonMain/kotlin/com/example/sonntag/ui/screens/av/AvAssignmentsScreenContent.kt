package com.example.sonntag.ui.screens.av

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.example.sonntag.data.sqldelight.Members
import com.example.sonntag.i18n.tr
import com.example.sonntag.ui.components.EmptyState
import com.example.sonntag.ui.components.MonthNavigator
import com.example.sonntag.ui.components.ScreenScaffold
import org.koin.compose.koinInject

private val CardMaxWidth = 980.dp

@Composable
fun AvAssignmentsScreenContent() {
    val viewModel = koinInject<AvAssignmentsViewModel>()
    val state by viewModel.uiState.collectAsState()

    // Recarrega ao abrir a tela: os conflitos dependem das programacoes, que podem
    // ter sido editadas em outra tela desde a ultima carga.
    LaunchedEffect(Unit) { viewModel.load() }

    val isViewingCurrentMonth = state.visibleYear == state.today.year &&
        state.visibleMonth == state.today.monthNumber

    val visibleMeetings = state.meetings.filter {
        it.year == state.visibleYear && it.month == state.visibleMonth
    }
    val canExport = !state.isLoading && visibleMeetings.isNotEmpty()

    ScreenScaffold(
        title = tr("Áudio/vídeo e acomodadores"),
        subtitle = tr("Designações técnicas de cada reunião"),
        actions = {
            if (!isViewingCurrentMonth) {
                TextButton(onClick = viewModel::showCurrentMonth) {
                    Text(tr("Hoje"))
                }
            }
            OutlinedButton(
                onClick = viewModel::exportVisibleMonthPdf,
                enabled = canExport,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(tr("Exportar PDF"))
            }
        },
    ) {
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> {
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
                        icon = Icons.Outlined.Headphones,
                        title = tr("Nenhuma reunião neste mês"),
                        description = tr("Navegue para outro mês ou cadastre dias de reunião em Configurações."),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(visibleMeetings, key = { it.meetingId }) { item ->
                            MeetingCard(
                                item = item,
                                members = state.members,
                                onRoleChanged = { role, memberId ->
                                    viewModel.onRoleChanged(item.meetingId, role, memberId)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeetingCard(
    item: AvMeetingItem,
    members: List<Members>,
    onRoleChanged: (AvRole, Long?) -> Unit,
) {
    val alphaMod = if (item.isPast) 0.6f else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = CardMaxWidth),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.dateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alphaMod),
                    )
                    Text(
                        text = if (item.isWeekend) {
                            tr("Reunião do fim de semana às {0}", item.time)
                        } else {
                            tr("Reunião de meio de semana às {0}", item.time)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alphaMod),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.conflictCount > 0) ConflictCountBadge(count = item.conflictCount)
                    FilledCountBadge(filled = item.filledCount)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quatro colunas, na mesma ordem das colunas do PDF.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                @Composable
                fun Slot(label: String, role: AvRole) {
                    MemberSlot(
                        label = label,
                        members = members,
                        selectedId = item.memberId(role),
                        enabled = !item.isPast,
                        conflictRoles = item.conflictsFor(role),
                        onSelected = { onRoleChanged(role, it) },
                    )
                }

                RoleColumn(title = tr("Áudio e vídeo"), modifier = Modifier.weight(1f)) {
                    Slot(tr("Áudio"), AvRole.AUDIO)
                    Slot(tr("Vídeo"), AvRole.VIDEO)
                }
                RoleColumn(title = tr("Plataforma"), modifier = Modifier.weight(1f)) {
                    Slot(tr("Plataforma 1"), AvRole.PLATAFORMA1)
                    Slot(tr("Plataforma 2"), AvRole.PLATAFORMA2)
                }
                RoleColumn(title = tr("Microfones"), modifier = Modifier.weight(1f)) {
                    Slot(tr("Microfone 1"), AvRole.MICROFONE1)
                    Slot(tr("Microfone 2"), AvRole.MICROFONE2)
                }
                RoleColumn(title = tr("Acomodadores"), modifier = Modifier.weight(1f)) {
                    Slot(tr("Acomodador do auditório"), AvRole.ACOMODADOR1)
                    Slot(tr("Acomodador da entrada"), AvRole.ACOMODADOR2)
                }
            }
        }
    }
}

@Composable
private fun RoleColumn(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun FilledCountBadge(filled: Int) {
    Box(
        modifier = Modifier
            .background(
                if (filled == 0) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.tertiaryContainer,
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = tr("{0} designados", filled),
            style = MaterialTheme.typography.labelSmall,
            color = if (filled == 0) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

/**
 * Aviso de que o membro escolhido ja tem designacao na programacao desta reuniao.
 * O tooltip (hover) lista quais funcoes estao em conflito.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConflictWarning(roles: List<String>) {
    val translatedRoles = roles.map { tr(it) }
    val detail = tr("Já designado nesta reunião como: {0}", translatedRoles.joinToString(", "))

    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 4.dp,
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = tr("Conflito de designação"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        },
        delayMillis = 250,
    ) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = detail,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ConflictCountBadge(count: Int) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = tr("{0} com conflito", count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberSlot(
    label: String,
    members: List<Members>,
    selectedId: Long?,
    enabled: Boolean,
    conflictRoles: List<String> = emptyList(),
    onSelected: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val hasConflict = conflictRoles.isNotEmpty()

    val selectedName = members.firstOrNull { it.id == selectedId }
        ?.let { "${it.nome} ${it.sobrenome}" }
        ?: ""

    LaunchedEffect(expanded) {
        if (!expanded) query = ""
    }

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
            value = if (expanded) query else selectedName,
            onValueChange = {
                query = it
                expanded = true
            },
            label = { Text(label, style = MaterialTheme.typography.bodySmall) },
            placeholder = { Text(tr("Selecionar membro...")) },
            enabled = enabled,
            singleLine = true,
            isError = hasConflict,
            textStyle = MaterialTheme.typography.bodyMedium,
            leadingIcon = if (hasConflict) {
                { ConflictWarning(roles = conflictRoles) }
            } else {
                null
            },
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
                text = {
                    Text(tr("Limpar seleção"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            if (filtered.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            tr("Nenhum membro encontrado"),
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
                        onSelected(member.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
