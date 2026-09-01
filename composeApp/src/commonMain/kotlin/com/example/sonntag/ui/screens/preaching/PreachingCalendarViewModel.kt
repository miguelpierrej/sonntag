package com.example.sonntag.ui.screens.preaching

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonntag.data.repos.MembersRepository
import com.example.sonntag.data.repos.PreachingGroupsRepository
import com.example.sonntag.data.repos.PreachingKind
import com.example.sonntag.data.repos.PreachingScheduleRepository
import com.example.sonntag.data.repos.PreachingSpotsRepository
import com.example.sonntag.data.repos.ShiftInput
import com.example.sonntag.data.repos.SettingsRepository
import com.example.sonntag.data.repos.SpotKind
import com.example.sonntag.data.sqldelight.Members
import com.example.sonntag.data.sqldelight.Preaching_shifts
import com.example.sonntag.data.sqldelight.Preaching_spots
import com.example.sonntag.data.sqldelight.Preaching_templates
import com.example.sonntag.domain.usecases.PreachingMonthGenerator
import com.example.sonntag.domain.usecases.WeeklySlot
import com.example.sonntag.i18n.LocaleController
import com.example.sonntag.pdf.PdfExportService
import com.example.sonntag.pdf.PreachingDayPdf
import com.example.sonntag.pdf.PreachingGroupPdf
import com.example.sonntag.pdf.PreachingProgramPdfData
import com.example.sonntag.pdf.PreachingShiftPdf
import com.example.sonntag.pdf.nomeCurtoDeCalendario
import com.example.sonntag.pdf.preachingPdfStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** Um dia do calendario, com os turnos que caem nele. */
data class CalendarDay(
    val date: LocalDate,
    val doMes: Boolean,
    val hoje: Boolean,
    val turnos: List<Preaching_shifts>,
)

data class PreachingCalendarUiState(
    val isLoading: Boolean = true,
    val tipo: PreachingKind = PreachingKind.CARRITO,
    val visibleYear: Int = 1970,
    val visibleMonth: Int = 1,
    val semanas: List<List<CalendarDay>> = emptyList(),
    val spots: List<Preaching_spots> = emptyList(),
    val members: List<Members> = emptyList(),
    val templates: List<Preaching_templates> = emptyList(),
    val observacao: String = "",
    val mensagem: String? = null,
) {
    /** Pontos que servem ao programa aberto. */
    val spotsDoTipo: List<Preaching_spots>
        get() = spots.filter { salvo ->
            SpotKind.entries.firstOrNull { it.name == salvo.tipo }?.serve(tipo) == true
        }
}

/**
 * Calendario dos dois programas de pregacao. A tela e a mesma; o que muda e o
 * [PreachingCalendarUiState.tipo], que filtra turnos, pontos e padrao semanal.
 */
class PreachingCalendarViewModel(
    private val scheduleRepository: PreachingScheduleRepository,
    private val spotsRepository: PreachingSpotsRepository,
    private val groupsRepository: PreachingGroupsRepository,
    private val membersRepository: MembersRepository,
    private val settingsRepository: SettingsRepository,
    private val pdfExportService: PdfExportService,
    private val localeController: LocaleController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreachingCalendarUiState())
    val uiState: StateFlow<PreachingCalendarUiState> = _uiState.asStateFlow()

    init {
        val hoje = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        _uiState.value = _uiState.value.copy(visibleYear = hoje.year, visibleMonth = hoje.monthNumber)
        load()
        observarMembros()
    }

    /**
     * A tela vive enquanto o app vive (e um singleton do Koin): sem observar a
     * tabela, um publicador cadastrado ou editado em Membros so apareceria aqui
     * depois de reabrir o app. A primeira emissao e descartada porque `init` ja
     * carregou.
     */
    private fun observarMembros() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { membersRepository.getAll().drop(1).collect { load() } }
        }
    }

    fun setTipo(tipo: PreachingKind) {
        _uiState.value = _uiState.value.copy(tipo = tipo)
        load()
    }

    fun showPreviousMonth() = shiftMonth(-1)

    fun showNextMonth() = shiftMonth(1)

    fun showToday() {
        val hoje = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        _uiState.value = _uiState.value.copy(visibleYear = hoje.year, visibleMonth = hoje.monthNumber)
        load()
    }

    private fun shiftMonth(delta: Int) {
        val s = _uiState.value
        val base = LocalDate(s.visibleYear, s.visibleMonth, 1).plus(DatePeriod(months = delta))
        _uiState.value = s.copy(visibleYear = base.year, visibleMonth = base.monthNumber)
        load()
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val s = _uiState.value
            val hoje = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val semanas = PreachingMonthGenerator.gradeDoMes(s.visibleYear, s.visibleMonth)
            val inicio = semanas.first().first()
            val fim = semanas.last().last()

            runCatching {
                val turnos = scheduleRepository.shiftsBetween(s.tipo, inicio.toString(), fim.toString())
                    .groupBy { it.data_ }
                val dias = semanas.map { semana ->
                    semana.map { dia ->
                        CalendarDay(
                            date = dia,
                            doMes = dia.monthNumber == s.visibleMonth && dia.year == s.visibleYear,
                            hoje = dia == hoje,
                            turnos = turnos[dia.toString()].orEmpty(),
                        )
                    }
                }
                Carga(
                    dias = dias,
                    spots = spotsRepository.getAllOnce(),
                    members = membersRepository.getAllOnce(),
                    templates = scheduleRepository.templates(s.tipo),
                    nota = scheduleRepository.note(s.tipo, s.visibleYear, s.visibleMonth)?.texto.orEmpty(),
                )
            }.onSuccess { carga ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    semanas = carga.dias,
                    spots = carga.spots,
                    members = carga.members,
                    templates = carga.templates,
                    observacao = carga.nota,
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, mensagem = "Erro ao carregar o calendário")
            }
        }
    }

    /** O que uma carga do mes traz de uma vez, para o estado mudar num passo so. */
    private data class Carga(
        val dias: List<List<CalendarDay>>,
        val spots: List<Preaching_spots>,
        val members: List<Members>,
        val templates: List<Preaching_templates>,
        val nota: String,
    )

    // ─── Turnos ──────────────────────────────────────────────────────────────

    fun saveShift(id: Long?, data: LocalDate, input: ShiftInput) {
        edita("Erro ao salvar o turno") {
            if (id == null) scheduleRepository.insertShift(_uiState.value.tipo, data.toString(), input)
            else scheduleRepository.updateShift(id, input)
        }
    }

    fun deleteShift(id: Long) = edita("Erro ao remover o turno") { scheduleRepository.deleteShift(id) }

    // ─── Padrao semanal ──────────────────────────────────────────────────────

    fun saveTemplate(id: Long?, diaSemana: Long, horaInicio: String, horaFim: String?, spotId: Long?) {
        edita("Erro ao salvar o padrão") {
            val tipo = _uiState.value.tipo
            val ordem = (scheduleRepository.templates(tipo).maxOfOrNull { it.ordem } ?: -1L) + 1L
            if (id == null) scheduleRepository.insertTemplate(tipo, diaSemana, horaInicio, horaFim, spotId, ordem)
            else scheduleRepository.updateTemplate(id, diaSemana, horaInicio, horaFim, spotId, ordem)
        }
    }

    fun deleteTemplate(id: Long) = edita("Erro ao remover o padrão") { scheduleRepository.deleteTemplate(id) }

    /**
     * Cria no mes visivel os turnos que faltam para cumprir o padrao semanal.
     *
     * Nao mexe no que ja existe: o mesmo dia e hora nao entram duas vezes, entao
     * gerar de novo depois de acertar um dia nao desfaz o acerto.
     */
    fun generateMonth() {
        edita("Erro ao gerar o mês") {
            val s = _uiState.value
            val templates = scheduleRepository.templates(s.tipo)
            if (templates.isEmpty()) {
                _uiState.value = s.copy(
                    mensagem = localeController.translator("Defina o padrão semanal antes de gerar o mês."),
                )
                return@edita
            }
            val existentes = scheduleRepository
                .shiftsBetween(s.tipo, LocalDate(s.visibleYear, s.visibleMonth, 1).toString(), fimDoMes(s).toString())
                .map { it.data_ to it.hora_inicio }
                .toSet()
            val novos = PreachingMonthGenerator.turnosDoMes(
                ano = s.visibleYear,
                mes = s.visibleMonth,
                padrao = templates.map {
                    WeeklySlot(it.dia_semana.toInt(), it.hora_inicio, it.hora_fim, it.spot_id, it.ordem)
                },
                existentes = existentes,
            )
            novos.forEach { (dia, input) -> scheduleRepository.insertShift(s.tipo, dia.toString(), input) }
            val criados = novos.size
            val t = localeController.translator
            _uiState.value = _uiState.value.copy(
                mensagem = if (criados == 0) t("O mês já estava completo.") else t("{0} turnos criados.", criados),
            )
        }
    }

    // ─── Exportacao ──────────────────────────────────────────────────────────

    fun exportPdf() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { pdfExportService.exportPreachingProgram(buildExport()) }
                .onFailure { _uiState.value = _uiState.value.copy(mensagem = "Erro ao exportar o programa") }
        }
    }

    private fun buildExport(): PreachingProgramPdfData {
        val s = _uiState.value
        val t = localeController.translator
        val labels = preachingPdfStrings(localeController.current)
        val settings = settingsRepository.getSettingsOnce()
        val nomes = s.members.associate { it.id to nomeCurtoDeCalendario(it.nome, it.sobrenome) }
        val pontos = s.spots.associateBy { it.id }

        fun turno(t0: Preaching_shifts) = PreachingShiftPdf(
            hora = listOfNotNull(t0.hora_inicio, t0.hora_fim).joinToString(" a "),
            ponto = pontos[t0.spot_id]?.nome,
            nomes = listOfNotNull(
                t0.designado1_id, t0.designado2_id, t0.designado3_id, t0.designado4_id,
            ).mapNotNull { nomes[it] },
            nota = t0.nota,
        )

        // Os grupos so fazem sentido no rodape da pregacao; o dos carrinhos traz o
        // recado do mes, que fala de onde retirar o carrinho.
        val grupos = if (s.tipo == PreachingKind.PREDICACION) {
            groupsRepository.getAllOnce().map { g ->
                PreachingGroupPdf(
                    nome = g.nome,
                    dirigente = g.dirigente_id?.let { nomes[it] },
                    local = pontos[g.spot_id]?.let { ponto ->
                        listOfNotNull(ponto.nome, ponto.endereco?.takeIf { it.isNotBlank() })
                            .joinToString(" – ")
                    },
                )
            }
        } else {
            emptyList()
        }

        val mesPadded = s.visibleMonth.toString().padStart(2, '0')
        return PreachingProgramPdfData(
            congregacao = settings?.nome?.takeIf { it.isNotBlank() } ?: t("Congregação"),
            titulo = if (s.tipo == PreachingKind.CARRITO) labels.tituloCarritos else labels.tituloPredicacion,
            mesLabel = t.monthYearLabel(s.visibleMonth, s.visibleYear),
            fileSlug = "${s.tipo.name.lowercase()}_${s.visibleYear}-$mesPadded",
            semanas = s.semanas.map { semana ->
                semana.map { dia ->
                    PreachingDayPdf(
                        dia = dia.date.dayOfMonth,
                        doMes = dia.doMes,
                        turnos = dia.turnos.map(::turno),
                    )
                }
            },
            grupos = grupos,
            observacao = s.observacao.takeIf { it.isNotBlank() },
            labels = labels,
        )
    }

    // ─── Observacao do rodape ────────────────────────────────────────────────

    fun onObservacaoChanged(texto: String) {
        _uiState.value = _uiState.value.copy(observacao = texto)
    }

    fun saveObservacao() {
        val s = _uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { scheduleRepository.saveNote(s.tipo, s.visibleYear, s.visibleMonth, s.observacao) }
                .onFailure { _uiState.value = _uiState.value.copy(mensagem = "Erro ao salvar a observação") }
        }
    }

    fun dismissMensagem() {
        _uiState.value = _uiState.value.copy(mensagem = null)
    }

    private fun fimDoMes(s: PreachingCalendarUiState): LocalDate =
        LocalDate(s.visibleYear, s.visibleMonth, 1)
            .plus(DatePeriod(months = 1))
            .minus(DatePeriod(days = 1))

    private fun edita(erro: String, bloco: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching(bloco)
                .onSuccess { load() }
                .onFailure { _uiState.value = _uiState.value.copy(mensagem = erro) }
        }
    }
}
