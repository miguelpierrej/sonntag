package com.example.sonntag.ui.screens.cleaning

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
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import com.example.sonntag.ui.components.EmptyState
import com.example.sonntag.ui.components.MonthNavigator
import com.example.sonntag.ui.components.ScreenScaffold
import org.koin.compose.koinInject

private val CardMaxWidth = 720.dp

@Composable
fun CleaningScreenContent() {
    val viewModel = koinInject<CleaningViewModel>()
    val state by viewModel.uiState.collectAsState()

    val isViewingCurrentMonth = state.visibleYear == state.today.year &&
        state.visibleMonth == state.today.monthNumber

    val visibleWeeksCount = state.weekItems.count {
        it.anchorYear == state.visibleYear && it.anchorMonth == state.visibleMonth
    }
    val canExport = !state.isLoading && visibleWeeksCount > 0

    ScreenScaffold(
        title = "Limpeza",
        subtitle = "Atribuição semanal de grupos",
        actions = {
            if (!isViewingCurrentMonth) {
                TextButton(onClick = viewModel::showCurrentMonth) {
                    Text("Hoje")
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
                Text("Exportar PDF")
            }
            OutlinedButton(
                onClick = viewModel::exportVisibleMonthPng,
                enabled = canExport,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Exportar PNG")
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

                val visibleWeeks = state.weekItems.filter {
                    it.anchorYear == state.visibleYear && it.anchorMonth == state.visibleMonth
                }

                if (visibleWeeks.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.CleaningServices,
                        title = "Nenhuma reunião neste mês",
                        description = "Navegue para outro mês ou cadastre dias de reunião em Configurações.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(visibleWeeks, key = { "${it.isoYear}-${it.isoWeek}" }) { item ->
                            WeekCard(
                                item = item,
                                groupOptions = state.groups.map { it.id to it.nome },
                                onSelected = { viewModel.onGroupSelected(item, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekCard(
    item: CleaningWeekItem,
    groupOptions: List<Pair<Long, String>>,
    onSelected: (Long?) -> Unit,
) {
    val alphaMod = if (item.isPast) 0.6f else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = CardMaxWidth),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.periodText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alphaMod),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.meetingDayTexts.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alphaMod),
            )
            Spacer(modifier = Modifier.height(12.dp))
            GroupDropdown(
                selectedId = item.assignedGroupId,
                groupOptions = groupOptions,
                enabled = !item.isPast,
                onSelected = onSelected,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupDropdown(
    selectedId: Long?,
    groupOptions: List<Pair<Long, String>>,
    enabled: Boolean,
    onSelected: (Long?) -> Unit,
) {
    val expanded = remember { mutableStateOf(false) }
    val hasGroups = groupOptions.isNotEmpty()
    val effectivelyEnabled = enabled && hasGroups
    val selectedName = groupOptions.firstOrNull { it.first == selectedId }?.second
    val displayText = when {
        !hasGroups -> "Cadastre grupos em Configurações"
        selectedName != null -> selectedName
        else -> "Sem grupo atribuído"
    }

    ExposedDropdownMenuBox(
        expanded = expanded.value && effectivelyEnabled,
        onExpandedChange = {
            if (effectivelyEnabled) expanded.value = !expanded.value
        },
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            enabled = effectivelyEnabled,
            label = { Text("Grupo responsável") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value && effectivelyEnabled)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, effectivelyEnabled),
        )
        ExposedDropdownMenu(
            expanded = expanded.value && effectivelyEnabled,
            onDismissRequest = { expanded.value = false },
        ) {
            DropdownMenuItem(
                text = { Text("Sem grupo atribuído") },
                onClick = {
                    onSelected(null)
                    expanded.value = false
                },
            )
            groupOptions.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelected(id)
                        expanded.value = false
                    },
                )
            }
        }
    }
}
