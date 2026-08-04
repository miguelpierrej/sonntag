package com.example.sonntag.ui.screens.midweek

import com.example.sonntag.i18n.tr
import com.example.sonntag.i18n.LocalT

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
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
import com.example.sonntag.data.repos.MidweekProgramInput
import com.example.sonntag.data.sqldelight.Members
import com.example.sonntag.ui.components.EmptyState
import com.example.sonntag.ui.components.MonthNavigator
import com.example.sonntag.ui.components.ScreenScaffold
import com.example.sonntag.ui.layout.LocalWindowSize
import kotlinx.coroutines.yield
import org.koin.compose.koinInject

private val LeftColumnWidth = 280.dp
private val EditorMaxWidth = 760.dp

@Composable
fun MidweekProgramsScreenContent() {
    val viewModel = koinInject<MidweekProgramsViewModel>()
    val state by viewModel.uiState.collectAsState()

    val visibleMeetings = state.allMeetings.filter {
        it.year == state.visibleYear && it.month == state.visibleMonth
    }
    val selected = state.allMeetings.firstOrNull { it.id == state.selectedMeetingId }
    val selectedIsInVisibleMonth = selected != null &&
        selected.year == state.visibleYear && selected.month == state.visibleMonth
    val isViewingCurrentMonth = state.visibleYear == state.today.year &&
        state.visibleMonth == state.today.monthNumber

    ScreenScaffold(
        title = tr("Programações de meio de semana"),
        subtitle = tr("Nossa Vida e Ministério Cristão (S-140)"),
        leadingIcon = Icons.AutoMirrored.Outlined.MenuBook,
        actions = {
            if (!isViewingCurrentMonth) {
                TextButton(onClick = viewModel::showCurrentMonth) {
                    Text(tr("Hoje"))
                }
            }
            OutlinedButton(
                onClick = viewModel::importApostila,
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
                Text(if (state.importInProgress) tr("Importando...") else tr("Importar apostila"))
            }
            ExportMenu(
                onPdf = viewModel::exportProgramaPdf,
                onS89 = viewModel::exportDesignacoesS89,
            )
        },
    ) {
        state.importResult?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::dismissImportResult,
                confirmButton = {
                    TextButton(onClick = viewModel::dismissImportResult) { Text(tr("OK")) }
                },
                title = { Text(tr("Importar apostila")) },
                text = { Text(message) },
            )
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@ScreenScaffold
        }

        val compact = LocalWindowSize.current.isCompact
        // No celular a lista e o editor nao cabem lado a lado: viram duas etapas.
        var showingDetail by rememberSaveable { mutableStateOf(false) }
        val detailVisible = selected != null && selectedIsInVisibleMonth

        val lista = @Composable { modifier: Modifier ->
            MeetingListPane(
                modifier = modifier,
                meetings = visibleMeetings,
                selectedId = state.selectedMeetingId,
                year = state.visibleYear,
                month = state.visibleMonth,
                onPrev = viewModel::showPreviousMonth,
                onNext = viewModel::showNextMonth,
                onSelect = {
                    viewModel.selectMeeting(it)
                    showingDetail = true
                },
            )
        }

        val editor = @Composable {
            if (!detailVisible) {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = tr("Selecione uma reunião para editar"),
                    description = tr("Escolha uma reunião na lista ao lado para preencher a programação de meio de semana."),
                )
            } else {
                ProgramEditor(
                    item = selected!!,
                    form = state.form,
                    members = state.members,
                    isReadOnly = state.isReadOnly,
                    onUpdate = viewModel::updateForm,
                )
            }
        }

        if (compact) {
            if (showingDetail && detailVisible) {
                Column(modifier = Modifier.fillMaxSize()) {
                    BackToListButton(onClick = { showingDetail = false })
                    Spacer(modifier = Modifier.height(8.dp))
                    editor()
                }
            } else {
                lista(Modifier.fillMaxSize())
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                lista(Modifier.fillMaxHeight().width(LeftColumnWidth))
                Spacer(modifier = Modifier.width(24.dp))
                Box(modifier = Modifier.fillMaxSize()) { editor() }
            }
        }
    }
}

/** Navegador de mes + lista de reunioes. Coluna fixa no desktop, tela inteira no celular. */
@Composable
private fun MeetingListPane(
    modifier: Modifier,
    meetings: List<MidweekMeetingItem>,
    selectedId: Long?,
    year: Int,
    month: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    Column(modifier = modifier) {
        MonthNavigator(year = year, month = month, onPrev = onPrev, onNext = onNext)
        Spacer(modifier = Modifier.height(12.dp))
        if (meetings.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    tr("Nenhuma reunião neste mês"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(meetings, key = { it.id }) { item ->
                    MeetingListItem(
                        item = item,
                        selected = item.id == selectedId,
                        onClick = { onSelect(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BackToListButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(tr("Voltar à lista"))
    }
}

@Composable
private fun MeetingListItem(
    item: MidweekMeetingItem,
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
                text = LocalT.current.longDate(item.date),
                style = MaterialTheme.typography.titleMedium,
                color = titleColor.copy(alpha = alphaMod),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.isPast) {
                Spacer(modifier = Modifier.width(8.dp))
                Badge(text = tr("Realizada"))
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        val secondary = if (item.summary.isNullOrBlank()) {
            "${item.time} — ${tr("Sem programação")}"
        } else {
            "${item.time} — ${item.summary}"
        }
        Text(
            text = secondary,
            style = MaterialTheme.typography.bodySmall.copy(
                fontStyle = if (item.summary.isNullOrBlank()) FontStyle.Italic else FontStyle.Normal,
            ),
            color = colors.onSurfaceVariant.copy(alpha = alphaMod),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProgramEditor(
    item: MidweekMeetingItem,
    form: MidweekProgramInput,
    members: List<Members>,
    isReadOnly: Boolean,
    onUpdate: ((MidweekProgramInput) -> MidweekProgramInput) -> Unit,
) {
    val enabled = !isReadOnly

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = EditorMaxWidth)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = LocalT.current.longDateWithYear(item.date),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (isReadOnly) Badge(text = tr("Realizada"))
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = tr("Reunião de meio de semana às {0}", item.time),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Cabecalho
        SectionCard(title = tr("Cabeçalho")) {
            FormTextField(
                label = tr("Leitura semanal da Bíblia"),
                value = form.leituraSemanal.orEmpty(),
                enabled = enabled,
                onValueChange = { v -> onUpdate { it.copy(leituraSemanal = v) } },
            )
            Spacer(modifier = Modifier.height(12.dp))
            MemberField(
                label = tr("Presidente"),
                members = members,
                selectedId = form.presidenteId,
                enabled = enabled,
                onSelected = { id -> onUpdate { it.copy(presidenteId = id) } },
            )
            Spacer(modifier = Modifier.height(12.dp))
            MemberField(
                label = tr("Conselheiro da sala auxiliar"),
                members = members,
                selectedId = form.conselheiroId,
                enabled = enabled,
                onSelected = { id -> onUpdate { it.copy(conselheiroId = id) } },
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormTextField(
                    label = tr("Cântico inicial"),
                    value = form.canticoInicial.orEmpty(),
                    enabled = enabled,
                    onValueChange = { v -> onUpdate { it.copy(canticoInicial = v) } },
                    modifier = Modifier.width(140.dp),
                )
                MemberField(
                    label = tr("Oração inicial"),
                    members = members,
                    selectedId = form.oracaoInicialId,
                    enabled = enabled,
                    onSelected = { id -> onUpdate { it.copy(oracaoInicialId = id) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Tesouros da Palavra de Deus
        SectionCard(title = tr("Tesouros da Palavra de Deus")) {
            FormTextField(
                label = tr("Discurso — título (10 min)"),
                value = form.tesourosTitulo.orEmpty(),
                enabled = enabled,
                onValueChange = { v -> onUpdate { it.copy(tesourosTitulo = v) } },
            )
            Spacer(modifier = Modifier.height(12.dp))
            MemberField(
                label = tr("Orador do discurso"),
                members = members,
                selectedId = form.tesourosOradorId,
                enabled = enabled,
                onSelected = { id -> onUpdate { it.copy(tesourosOradorId = id) } },
            )
            Spacer(modifier = Modifier.height(12.dp))
            MemberField(
                label = tr("Joias espirituais (10 min)"),
                members = members,
                selectedId = form.joiasId,
                enabled = enabled,
                onSelected = { id -> onUpdate { it.copy(joiasId = id) } },
            )
            Spacer(modifier = Modifier.height(12.dp))
            MemberField(
                label = tr("Leitura da Bíblia (4 min) — Estudante"),
                members = members,
                selectedId = form.leituraBibliaId,
                enabled = enabled,
                onSelected = { id -> onUpdate { it.copy(leituraBibliaId = id) } },
            )
        }

        // Faca seu melhor no ministerio
        SectionCard(title = tr("Faça seu melhor no ministério")) {
            MinistryPart(
                index = 1,
                titulo = form.min1Titulo.orEmpty(),
                minutos = form.min1Minutos.orEmpty(),
                estudanteId = form.min1EstudanteId,
                ajudanteId = form.min1AjudanteId,
                members = members,
                enabled = enabled,
                onTitulo = { v -> onUpdate { it.copy(min1Titulo = v) } },
                onMinutos = { v -> onUpdate { it.copy(min1Minutos = v) } },
                onEstudante = { id -> onUpdate { it.copy(min1EstudanteId = id) } },
                onAjudante = { id -> onUpdate { it.copy(min1AjudanteId = id) } },
            )
            Spacer(modifier = Modifier.height(16.dp))
            MinistryPart(
                index = 2,
                titulo = form.min2Titulo.orEmpty(),
                minutos = form.min2Minutos.orEmpty(),
                estudanteId = form.min2EstudanteId,
                ajudanteId = form.min2AjudanteId,
                members = members,
                enabled = enabled,
                onTitulo = { v -> onUpdate { it.copy(min2Titulo = v) } },
                onMinutos = { v -> onUpdate { it.copy(min2Minutos = v) } },
                onEstudante = { id -> onUpdate { it.copy(min2EstudanteId = id) } },
                onAjudante = { id -> onUpdate { it.copy(min2AjudanteId = id) } },
            )
            Spacer(modifier = Modifier.height(16.dp))
            MinistryPart(
                index = 3,
                titulo = form.min3Titulo.orEmpty(),
                minutos = form.min3Minutos.orEmpty(),
                estudanteId = form.min3EstudanteId,
                ajudanteId = form.min3AjudanteId,
                members = members,
                enabled = enabled,
                onTitulo = { v -> onUpdate { it.copy(min3Titulo = v) } },
                onMinutos = { v -> onUpdate { it.copy(min3Minutos = v) } },
                onEstudante = { id -> onUpdate { it.copy(min3EstudanteId = id) } },
                onAjudante = { id -> onUpdate { it.copy(min3AjudanteId = id) } },
            )
            Spacer(modifier = Modifier.height(16.dp))
            MinistryPart(
                index = 4,
                titulo = form.min4Titulo.orEmpty(),
                minutos = form.min4Minutos.orEmpty(),
                estudanteId = form.min4EstudanteId,
                ajudanteId = form.min4AjudanteId,
                members = members,
                enabled = enabled,
                onTitulo = { v -> onUpdate { it.copy(min4Titulo = v) } },
                onMinutos = { v -> onUpdate { it.copy(min4Minutos = v) } },
                onEstudante = { id -> onUpdate { it.copy(min4EstudanteId = id) } },
                onAjudante = { id -> onUpdate { it.copy(min4AjudanteId = id) } },
            )
        }

        // Nossa vida crista
        SectionCard(title = tr("Nossa Vida Cristã")) {
            FormTextField(
                label = tr("Cântico do meio"),
                value = form.canticoMeio.orEmpty(),
                enabled = enabled,
                onValueChange = { v -> onUpdate { it.copy(canticoMeio = v) } },
                modifier = Modifier.width(140.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            LifePart(
                titulo = form.vida1Titulo.orEmpty(),
                minutos = form.vida1Minutos.orEmpty(),
                responsavelId = form.vida1Id,
                members = members,
                enabled = enabled,
                onTitulo = { v -> onUpdate { it.copy(vida1Titulo = v) } },
                onMinutos = { v -> onUpdate { it.copy(vida1Minutos = v) } },
                onResponsavel = { id -> onUpdate { it.copy(vida1Id = id) } },
            )
            Spacer(modifier = Modifier.height(16.dp))
            LifePart(
                titulo = form.vida2Titulo.orEmpty(),
                minutos = form.vida2Minutos.orEmpty(),
                responsavelId = form.vida2Id,
                members = members,
                enabled = enabled,
                onTitulo = { v -> onUpdate { it.copy(vida2Titulo = v) } },
                onMinutos = { v -> onUpdate { it.copy(vida2Minutos = v) } },
                onResponsavel = { id -> onUpdate { it.copy(vida2Id = id) } },
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = tr("Estudo bíblico de congregação (30 min)"),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MemberField(
                    label = tr("Dirigente"),
                    members = members,
                    selectedId = form.estudoDirigenteId,
                    enabled = enabled,
                    onSelected = { id -> onUpdate { it.copy(estudoDirigenteId = id) } },
                    modifier = Modifier.weight(1f),
                )
                MemberField(
                    label = tr("Leitor"),
                    members = members,
                    selectedId = form.estudoLeitorId,
                    enabled = enabled,
                    onSelected = { id -> onUpdate { it.copy(estudoLeitorId = id) } },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormTextField(
                    label = tr("Cântico final"),
                    value = form.canticoFinal.orEmpty(),
                    enabled = enabled,
                    onValueChange = { v -> onUpdate { it.copy(canticoFinal = v) } },
                    modifier = Modifier.width(140.dp),
                )
                MemberField(
                    label = tr("Oração final"),
                    members = members,
                    selectedId = form.oracaoFinalId,
                    enabled = enabled,
                    onSelected = { id -> onUpdate { it.copy(oracaoFinalId = id) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MinistryPart(
    index: Int,
    titulo: String,
    minutos: String,
    estudanteId: Long?,
    ajudanteId: Long?,
    members: List<Members>,
    enabled: Boolean,
    onTitulo: (String) -> Unit,
    onMinutos: (String) -> Unit,
    onEstudante: (Long?) -> Unit,
    onAjudante: (Long?) -> Unit,
) {
    Column {
        Text(
            text = tr("Parte {0}", index),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTextField(
                label = tr("Título / designação"),
                value = titulo,
                enabled = enabled,
                onValueChange = onTitulo,
                modifier = Modifier.weight(1f),
            )
            FormTextField(
                label = tr("Min."),
                value = minutos,
                enabled = enabled,
                onValueChange = onMinutos,
                modifier = Modifier.width(90.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MemberField(
                label = tr("Estudante"),
                members = members,
                selectedId = estudanteId,
                enabled = enabled,
                onSelected = onEstudante,
                modifier = Modifier.weight(1f),
            )
            MemberField(
                label = tr("Ajudante"),
                members = members,
                selectedId = ajudanteId,
                enabled = enabled,
                onSelected = onAjudante,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LifePart(
    titulo: String,
    minutos: String,
    responsavelId: Long?,
    members: List<Members>,
    enabled: Boolean,
    onTitulo: (String) -> Unit,
    onMinutos: (String) -> Unit,
    onResponsavel: (Long?) -> Unit,
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormTextField(
                label = tr("Título da parte"),
                value = titulo,
                enabled = enabled,
                onValueChange = onTitulo,
                modifier = Modifier.weight(1f),
            )
            FormTextField(
                label = tr("Min."),
                value = minutos,
                enabled = enabled,
                onValueChange = onMinutos,
                modifier = Modifier.width(90.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        MemberField(
            label = tr("Responsável"),
            members = members,
            selectedId = responsavelId,
            enabled = enabled,
            onSelected = onResponsavel,
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = EditorMaxWidth),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun FormTextField(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        modifier = modifier.then(if (modifier == Modifier) Modifier.fillMaxWidth() else Modifier),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberField(
    label: String,
    members: List<Members>,
    selectedId: Long?,
    enabled: Boolean,
    onSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val selectedMemberName = members.firstOrNull { it.id == selectedId }
        ?.let { "${it.nome} ${it.sobrenome}" }
        ?: ""

    LaunchedEffect(expanded) {
        if (!expanded) query = ""
    }

    val displayValue = if (expanded) query else selectedMemberName
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
        modifier = modifier.then(if (modifier == Modifier) Modifier.fillMaxWidth() else Modifier),
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = { newValue ->
                query = newValue
                expanded = true
            },
            label = { Text(label) },
            placeholder = { Text(tr("Selecionar membro...")) },
            enabled = enabled,
            singleLine = true,
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
                    onSelected(null)
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
                        onSelected(member.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ExportMenu(onPdf: () -> Unit, onS89: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Espera o popup fechar antes de abrir o diálogo nativo de salvar arquivo.
    LaunchedEffect(expanded, pendingAction) {
        val action = pendingAction
        if (!expanded && action != null) {
            yield()
            pendingAction = null
            action()
        }
    }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Outlined.FileDownload,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(tr("Exportar"))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(tr("Exportar PDF")) },
                onClick = {
                    pendingAction = onPdf
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(tr("Designações (S-89)")) },
                onClick = {
                    pendingAction = onS89
                    expanded = false
                },
            )
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
