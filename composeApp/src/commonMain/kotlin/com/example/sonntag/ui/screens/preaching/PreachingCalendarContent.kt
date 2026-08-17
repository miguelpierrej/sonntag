package com.example.sonntag.ui.screens.preaching

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sonntag.data.repos.PreachingKind
import com.example.sonntag.data.repos.ShiftInput
import com.example.sonntag.data.sqldelight.Preaching_shifts
import com.example.sonntag.data.sqldelight.Preaching_spots
import com.example.sonntag.i18n.LocalT
import com.example.sonntag.i18n.tr
import com.example.sonntag.ui.layout.LocalWindowSize
import kotlinx.datetime.LocalDate
import org.koin.compose.koinInject

/**
 * Calendario mensal dos carrinhos e da pregacao, na mesma grade do documento
 * impresso: domingo a sabado, os dias de fora do mes apagados.
 *
 * Clicar num dia abre os turnos daquele dia. O padrao semanal e o gerador do mes
 * ficam no topo, porque e assim que o mes costuma nascer: gera e depois ajusta.
 */
@Composable
fun PreachingCalendarContent(tipo: PreachingKind) {
    val viewModel = koinInject<PreachingCalendarViewModel>()
    val state by viewModel.uiState.collectAsState()
    val t = LocalT.current
    val compacto = LocalWindowSize.current.isCompact

    // A tela e uma so; o tipo vem da aba escolhida. Trocar de aba e um efeito, nao
    // parte do desenho — dentro da composicao viraria recarga em laco.
    LaunchedEffect(tipo) { viewModel.setTipo(tipo) }

    // Guarda a data, nao o dia: depois de salvar um turno o estado e recarregado, e
    // uma copia antiga deixaria o dialogo mostrando a lista de antes.
    var dataAberta by remember { mutableStateOf<LocalDate?>(null) }
    var mostrarPadrao by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        // No celular os botoes nao cabem ao lado do seletor de mes: descem uma linha.
        val navegacao: @Composable () -> Unit = {
            TextButton(onClick = viewModel::showPreviousMonth) { Text("‹") }
            Text(
                t.monthYearLabel(state.visibleMonth, state.visibleYear),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = viewModel::showNextMonth) { Text("›") }
            TextButton(onClick = viewModel::showToday) { Text(tr("Hoje")) }
        }
        val acoes: @Composable () -> Unit = {
            OutlinedButton(onClick = { mostrarPadrao = true }) { Text(tr("Padrão semanal")) }
            Button(onClick = viewModel::generateMonth) { Text(tr("Gerar mês")) }
            OutlinedButton(onClick = viewModel::exportPdf) { Text(tr("Exportar PDF")) }
        }

        if (compacto) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) { navegacao() }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) { acoes() }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                navegacao()
                Spacer(modifier = Modifier.weight(1f))
                acoes()
            }
        }

        state.mensagem?.let { msg ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr(msg), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::dismissMensagem) { Text(tr("OK")) }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb").forEach { dia ->
                Text(
                    tr(dia),
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.semanas.forEach { semana ->
            Row(modifier = Modifier.fillMaxWidth()) {
                semana.forEach { dia ->
                    DayCell(
                        dia = dia,
                        spots = state.spots,
                        compacto = compacto,
                        modifier = Modifier.weight(1f),
                        onClick = { dataAberta = dia.date },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(tr("Observação do rodapé"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            tr("Sai abaixo do calendário no documento, junto com os grupos."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.observacao,
            onValueChange = viewModel::onObservacaoChanged,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            minLines = 3,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::saveObservacao) { Text(tr("Salvar")) }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    dataAberta?.let { data ->
        val dia = state.semanas.flatten().firstOrNull { it.date == data } ?: return@let
        DayDialog(
            dia = dia,
            state = state,
            onDismiss = { dataAberta = null },
            onSave = { id, input -> viewModel.saveShift(id, dia.date, input) },
            onDelete = viewModel::deleteShift,
        )
    }

    if (mostrarPadrao) {
        WeeklyTemplateDialog(
            state = state,
            onDismiss = { mostrarPadrao = false },
            onSave = { id, diaSemana, inicio, fim, spot ->
                viewModel.saveTemplate(id, diaSemana, inicio, fim, spot)
            },
            onDelete = viewModel::deleteTemplate,
        )
    }
}

/** Uma celula do calendario: o dia e o resumo dos turnos. */
@Composable
private fun DayCell(
    dia: CalendarDay,
    spots: List<Preaching_spots>,
    compacto: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val fundo = when {
        !dia.doMes -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        dia.hoje -> MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
        else -> MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = modifier
            .height(if (compacto) 74.dp else 116.dp)
            .padding(1.dp)
            .background(fundo, RoundedCornerShape(6.dp))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .clickable(enabled = dia.doMes, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 3.dp),
    ) {
        Text(
            dia.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (dia.hoje) FontWeight.Bold else FontWeight.Normal,
            color = if (dia.doMes) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!dia.doMes) return@Column

        if (compacto) {
            if (dia.turnos.isNotEmpty()) {
                Text(
                    "• ${dia.turnos.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            return@Column
        }

        dia.turnos.take(2).forEach { turno ->
            Text(
                turno.hora_inicio,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            spots.firstOrNull { it.id == turno.spot_id }?.let {
                Text(
                    it.nome,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (dia.turnos.size > 2) {
            Text(
                "+${dia.turnos.size - 2}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Os turnos de um dia, com o editor de cada um. */
@Composable
private fun DayDialog(
    dia: CalendarDay,
    state: PreachingCalendarUiState,
    onDismiss: () -> Unit,
    onSave: (Long?, ShiftInput) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val t = LocalT.current
    var editando by remember { mutableStateOf<Preaching_shifts?>(null) }
    var novo by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.longDate(dia.date)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (dia.turnos.isEmpty()) {
                    Text(
                        tr("Nenhum turno neste dia"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                dia.turnos.forEach { turno ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(horaTexto(turno), fontWeight = FontWeight.Bold)
                            val ponto = state.spots.firstOrNull { it.id == turno.spot_id }?.nome
                            val nomes = designados(turno).mapNotNull { id ->
                                state.members.nomeDe(id).ifBlank { null }
                            }
                            Text(
                                listOfNotNull(ponto, nomes.joinToString(", ").ifBlank { null })
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            turno.nota?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        TextButton(onClick = { editando = turno }) { Text(tr("Editar")) }
                        TextButton(onClick = { onDelete(turno.id) }) { Text(tr("Excluir")) }
                    }
                    HorizontalDivider()
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { novo = true }) { Text(tr("Novo turno")) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("OK")) } },
    )

    if (novo || editando != null) {
        ShiftDialog(
            inicial = editando,
            state = state,
            onDismiss = {
                novo = false
                editando = null
            },
            onConfirm = { input ->
                onSave(editando?.id, input)
                novo = false
                editando = null
            },
        )
    }
}

/** Editor de um turno: hora, ponto, nota e os quatro designados. */
@Composable
private fun ShiftDialog(
    inicial: Preaching_shifts?,
    state: PreachingCalendarUiState,
    onDismiss: () -> Unit,
    onConfirm: (ShiftInput) -> Unit,
) {
    var inicio by remember(inicial?.id) { mutableStateOf(inicial?.hora_inicio ?: "") }
    var fim by remember(inicial?.id) { mutableStateOf(inicial?.hora_fim.orEmpty()) }
    var spot by remember(inicial?.id) { mutableStateOf(inicial?.spot_id) }
    var nota by remember(inicial?.id) { mutableStateOf(inicial?.nota.orEmpty()) }
    var pessoas by remember(inicial?.id) {
        mutableStateOf(inicial?.let { designados(it) } ?: List(4) { null })
    }

    val opcoesPonto: List<Pair<Long?, String>> =
        listOf<Pair<Long?, String>>(null to "—") + state.spotsDoTipo.map { it.id as Long? to it.nome }
    val opcoesMembro = state.members.comoOpcoes()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (inicial == null) tr("Novo turno") else tr("Editar turno")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = inicio,
                        onValueChange = { inicio = it },
                        label = { Text(tr("Início")) },
                        placeholder = { Text("09:00") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = fim,
                        onValueChange = { fim = it },
                        label = { Text(tr("Fim")) },
                        placeholder = { Text("11:00") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                PickerField(
                    label = tr("Ponto"),
                    value = state.spots.firstOrNull { it.id == spot }?.nome.orEmpty(),
                    options = opcoesPonto,
                    onSelected = { spot = it },
                )
                pessoas.forEachIndexed { i, id ->
                    PickerField(
                        label = tr("Designado {0}", i + 1),
                        value = state.members.nomeDe(id),
                        options = opcoesMembro,
                        onSelected = { escolhido ->
                            pessoas = pessoas.toMutableList().also { it[i] = escolhido }
                        },
                    )
                }
                OutlinedTextField(
                    value = nota,
                    onValueChange = { nota = it },
                    label = { Text(tr("Destaque")) },
                    placeholder = { Text(tr("Ex.: Todos os grupos no Salão")) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        ShiftInput(
                            horaInicio = normalizaHora(inicio),
                            horaFim = normalizaHora(fim).ifBlank { null },
                            spotId = spot,
                            nota = nota.trim().ifBlank { null },
                            designados = pessoas,
                            ordem = inicial?.ordem ?: 0L,
                        ),
                    )
                },
                enabled = normalizaHora(inicio).isNotBlank(),
            ) { Text(tr("Salvar")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar")) } },
    )
}

/** O padrao semanal que o gerador do mes usa. */
@Composable
private fun WeeklyTemplateDialog(
    state: PreachingCalendarUiState,
    onDismiss: () -> Unit,
    onSave: (Long?, Long, String, String?, Long?) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var diaSemana by remember { mutableStateOf(1L) }
    var inicio by remember { mutableStateOf("") }
    var fim by remember { mutableStateOf("") }
    var spot by remember { mutableStateOf<Long?>(null) }

    val dias = listOf(
        1L to "Segunda", 2L to "Terça", 3L to "Quarta", 4L to "Quinta",
        5L to "Sexta", 6L to "Sábado", 7L to "Domingo",
    )
    val opcoesPonto: List<Pair<Long?, String>> =
        listOf<Pair<Long?, String>>(null to "—") + state.spotsDoTipo.map { it.id as Long? to it.nome }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Padrão semanal")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    tr("Os turnos que se repetem toda semana. \"Gerar mês\" cria o que falta, sem mexer no que já existe."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.templates.forEach { modelo ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val dia = dias.firstOrNull { it.first == modelo.dia_semana }?.second.orEmpty()
                        val ponto = state.spots.firstOrNull { it.id == modelo.spot_id }?.nome
                        Text(
                            listOfNotNull(
                                tr(dia),
                                listOfNotNull(modelo.hora_inicio, modelo.hora_fim).joinToString(" – "),
                                ponto,
                            ).joinToString(" · "),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { onDelete(modelo.id) }) { Text(tr("Excluir")) }
                    }
                    HorizontalDivider()
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(tr("Adicionar ao padrão"), fontWeight = FontWeight.Bold)
                PickerField(
                    label = tr("Dia"),
                    value = tr(dias.firstOrNull { it.first == diaSemana }?.second.orEmpty()),
                    options = dias.map { it.first to tr(it.second) },
                    onSelected = { diaSemana = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = inicio,
                        onValueChange = { inicio = it },
                        label = { Text(tr("Início")) },
                        placeholder = { Text("09:00") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = fim,
                        onValueChange = { fim = it },
                        label = { Text(tr("Fim")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                PickerField(
                    label = tr("Ponto"),
                    value = state.spots.firstOrNull { it.id == spot }?.nome.orEmpty(),
                    options = opcoesPonto,
                    onSelected = { spot = it },
                )
                Button(
                    onClick = {
                        onSave(null, diaSemana, normalizaHora(inicio), normalizaHora(fim).ifBlank { null }, spot)
                        inicio = ""
                        fim = ""
                    },
                    enabled = normalizaHora(inicio).isNotBlank(),
                ) { Text(tr("Adicionar")) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("OK")) } },
    )
}

private fun designados(turno: Preaching_shifts): List<Long?> =
    listOf(turno.designado1_id, turno.designado2_id, turno.designado3_id, turno.designado4_id)

private fun horaTexto(turno: Preaching_shifts): String =
    listOfNotNull(turno.hora_inicio, turno.hora_fim).joinToString(" – ")

/**
 * Aceita "9:00" e "0900" e devolve "09:00". Texto que nao vira hora volta vazio, o
 * que desabilita o botao de salvar em vez de gravar lixo no banco.
 */
internal fun normalizaHora(bruto: String): String {
    val digitos = bruto.filter { it.isDigit() }
    if (digitos.length !in 3..4) return ""
    val hora = digitos.dropLast(2).toIntOrNull() ?: return ""
    val minuto = digitos.takeLast(2).toIntOrNull() ?: return ""
    if (hora !in 0..23 || minuto !in 0..59) return ""
    return "${hora.toString().padStart(2, '0')}:${minuto.toString().padStart(2, '0')}"
}
