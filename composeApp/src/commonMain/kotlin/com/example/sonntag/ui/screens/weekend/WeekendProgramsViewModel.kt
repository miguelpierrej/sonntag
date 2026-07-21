package com.example.sonntag.ui.screens.weekend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonntag.data.repos.MembersRepository
import com.example.sonntag.data.repos.MeetingsRepository
import com.example.sonntag.data.repos.SettingsRepository
import com.example.sonntag.data.repos.WeekendProgramsRepository
import com.example.sonntag.data.sqldelight.Members
import com.example.sonntag.domain.usecases.MeetingGenerator
import com.example.sonntag.pdf.MeetingProgramPdfData
import com.example.sonntag.pdf.MonthlyProgramPdfData
import com.example.sonntag.pdf.PdfExportService
import com.example.sonntag.pdf.PdfMeetingLine
import com.example.sonntag.ui.util.longDateLabel
import com.example.sonntag.ui.util.longDateLabelWithYear
import com.example.sonntag.ui.util.monthNamePt
import com.example.sonntag.ui.util.monthNamePtCapitalized
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

data class WeekendMeetingItem(
    val id: Long,
    val date: LocalDate,
    val time: String,
    val year: Int,
    val month: Int,
    val dateLabelShort: String,
    val dateLabelLong: String,
    val titleSummary: String?,
    val isPast: Boolean,
)

data class WeekendProgramsUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate(1970, 1, 1),
    val visibleYear: Int = 1970,
    val visibleMonth: Int = 1,
    val allMeetings: List<WeekendMeetingItem> = emptyList(),
    val selectedMeetingId: Long? = null,
    val members: List<Members> = emptyList(),
    val tituloDiscurso: String = "",
    val oradorId: Long? = null,
    val oradorNome: String = "",
    val presidenteId: Long? = null,
    val dirigenteId: Long? = null,
    val leitorId: Long? = null,
    val isReadOnly: Boolean = false,
)

class WeekendProgramsViewModel(
    private val meetingsRepository: MeetingsRepository,
    private val membersRepository: MembersRepository,
    private val weekendProgramsRepository: WeekendProgramsRepository,
    private val settingsRepository: SettingsRepository,
    private val pdfExportService: PdfExportService,
    private val meetingGenerator: MeetingGenerator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeekendProgramsUiState())
    val uiState: StateFlow<WeekendProgramsUiState> = _uiState.asStateFlow()
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
                val current = _uiState.value
                val updatedOradorNome = if (current.oradorId != null) {
                    members.firstOrNull { it.id == current.oradorId }
                        ?.let { "${it.nome} ${it.sobrenome}" }
                        ?: current.oradorNome
                } else {
                    current.oradorNome
                }
                _uiState.value = current.copy(
                    members = members,
                    oradorNome = updatedOradorNome,
                )
            }
        }
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            meetingGenerator.generateNext12Months()
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val meetings = meetingsRepository.getByTypeOnce("WEEKEND").sortedBy { it.data_ }

            val items = meetings.map { m ->
                val date = LocalDate.parse(m.data_)
                val titulo = weekendProgramsRepository.getByMeetingIdOnce(m.id)?.titulo_discurso
                    ?.takeIf { it.isNotBlank() }
                WeekendMeetingItem(
                    id = m.id,
                    date = date,
                    time = m.hora,
                    year = date.year,
                    month = date.monthNumber,
                    dateLabelShort = longDateLabel(date),
                    dateLabelLong = longDateLabelWithYear(date),
                    titleSummary = titulo,
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

    fun selectMeeting(meetingId: Long) {
        val meeting = _uiState.value.allMeetings.firstOrNull { it.id == meetingId } ?: return
        _uiState.value = _uiState.value.copy(selectedMeetingId = meetingId)
        viewModelScope.launch(Dispatchers.IO) {
            suppressAutosave = true
            val existing = weekendProgramsRepository.getByMeetingIdOnce(meeting.id)
            val resolvedOradorNome = when {
                existing?.orador_nome?.isNotBlank() == true -> existing.orador_nome.orEmpty()
                existing?.orador_id != null -> {
                    val m = _uiState.value.members.firstOrNull { it.id == existing.orador_id }
                    if (m != null) "${m.nome} ${m.sobrenome}" else ""
                }
                else -> ""
            }
            _uiState.value = _uiState.value.copy(
                tituloDiscurso = existing?.titulo_discurso.orEmpty(),
                oradorId = existing?.orador_id,
                oradorNome = resolvedOradorNome,
                presidenteId = existing?.presidente_id,
                dirigenteId = existing?.dirigente_id,
                leitorId = existing?.leitor_id,
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

    fun onTituloChanged(value: String) {
        val updated = _uiState.value.copy(
            tituloDiscurso = value,
            allMeetings = updateMeetingTitle(_uiState.value.allMeetings, _uiState.value.selectedMeetingId, value),
        )
        _uiState.value = updated
        scheduleAutosave()
    }

    private fun updateMeetingTitle(
        list: List<WeekendMeetingItem>,
        selectedId: Long?,
        newTitle: String,
    ): List<WeekendMeetingItem> {
        if (selectedId == null) return list
        return list.map {
            if (it.id == selectedId) it.copy(titleSummary = newTitle.takeIf { t -> t.isNotBlank() }) else it
        }
    }

    fun onOradorChanged(id: Long?, nome: String) {
        _uiState.value = _uiState.value.copy(oradorId = id, oradorNome = nome)
        scheduleAutosave()
    }

    fun onPresidenteChanged(value: Long?) {
        _uiState.value = _uiState.value.copy(presidenteId = value)
        scheduleAutosave()
    }

    fun onDirigenteChanged(value: Long?) {
        _uiState.value = _uiState.value.copy(dirigenteId = value)
        scheduleAutosave()
    }

    fun onLeitorChanged(value: Long?) {
        _uiState.value = _uiState.value.copy(leitorId = value)
        scheduleAutosave()
    }

    private fun scheduleAutosave() {
        if (suppressAutosave || _uiState.value.isReadOnly) return
        val meetingId = _uiState.value.selectedMeetingId ?: return
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            val s = _uiState.value
            weekendProgramsRepository.upsert(
                meetingId = meetingId,
                tituloDiscurso = s.tituloDiscurso.ifBlank { null },
                oradorId = s.oradorId,
                oradorNome = s.oradorNome.ifBlank { null },
                presidenteId = s.presidenteId,
                dirigenteId = s.dirigenteId,
                leitorId = s.leitorId,
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
        val congregation = settingsRepository.getSettingsOnce()?.nome ?: "Congregação"
        val date = LocalDate.parse(meeting.data_)
        return MeetingProgramPdfData(
            congregacao = congregation,
            dateLabel = longDateLabelWithYear(date),
            hora = meeting.hora,
            fileSlug = "programacao-reuniao-${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}",
            tituloDiscurso = program?.titulo_discurso?.takeIf { it.isNotBlank() },
            orador = oradorNameOrNull(program, membersMap),
            presidente = memberNameOrNull(program?.presidente_id, membersMap),
            dirigenteEstudo = memberNameOrNull(program?.dirigente_id, membersMap),
            leitor = memberNameOrNull(program?.leitor_id, membersMap),
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
                dateLabel = longDateLabelWithYear(date),
                hora = meeting.hora,
                tituloDiscurso = program?.titulo_discurso?.takeIf { it.isNotBlank() },
                orador = oradorNameOrNull(program, membersMap),
                presidente = memberNameOrNull(program?.presidente_id, membersMap),
                dirigenteEstudo = memberNameOrNull(program?.dirigente_id, membersMap),
                leitor = memberNameOrNull(program?.leitor_id, membersMap),
            )
        }

        val congregation = settingsRepository.getSettingsOnce()?.nome ?: "Congregação"
        val monthLabel = "${monthNamePtCapitalized(s.visibleMonth)} de ${s.visibleYear}"
        val slug = "programacao-${monthNamePt(s.visibleMonth)}-${s.visibleYear}"
        return MonthlyProgramPdfData(
            congregacao = congregation,
            mesLabel = monthLabel,
            fileSlug = slug,
            reunioes = lines,
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
