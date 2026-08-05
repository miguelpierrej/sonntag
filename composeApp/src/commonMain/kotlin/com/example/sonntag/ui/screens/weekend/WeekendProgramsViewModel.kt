package com.example.sonntag.ui.screens.weekend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonntag.data.repos.MembersRepository
import com.example.sonntag.data.repos.MeetingsRepository
import com.example.sonntag.data.repos.SettingsRepository
import com.example.sonntag.data.repos.TalkOutlinesRepository
import com.example.sonntag.data.repos.WeekendProgramsRepository
import com.example.sonntag.data.sqldelight.Members
import com.example.sonntag.domain.models.TalkOutline
import com.example.sonntag.domain.usecases.MeetingGenerator
import com.example.sonntag.imports.S34ImportService
import com.example.sonntag.pdf.MeetingProgramPdfData
import com.example.sonntag.pdf.MonthlyProgramPdfData
import com.example.sonntag.pdf.PdfExportService
import com.example.sonntag.pdf.PdfMeetingLine
import com.example.sonntag.pdf.weekendPdfStrings
import com.example.sonntag.ui.util.slugMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
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

/**
 * Uma reuniao com a sua programacao. Os dados ficam no item, e nao numa selecao
 * unica, porque a tela mostra todas as reunioes do mes editaveis ao mesmo tempo —
 * o mesmo modelo da tela de audio/video.
 */
data class WeekendMeetingItem(
    val id: Long,
    val date: LocalDate,
    val time: String,
    val year: Int,
    val month: Int,
    val dateLabelShort: String,
    val dateLabelLong: String,
    val isPast: Boolean,
    val tituloDiscurso: String = "",
    val oradorId: Long? = null,
    val oradorNome: String = "",
    val presidenteId: Long? = null,
    val dirigenteId: Long? = null,
    val leitorId: Long? = null,
) {
    val titleSummary: String? get() = tituloDiscurso.takeIf { it.isNotBlank() }

    /** Quantos dos cinco campos ja estao preenchidos. */
    val filledCount: Int
        get() = listOf(
            tituloDiscurso.isNotBlank(),
            oradorId != null || oradorNome.isNotBlank(),
            presidenteId != null,
            dirigenteId != null,
            leitorId != null,
        ).count { it }
}

data class WeekendProgramsUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate(1970, 1, 1),
    val visibleYear: Int = 1970,
    val visibleMonth: Int = 1,
    val allMeetings: List<WeekendMeetingItem> = emptyList(),
    val selectedMeetingId: Long? = null,
    val members: List<Members> = emptyList(),
    val talkOutlines: List<TalkOutline> = emptyList(),
    val importInProgress: Boolean = false,
    val importResult: String? = null,
)

class WeekendProgramsViewModel(
    private val meetingsRepository: MeetingsRepository,
    private val membersRepository: MembersRepository,
    private val weekendProgramsRepository: WeekendProgramsRepository,
    private val settingsRepository: SettingsRepository,
    private val pdfExportService: PdfExportService,
    private val meetingGenerator: MeetingGenerator,
    private val localeController: com.example.sonntag.i18n.LocaleController,
    private val talkOutlinesRepository: TalkOutlinesRepository,
    private val s34ImportService: S34ImportService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeekendProgramsUiState())
    val uiState: StateFlow<WeekendProgramsUiState> = _uiState.asStateFlow()
    /** Uma gravacao pendente por reuniao: os cartoes sao editaveis ao mesmo tempo. */
    private val autosaveJobs = mutableMapOf<Long, Job>()

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
                val porId = members.associateBy { it.id }
                _uiState.value = _uiState.value.copy(
                    members = members,
                    // Renomear um membro precisa refletir no orador ja escolhido.
                    allMeetings = _uiState.value.allMeetings.map { item ->
                        val nome = item.oradorId?.let { porId[it] }?.let { "${it.nome} ${it.sobrenome}" }
                        if (nome != null) item.copy(oradorNome = nome) else item
                    },
                )
            }
        }
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            meetingGenerator.generateNext12Months()
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val meetings = meetingsRepository.getByTypeOnce("WEEKEND").sortedBy { it.data_ }

            val membrosPorId = _uiState.value.members.associateBy { it.id }
            val items = meetings.map { m ->
                val date = LocalDate.parse(m.data_)
                val p = weekendProgramsRepository.getByMeetingIdOnce(m.id)
                WeekendMeetingItem(
                    id = m.id,
                    date = date,
                    time = m.hora,
                    year = date.year,
                    month = date.monthNumber,
                    dateLabelShort = localeController.translator.longDate(date),
                    dateLabelLong = localeController.translator.longDateWithYear(date),
                    isPast = date < today,
                    tituloDiscurso = p?.titulo_discurso.orEmpty(),
                    oradorId = p?.orador_id,
                    oradorNome = p?.orador_id?.let { membrosPorId[it] }
                        ?.let { "${it.nome} ${it.sobrenome}" }
                        ?: p?.orador_nome.orEmpty(),
                    presidenteId = p?.presidente_id,
                    dirigenteId = p?.dirigente_id,
                    leitorId = p?.leitor_id,
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
            loadTalkOutlines()
        }
    }

    private fun loadTalkOutlines() {
        _uiState.value = _uiState.value.copy(talkOutlines = talkOutlinesRepository.getAllOnce())
    }

    fun dismissImportResult() {
        _uiState.value = _uiState.value.copy(importResult = null)
    }

    /** Importa os titulos dos bosquejos de um arquivo S-34 (.jwpub). */
    fun importS34() {
        if (_uiState.value.importInProgress) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(importInProgress = true, importResult = null)
            try {
                val t = localeController.translator
                val outlines = s34ImportService.pickTalkOutlines(
                    t("Selecionar S-34 (.jwpub)"),
                    t("Publicações JWPUB"),
                )
                if (outlines == null) {
                    _uiState.value = _uiState.value.copy(importInProgress = false)
                    return@launch
                }
                if (outlines.isNotEmpty()) {
                    talkOutlinesRepository.replaceAll(outlines)
                    loadTalkOutlines()
                }
                _uiState.value = _uiState.value.copy(
                    importInProgress = false,
                    importResult = if (outlines.isEmpty()) {
                        t("Nenhum bosquejo encontrado no arquivo. Verifique se é o S-34 (.jwpub) correto.")
                    } else {
                        t("Importados {0} bosquejos. Agora eles aparecem na lista do título do discurso.", outlines.size)
                    },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    importInProgress = false,
                    importResult = localeController.translator("Erro ao importar: {0}", e.message),
                )
            }
        }
    }

    /** Marca a reuniao usada pela exportacao "Esta reuniao". */
    fun selectMeeting(meetingId: Long) {
        _uiState.value = _uiState.value.copy(selectedMeetingId = meetingId)
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

    fun onTituloChanged(meetingId: Long, value: String) =
        edit(meetingId) { it.copy(tituloDiscurso = value) }

    fun onOradorChanged(meetingId: Long, id: Long?, nome: String) =
        edit(meetingId) { it.copy(oradorId = id, oradorNome = nome) }

    fun onPresidenteChanged(meetingId: Long, value: Long?) =
        edit(meetingId) { it.copy(presidenteId = value) }

    fun onDirigenteChanged(meetingId: Long, value: Long?) =
        edit(meetingId) { it.copy(dirigenteId = value) }

    fun onLeitorChanged(meetingId: Long, value: Long?) =
        edit(meetingId) { it.copy(leitorId = value) }

    /** Altera uma reuniao da lista e agenda a gravacao so dela. */
    private fun edit(meetingId: Long, bloco: (WeekendMeetingItem) -> WeekendMeetingItem) {
        val atual = _uiState.value.allMeetings.firstOrNull { it.id == meetingId } ?: return
        if (atual.isPast) return
        _uiState.value = _uiState.value.copy(
            selectedMeetingId = meetingId,
            allMeetings = _uiState.value.allMeetings.map { if (it.id == meetingId) bloco(it) else it },
        )
        scheduleAutosave(meetingId)
    }

    private fun scheduleAutosave(meetingId: Long) {
        autosaveJobs.remove(meetingId)?.cancel()
        autosaveJobs[meetingId] = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            val item = _uiState.value.allMeetings.firstOrNull { it.id == meetingId } ?: return@launch
            weekendProgramsRepository.upsert(
                meetingId = meetingId,
                tituloDiscurso = item.tituloDiscurso.ifBlank { null },
                oradorId = item.oradorId,
                oradorNome = item.oradorNome.ifBlank { null },
                presidenteId = item.presidenteId,
                dirigenteId = item.dirigenteId,
                leitorId = item.leitorId,
            )
        }
    }

    fun exportSelectedMeetingPdf() {
        viewModelScope.launch(Dispatchers.IO) {
            val data = buildSelectedMeetingExport() ?: return@launch
            pdfExportService.exportMeetingProgram(data)
        }
    }

    fun exportSelectedMeetingPng() {
        viewModelScope.launch(Dispatchers.IO) {
            val data = buildSelectedMeetingExport() ?: return@launch
            pdfExportService.exportMeetingProgramPng(data)
        }
    }

    fun exportVisibleMonthPdf() {
        viewModelScope.launch(Dispatchers.IO) {
            val data = buildVisibleMonthExport()
            pdfExportService.exportMonthlyProgram(data)
        }
    }

    fun exportVisibleMonthPng() {
        viewModelScope.launch(Dispatchers.IO) {
            val data = buildVisibleMonthExport()
            pdfExportService.exportMonthlyProgramPng(data)
        }
    }

    private fun buildSelectedMeetingExport(): MeetingProgramPdfData? {
        val selectedId = _uiState.value.selectedMeetingId ?: return null
        val meeting = meetingsRepository.getByIdOnce(selectedId) ?: return null
        if (meeting.tipo != "WEEKEND") return null
        val membersMap = _uiState.value.members.associateBy { it.id }
        val program = weekendProgramsRepository.getByMeetingIdOnce(selectedId)
        val labels = weekendPdfStrings(localeController.current)
        val congregation = settingsRepository.getSettingsOnce()?.nome?.takeIf { it.isNotBlank() }
            ?: labels.common.congregacao
        val date = LocalDate.parse(meeting.data_)
        return MeetingProgramPdfData(
            congregacao = congregation,
            dateLabel = localeController.translator.longDateWithYear(date),
            hora = meeting.hora,
            fileSlug = "programacao-reuniao-${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}",
            tituloDiscurso = program?.titulo_discurso?.takeIf { it.isNotBlank() },
            orador = oradorNameOrNull(program, membersMap),
            presidente = memberNameOrNull(program?.presidente_id, membersMap),
            dirigenteEstudo = memberNameOrNull(program?.dirigente_id, membersMap),
            leitor = memberNameOrNull(program?.leitor_id, membersMap),
            labels = labels,
        )
    }

    private fun buildVisibleMonthExport(): MonthlyProgramPdfData {
        val s = _uiState.value
        val monthStart = LocalDate(s.visibleYear, s.visibleMonth, 1)
        val nextMonth = if (s.visibleMonth == 12) {
            LocalDate(s.visibleYear + 1, 1, 1)
        } else {
            LocalDate(s.visibleYear, s.visibleMonth + 1, 1)
        }
        val monthEnd = nextMonth.plus(DatePeriod(days = -1))

        val membersMap = s.members.associateBy { it.id }
        val meetings = meetingsRepository
            .getByDateRangeOnce(monthStart.toString(), monthEnd.toString())
            .filter { it.tipo == "WEEKEND" }
        val lines = meetings.sortedBy { it.data_ }.map { meeting ->
            val program = weekendProgramsRepository.getByMeetingIdOnce(meeting.id)
            val date = LocalDate.parse(meeting.data_)
            PdfMeetingLine(
                dateLabel = localeController.translator.longDateWithYear(date),
                hora = meeting.hora,
                tituloDiscurso = program?.titulo_discurso?.takeIf { it.isNotBlank() },
                orador = oradorNameOrNull(program, membersMap),
                presidente = memberNameOrNull(program?.presidente_id, membersMap),
                dirigenteEstudo = memberNameOrNull(program?.dirigente_id, membersMap),
                leitor = memberNameOrNull(program?.leitor_id, membersMap),
            )
        }

        val labels = weekendPdfStrings(localeController.current)
        val congregation = settingsRepository.getSettingsOnce()?.nome?.takeIf { it.isNotBlank() }
            ?: labels.common.congregacao
        val monthLabel = localeController.translator.monthYearLabel(s.visibleMonth, s.visibleYear)
        val slug = "programacao-${slugMonth(localeController.translator, s.visibleMonth)}-${s.visibleYear}"
        return MonthlyProgramPdfData(
            congregacao = congregation,
            mesLabel = monthLabel,
            fileSlug = slug,
            reunioes = lines,
            labels = labels,
        )
    }

    private fun shiftMonth(year: Int, month: Int, delta: Int): Pair<Int, Int> {
        val zeroBased = month - 1 + delta
        val newYear = year + Math.floorDiv(zeroBased, 12)
        val newMonth = Math.floorMod(zeroBased, 12) + 1
        return newYear to newMonth
    }

    private fun oradorNameOrNull(
        program: com.example.sonntag.data.sqldelight.Weekend_programs?,
        map: Map<Long, Members>,
    ): String? {
        if (program == null) return null
        program.orador_nome?.takeIf { it.isNotBlank() }?.let { return it }
        return memberNameOrNull(program.orador_id, map)
    }

    private fun memberNameOrNull(id: Long?, map: Map<Long, Members>): String? {
        if (id == null) return null
        val member = map[id] ?: return null
        return "${member.nome} ${member.sobrenome}"
    }
}
