package com.example.sonntag.ui.screens.midweek

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonntag.data.repos.MembersRepository
import com.example.sonntag.data.repos.MeetingsRepository
import com.example.sonntag.data.repos.MidweekProgramInput
import com.example.sonntag.data.repos.MidweekProgramsRepository
import com.example.sonntag.data.repos.SettingsRepository
import com.example.sonntag.data.sqldelight.Members
import com.example.sonntag.data.sqldelight.Midweek_programs
import com.example.sonntag.domain.usecases.MeetingGenerator
import com.example.sonntag.domain.usecases.MwbParser
import com.example.sonntag.domain.usecases.MwbWeek
import com.example.sonntag.imports.MwbImportService
import com.example.sonntag.pdf.MidweekAssignmentPdf
import com.example.sonntag.pdf.MidweekAssignmentsPdfData
import com.example.sonntag.pdf.MidweekPartPdf
import com.example.sonntag.pdf.MidweekProgramPdfData
import com.example.sonntag.pdf.MidweekWeekPdf
import com.example.sonntag.pdf.PdfExportService
import com.example.sonntag.ui.util.longDateLabel
import com.example.sonntag.ui.util.longDateLabelWithYear
import com.example.sonntag.ui.util.monthNamePt
import com.example.sonntag.ui.util.monthNamePtCapitalized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class MidweekMeetingItem(
    val id: Long,
    val date: LocalDate,
    val time: String,
    val year: Int,
    val month: Int,
    val dateLabelShort: String,
    val dateLabelLong: String,
    val summary: String?,
    val isPast: Boolean,
)

data class MidweekProgramsUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate(1970, 1, 1),
    val visibleYear: Int = 1970,
    val visibleMonth: Int = 1,
    val allMeetings: List<MidweekMeetingItem> = emptyList(),
    val selectedMeetingId: Long? = null,
    val members: List<Members> = emptyList(),
    val form: MidweekProgramInput = MidweekProgramInput(),
    val isReadOnly: Boolean = false,
    val importInProgress: Boolean = false,
    val importResult: String? = null,
)

class MidweekProgramsViewModel(
    private val meetingsRepository: MeetingsRepository,
    private val membersRepository: MembersRepository,
    private val midweekProgramsRepository: MidweekProgramsRepository,
    private val meetingGenerator: MeetingGenerator,
    private val mwbImportService: MwbImportService,
    private val settingsRepository: SettingsRepository,
    private val pdfExportService: PdfExportService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MidweekProgramsUiState())
    val uiState: StateFlow<MidweekProgramsUiState> = _uiState.asStateFlow()
    private var autosaveJob: Job? = null
    private var suppressAutosave = false

    init {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        _uiState.value = _uiState.value.copy(
            today = today,
            visibleYear = today.year,
            visibleMonth = today.monthNumber,
        )
        observeMembers()
        load()
    }

    private fun observeMembers() {
        viewModelScope.launch(Dispatchers.IO) {
            membersRepository.getAll().collect { members ->
                _uiState.value = _uiState.value.copy(members = members)
            }
        }
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) { loadMeetings() }
    }

    private suspend fun loadMeetings() {
        run {
            meetingGenerator.generateNext12Months()
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val meetings = meetingsRepository.getByTypeOnce("WEEKDAY").sortedBy { it.data_ }

            val items = meetings.map { m ->
                val date = LocalDate.parse(m.data_)
                val summary = midweekProgramsRepository.getByMeetingIdOnce(m.id)?.leitura_semanal
                    ?.takeIf { it.isNotBlank() }
                MidweekMeetingItem(
                    id = m.id,
                    date = date,
                    time = m.hora,
                    year = date.year,
                    month = date.monthNumber,
                    dateLabelShort = longDateLabel(date),
                    dateLabelLong = longDateLabelWithYear(date),
                    summary = summary,
                    isPast = date < today,
                )
            }

            val currentMonthItems = items.filter {
                it.year == _uiState.value.visibleYear && it.month == _uiState.value.visibleMonth
            }
            val firstSelectable = currentMonthItems.firstOrNull { !it.isPast } ?: currentMonthItems.firstOrNull()
            val initialSelected = firstSelectable?.id

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                today = today,
                allMeetings = items,
                selectedMeetingId = initialSelected,
            )
            initialSelected?.let { selectMeeting(it) }
        }
    }

    fun dismissImportResult() {
        _uiState.value = _uiState.value.copy(importResult = null)
    }

    /** Importa uma apostila (mwb) em PDF e pre-preenche as programacoes do periodo. */
    fun importApostila() {
        if (_uiState.value.importInProgress) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(importInProgress = true, importResult = null)
            try {
                val text = mwbImportService.pickPdfText()
                if (text == null) {
                    _uiState.value = _uiState.value.copy(importInProgress = false)
                    return@launch
                }
                val weeks = MwbParser.parse(text)
                val meetings = _uiState.value.allMeetings
                var applied = 0
                weeks.forEach { week ->
                    val meeting = matchMeeting(meetings, week) ?: return@forEach
                    val base = midweekProgramsRepository.getByMeetingIdOnce(meeting.id)?.toInput()
                        ?: MidweekProgramInput()
                    midweekProgramsRepository.upsert(meeting.id, applyWeek(base, week))
                    applied++
                }
                val previousSelected = _uiState.value.selectedMeetingId
                loadMeetings()
                previousSelected?.let { id ->
                    if (_uiState.value.allMeetings.any { it.id == id }) selectMeeting(id)
                }
                _uiState.value = _uiState.value.copy(
                    importInProgress = false,
                    importResult = when {
                        weeks.isEmpty() ->
                            "Nenhuma semana encontrada no PDF. Verifique se é a apostila (mwb) correta."
                        applied == 0 ->
                            "Nenhuma reunião do período corresponde às ${weeks.size} semanas do PDF."
                        else ->
                            "Importadas $applied de ${weeks.size} semanas. Agora só faltam as designações."
                    },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    importInProgress = false,
                    importResult = "Erro ao importar: ${e.message}",
                )
            }
        }
    }

    private fun matchMeeting(meetings: List<MidweekMeetingItem>, week: MwbWeek): MidweekMeetingItem? {
        val start = runCatching { LocalDate(week.ano, week.mes, week.diaInicio) }.getOrNull() ?: return null
        val end = start.plus(DatePeriod(days = 6))
        return meetings.firstOrNull { it.date >= start && it.date <= end }
    }

    private fun applyWeek(base: MidweekProgramInput, week: MwbWeek): MidweekProgramInput {
        fun m(i: Int) = week.ministerio.getOrNull(i)
        fun v(i: Int) = week.vida.getOrNull(i)
        return base.copy(
            leituraSemanal = week.leituraSemanal ?: base.leituraSemanal,
            canticoInicial = week.canticos.getOrNull(0) ?: base.canticoInicial,
            tesourosTitulo = week.tesourosTitulo ?: base.tesourosTitulo,
            min1Titulo = m(0)?.titulo ?: base.min1Titulo,
            min1Minutos = m(0)?.minutos ?: base.min1Minutos,
            min2Titulo = m(1)?.titulo ?: base.min2Titulo,
            min2Minutos = m(1)?.minutos ?: base.min2Minutos,
            min3Titulo = m(2)?.titulo ?: base.min3Titulo,
            min3Minutos = m(2)?.minutos ?: base.min3Minutos,
            min4Titulo = m(3)?.titulo ?: base.min4Titulo,
            min4Minutos = m(3)?.minutos ?: base.min4Minutos,
            canticoMeio = week.canticos.getOrNull(1) ?: base.canticoMeio,
            vida1Titulo = v(0)?.titulo ?: base.vida1Titulo,
            vida1Minutos = v(0)?.minutos ?: base.vida1Minutos,
            vida2Titulo = v(1)?.titulo ?: base.vida2Titulo,
            vida2Minutos = v(1)?.minutos ?: base.vida2Minutos,
            canticoFinal = week.canticos.getOrNull(2) ?: base.canticoFinal,
        )
    }

    fun exportProgramaPdf() {
        viewModelScope.launch(Dispatchers.IO) {
            pdfExportService.exportMidweekProgram(buildProgramData())
        }
    }

    fun exportDesignacoesS89() {
        viewModelScope.launch(Dispatchers.IO) {
            pdfExportService.exportMidweekAssignments(buildAssignmentsData())
        }
    }

    private fun memberName(map: Map<Long, Members>, id: Long?): String? {
        val m = id?.let { map[it] } ?: return null
        return "${m.nome} ${m.sobrenome}".trim()
    }

    private fun monthMeetings(): List<MidweekMeetingItem> {
        val s = _uiState.value
        return s.allMeetings
            .filter { it.year == s.visibleYear && it.month == s.visibleMonth }
            .sortedBy { it.date }
    }

    private fun addressFirstLine(raw: String): String? {
        val firstLine = raw.trim().lineSequence().firstOrNull()?.trim().orEmpty()
        return firstLine.ifEmpty { null }
    }

    private fun buildProgramData(): MidweekProgramPdfData {
        val s = _uiState.value
        val map = s.members.associateBy { it.id }
        val settings = settingsRepository.getSettingsOnce()
        val congregacao = settings?.nome?.ifBlank { null } ?: "Congregação"
        val subtitulo = settings?.endereco?.let { addressFirstLine(it) }
        val semanas = monthMeetings().map { m ->
            buildWeekPdf(m, midweekProgramsRepository.getByMeetingIdOnce(m.id), map)
        }
        val mesLabel = "${monthNamePtCapitalized(s.visibleMonth)} de ${s.visibleYear}"
        val slug = "vida-ministerio-${monthNamePt(s.visibleMonth)}-${s.visibleYear}"
        return MidweekProgramPdfData(congregacao, subtitulo, mesLabel, slug, semanas)
    }

    private fun buildWeekPdf(
        m: MidweekMeetingItem,
        p: Midweek_programs?,
        map: Map<Long, Members>,
    ): MidweekWeekPdf {
        var n = 3
        val ministerio = mutableListOf<MidweekPartPdf>()
        fun addMin(titulo: String?, minutos: String?, estId: Long?, ajuId: Long?) {
            if (!titulo.isNullOrBlank()) {
                n += 1
                ministerio.add(MidweekPartPdf(n, titulo, minutos, memberName(map, estId), memberName(map, ajuId)))
            }
        }
        addMin(p?.min1_titulo, p?.min1_minutos, p?.min1_estudante_id, p?.min1_ajudante_id)
        addMin(p?.min2_titulo, p?.min2_minutos, p?.min2_estudante_id, p?.min2_ajudante_id)
        addMin(p?.min3_titulo, p?.min3_minutos, p?.min3_estudante_id, p?.min3_ajudante_id)
        addMin(p?.min4_titulo, p?.min4_minutos, p?.min4_estudante_id, p?.min4_ajudante_id)

        val vida = mutableListOf<MidweekPartPdf>()
        fun addVida(titulo: String?, minutos: String?, pid: Long?) {
            if (!titulo.isNullOrBlank()) {
                n += 1
                vida.add(MidweekPartPdf(n, titulo, minutos, memberName(map, pid)))
            }
        }
        addVida(p?.vida1_titulo, p?.vida1_minutos, p?.vida1_id)
        addVida(p?.vida2_titulo, p?.vida2_minutos, p?.vida2_id)

        n += 1
        val estudo = MidweekPartPdf(
            n, "Estudio bíblico de la congregación", "30",
            memberName(map, p?.estudo_dirigente_id), memberName(map, p?.estudo_leitor_id),
        )

        return MidweekWeekPdf(
            periodo = m.dateLabelShort,
            leitura = p?.leitura_semanal?.takeIf { it.isNotBlank() }.orEmpty(),
            presidente = memberName(map, p?.presidente_id),
            oracaoInicial = memberName(map, p?.oracao_inicial_id),
            canticoInicial = p?.cantico_inicial?.takeIf { it.isNotBlank() },
            tesouros = MidweekPartPdf(
                1, p?.tesouros_titulo?.takeIf { it.isNotBlank() } ?: "Tesoros de la Biblia", "10",
                memberName(map, p?.tesouros_orador_id),
            ),
            joias = MidweekPartPdf(2, "Busquemos perlas escondidas", "10", memberName(map, p?.joias_id)),
            leituraBiblia = MidweekPartPdf(3, "Lectura de la biblia", "4", memberName(map, p?.leitura_biblia_id)),
            ministerio = ministerio,
            canticoMeio = p?.cantico_meio?.takeIf { it.isNotBlank() },
            vida = vida,
            estudo = estudo,
            canticoFinal = p?.cantico_final?.takeIf { it.isNotBlank() },
            oracaoFinal = memberName(map, p?.oracao_final_id),
        )
    }

    private fun buildAssignmentsData(): MidweekAssignmentsPdfData {
        val s = _uiState.value
        val map = s.members.associateBy { it.id }
        val settings = settingsRepository.getSettingsOnce()
        val congregacao = settings?.nome?.ifBlank { null } ?: "Congregação"
        val designacoes = mutableListOf<MidweekAssignmentPdf>()
        monthMeetings().forEach { m ->
            val p = midweekProgramsRepository.getByMeetingIdOnce(m.id) ?: return@forEach
            fun pad(v: Int) = v.toString().padStart(2, '0')
            val dateStr = "${pad(m.date.dayOfMonth)}/${pad(m.date.monthNumber)}/${m.date.year}"
            var n = 3
            memberName(map, p.leitura_biblia_id)?.let {
                designacoes.add(MidweekAssignmentPdf(it, null, dateStr, "3", false))
            }
            fun addMin(titulo: String?, estId: Long?, ajuId: Long?) {
                if (!titulo.isNullOrBlank()) {
                    n += 1
                    val est = memberName(map, estId) ?: return
                    designacoes.add(MidweekAssignmentPdf(est, memberName(map, ajuId), dateStr, n.toString(), false))
                }
            }
            addMin(p.min1_titulo, p.min1_estudante_id, p.min1_ajudante_id)
            addMin(p.min2_titulo, p.min2_estudante_id, p.min2_ajudante_id)
            addMin(p.min3_titulo, p.min3_estudante_id, p.min3_ajudante_id)
            addMin(p.min4_titulo, p.min4_estudante_id, p.min4_ajudante_id)
        }
        val mesLabel = "${monthNamePtCapitalized(s.visibleMonth)} de ${s.visibleYear}"
        val slug = "designacoes-s89-${monthNamePt(s.visibleMonth)}-${s.visibleYear}"
        return MidweekAssignmentsPdfData(congregacao, mesLabel, slug, designacoes)
    }

    fun selectMeeting(meetingId: Long) {
        val meeting = _uiState.value.allMeetings.firstOrNull { it.id == meetingId } ?: return
        _uiState.value = _uiState.value.copy(selectedMeetingId = meetingId)
        viewModelScope.launch(Dispatchers.IO) {
            suppressAutosave = true
            val existing = midweekProgramsRepository.getByMeetingIdOnce(meeting.id)
            _uiState.value = _uiState.value.copy(
                form = existing?.toInput() ?: MidweekProgramInput(),
                isReadOnly = meeting.isPast,
            )
            suppressAutosave = false
        }
    }

    fun showPreviousMonth() {
        val s = _uiState.value
        val (y, m) = shiftMonth(s.visibleYear, s.visibleMonth, -1)
        _uiState.value = s.copy(visibleYear = y, visibleMonth = m)
    }

    fun showNextMonth() {
        val s = _uiState.value
        val (y, m) = shiftMonth(s.visibleYear, s.visibleMonth, 1)
        _uiState.value = s.copy(visibleYear = y, visibleMonth = m)
    }

    fun showCurrentMonth() {
        val today = _uiState.value.today
        _uiState.value = _uiState.value.copy(visibleYear = today.year, visibleMonth = today.monthNumber)
    }

    /** Aplica uma alteracao no formulario e agenda o salvamento automatico. */
    fun updateForm(transform: (MidweekProgramInput) -> MidweekProgramInput) {
        if (_uiState.value.isReadOnly) return
        val newForm = transform(_uiState.value.form)
        _uiState.value = _uiState.value.copy(
            form = newForm,
            allMeetings = updateSummary(_uiState.value.allMeetings, _uiState.value.selectedMeetingId, newForm.leituraSemanal),
        )
        scheduleAutosave()
    }

    private fun updateSummary(
        list: List<MidweekMeetingItem>,
        selectedId: Long?,
        leitura: String?,
    ): List<MidweekMeetingItem> {
        if (selectedId == null) return list
        return list.map {
            if (it.id == selectedId) it.copy(summary = leitura?.takeIf { t -> t.isNotBlank() }) else it
        }
    }

    private fun scheduleAutosave() {
        if (suppressAutosave || _uiState.value.isReadOnly) return
        val meetingId = _uiState.value.selectedMeetingId ?: return
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            midweekProgramsRepository.upsert(meetingId, _uiState.value.form.normalized())
        }
    }

    private fun shiftMonth(year: Int, month: Int, delta: Int): Pair<Int, Int> {
        val zeroBased = month - 1 + delta
        val newYear = year + Math.floorDiv(zeroBased, 12)
        val newMonth = Math.floorMod(zeroBased, 12) + 1
        return newYear to newMonth
    }
}

/** Converte campos de texto em branco para null antes de persistir. */
private fun MidweekProgramInput.normalized(): MidweekProgramInput = copy(
    leituraSemanal = leituraSemanal?.ifBlank { null },
    canticoInicial = canticoInicial?.ifBlank { null },
    tesourosTitulo = tesourosTitulo?.ifBlank { null },
    min1Titulo = min1Titulo?.ifBlank { null },
    min1Minutos = min1Minutos?.ifBlank { null },
    min2Titulo = min2Titulo?.ifBlank { null },
    min2Minutos = min2Minutos?.ifBlank { null },
    min3Titulo = min3Titulo?.ifBlank { null },
    min3Minutos = min3Minutos?.ifBlank { null },
    min4Titulo = min4Titulo?.ifBlank { null },
    min4Minutos = min4Minutos?.ifBlank { null },
    canticoMeio = canticoMeio?.ifBlank { null },
    vida1Titulo = vida1Titulo?.ifBlank { null },
    vida1Minutos = vida1Minutos?.ifBlank { null },
    vida2Titulo = vida2Titulo?.ifBlank { null },
    vida2Minutos = vida2Minutos?.ifBlank { null },
    canticoFinal = canticoFinal?.ifBlank { null },
)

private fun Midweek_programs.toInput(): MidweekProgramInput = MidweekProgramInput(
    leituraSemanal = leitura_semanal,
    presidenteId = presidente_id,
    conselheiroId = conselheiro_id,
    canticoInicial = cantico_inicial,
    oracaoInicialId = oracao_inicial_id,
    tesourosTitulo = tesouros_titulo,
    tesourosOradorId = tesouros_orador_id,
    joiasId = joias_id,
    leituraBibliaId = leitura_biblia_id,
    min1Titulo = min1_titulo,
    min1Minutos = min1_minutos,
    min1EstudanteId = min1_estudante_id,
    min1AjudanteId = min1_ajudante_id,
    min2Titulo = min2_titulo,
    min2Minutos = min2_minutos,
    min2EstudanteId = min2_estudante_id,
    min2AjudanteId = min2_ajudante_id,
    min3Titulo = min3_titulo,
    min3Minutos = min3_minutos,
    min3EstudanteId = min3_estudante_id,
    min3AjudanteId = min3_ajudante_id,
    min4Titulo = min4_titulo,
    min4Minutos = min4_minutos,
    min4EstudanteId = min4_estudante_id,
    min4AjudanteId = min4_ajudante_id,
    canticoMeio = cantico_meio,
    vida1Titulo = vida1_titulo,
    vida1Minutos = vida1_minutos,
    vida1Id = vida1_id,
    vida2Titulo = vida2_titulo,
    vida2Minutos = vida2_minutos,
    vida2Id = vida2_id,
    estudoDirigenteId = estudo_dirigente_id,
    estudoLeitorId = estudo_leitor_id,
    canticoFinal = cantico_final,
    oracaoFinalId = oracao_final_id,
)
