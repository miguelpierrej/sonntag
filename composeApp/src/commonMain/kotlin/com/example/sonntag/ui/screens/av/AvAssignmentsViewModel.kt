package com.example.sonntag.ui.screens.av

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonntag.data.repos.AvAssignmentsRepository
import com.example.sonntag.data.repos.EventsRepository
import com.example.sonntag.data.repos.MeetingsRepository
import com.example.sonntag.data.repos.MembersRepository
import com.example.sonntag.data.repos.MidweekProgramsRepository
import com.example.sonntag.data.repos.SettingsRepository
import com.example.sonntag.data.repos.WeekendProgramsRepository
import com.example.sonntag.data.sqldelight.Members
import com.example.sonntag.domain.usecases.CongregationEvent
import com.example.sonntag.domain.usecases.EventSchedule
import com.example.sonntag.domain.usecases.MeetingGenerator
import com.example.sonntag.domain.usecases.toDomain
import com.example.sonntag.i18n.LocaleController
import com.example.sonntag.pdf.AvScheduleLine
import com.example.sonntag.pdf.AvSchedulePdfData
import com.example.sonntag.pdf.PdfExportService
import com.example.sonntag.pdf.avPdfStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** As oito funcoes tecnicas designaveis em cada reuniao. */
enum class AvRole {
    AUDIO,
    VIDEO,
    PLATAFORMA1,
    PLATAFORMA2,
    MICROFONE1,
    MICROFONE2,
    ACOMODADOR1,
    ACOMODADOR2,
}

data class AvMeetingItem(
    val meetingId: Long,
    val date: LocalDate,
    val time: String,
    val year: Int,
    val month: Int,
    val isWeekend: Boolean,
    val dateLabel: String,
    val isPast: Boolean,
    /** Preenchido quando um evento toma o lugar desta reuniao: ninguem e designado. */
    val event: CongregationEvent? = null,
    val assignments: Map<AvRole, Long?> = emptyMap(),
    /**
     * Funcoes que cada membro ja ocupa na programacao desta mesma reuniao
     * (fim de semana: orador/presidente/dirigente/leitor; meio de semana: presidente).
     * Os rotulos sao chaves em portugues, traduzidas na UI.
     */
    val programRoles: Map<Long, List<String>> = emptyMap(),
) {
    fun memberId(role: AvRole): Long? = assignments[role]

    /** Rotulos das designacoes do programa que colidem com a funcao tecnica escolhida. */
    fun conflictsFor(role: AvRole): List<String> {
        val memberId = memberId(role) ?: return emptyList()
        return programRoles[memberId].orEmpty()
    }

    /** Quantas das oito funcoes ja tem alguem designado. */
    val filledCount: Int get() = assignments.count { it.value != null }

    /** Quantas funcoes tecnicas estao com alguem ja designado na programacao. */
    val conflictCount: Int get() = AvRole.entries.count { conflictsFor(it).isNotEmpty() }
}

data class AvAssignmentsUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate(1970, 1, 1),
    val visibleYear: Int = 1970,
    val visibleMonth: Int = 1,
    val meetings: List<AvMeetingItem> = emptyList(),
    val members: List<Members> = emptyList(),
)

class AvAssignmentsViewModel(
    private val meetingsRepository: MeetingsRepository,
    private val membersRepository: MembersRepository,
    private val avAssignmentsRepository: AvAssignmentsRepository,
    private val weekendProgramsRepository: WeekendProgramsRepository,
    private val midweekProgramsRepository: MidweekProgramsRepository,
    private val settingsRepository: SettingsRepository,
    private val meetingGenerator: MeetingGenerator,
    private val pdfExportService: PdfExportService,
    private val localeController: LocaleController,
    private val eventsRepository: EventsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AvAssignmentsUiState())
    val uiState: StateFlow<AvAssignmentsUiState> = _uiState.asStateFlow()

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
                _uiState.value = _uiState.value.copy(members = members)
            }
        }
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            meetingGenerator.generateNext12Months()
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val translator = localeController.translator
            val events = EventSchedule(eventsRepository.getAllOnce().map { it.toDomain() })

            val items = meetingsRepository.getAllOnce()
                .sortedWith(compareBy({ it.data_ }, { it.hora }))
                .map { meeting ->
                    val date = LocalDate.parse(meeting.data_)
                    val saved = avAssignmentsRepository.getByMeetingIdOnce(meeting.id)
                    val isWeekend = meeting.tipo == "WEEKEND"
                    AvMeetingItem(
                        meetingId = meeting.id,
                        date = date,
                        time = meeting.hora,
                        year = date.year,
                        month = date.monthNumber,
                        isWeekend = isWeekend,
                        dateLabel = translator.longDate(date),
                        isPast = date < today,
                        event = events.replacing(date, meeting.tipo),
                        programRoles = programRolesFor(meeting.id, isWeekend),
                        assignments = mapOf(
                            AvRole.AUDIO to saved?.audio_id,
                            AvRole.VIDEO to saved?.video_id,
                            AvRole.PLATAFORMA1 to saved?.plataforma1_id,
                            AvRole.PLATAFORMA2 to saved?.plataforma2_id,
                            AvRole.MICROFONE1 to saved?.microfone1_id,
                            AvRole.MICROFONE2 to saved?.microfone2_id,
                            AvRole.ACOMODADOR1 to saved?.acomodador1_id,
                            AvRole.ACOMODADOR2 to saved?.acomodador2_id,
                        ),
                    )
                }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                today = today,
                meetings = items,
            )
        }
    }

    /**
     * Quem ja tem designacao na programacao desta reuniao, por membro. Um mesmo
     * membro pode acumular mais de uma funcao (ex.: presidente e leitor).
     */
    private fun programRolesFor(meetingId: Long, isWeekend: Boolean): Map<Long, List<String>> {
        val pairs: List<Pair<Long, String>> = if (isWeekend) {
            val program = weekendProgramsRepository.getByMeetingIdOnce(meetingId) ?: return emptyMap()
            listOfNotNull(
                program.orador_id?.let { it to "Orador" },
                program.presidente_id?.let { it to "Presidente" },
                program.dirigente_id?.let { it to "Dirigente do estudo" },
                program.leitor_id?.let { it to "Leitor" },
            )
        } else {
            val program = midweekProgramsRepository.getByMeetingIdOnce(meetingId) ?: return emptyMap()
            listOfNotNull(program.presidente_id?.let { it to "Presidente" })
        }
        return pairs
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, roles) -> roles.distinct() }
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

    fun onRoleChanged(meetingId: Long, role: AvRole, memberId: Long?) {
        _uiState.value = _uiState.value.copy(
            meetings = _uiState.value.meetings.map { item ->
                if (item.meetingId == meetingId) {
                    item.copy(assignments = item.assignments + (role to memberId))
                } else {
                    item
                }
            },
        )
        scheduleAutosave(meetingId)
    }

    private fun scheduleAutosave(meetingId: Long) {
        autosaveJobs.remove(meetingId)?.cancel()
        autosaveJobs[meetingId] = viewModelScope.launch(Dispatchers.IO) {
            delay(400)
            val item = _uiState.value.meetings.firstOrNull { it.meetingId == meetingId } ?: return@launch
            if (item.assignments.values.all { it == null }) {
                avAssignmentsRepository.delete(meetingId)
            } else {
                avAssignmentsRepository.upsert(
                    meetingId = meetingId,
                    audioId = item.memberId(AvRole.AUDIO),
                    videoId = item.memberId(AvRole.VIDEO),
                    plataforma1Id = item.memberId(AvRole.PLATAFORMA1),
                    plataforma2Id = item.memberId(AvRole.PLATAFORMA2),
                    microfone1Id = item.memberId(AvRole.MICROFONE1),
                    microfone2Id = item.memberId(AvRole.MICROFONE2),
                    acomodador1Id = item.memberId(AvRole.ACOMODADOR1),
                    acomodador2Id = item.memberId(AvRole.ACOMODADOR2),
                )
            }
        }
    }

    fun exportVisibleMonthPdf() {
        viewModelScope.launch(Dispatchers.IO) {
            pdfExportService.exportAvSchedule(buildVisibleMonthExport())
        }
    }

    private fun buildVisibleMonthExport(): AvSchedulePdfData {
        val state = _uiState.value
        val settings = settingsRepository.getSettingsOnce()
        val translator = localeController.translator
        val labels = avPdfStrings(localeController.current)
        val namesById = state.members.associate { it.id to "${it.nome} ${it.sobrenome}" }

        fun name(item: AvMeetingItem, role: AvRole): String? = item.memberId(role)?.let { namesById[it] }
        fun names(item: AvMeetingItem, vararg roles: AvRole): List<String> = roles.mapNotNull { name(item, it) }

        val visible = state.meetings.filter {
            it.year == state.visibleYear && it.month == state.visibleMonth
        }
        val monthLabel = translator.monthYearLabel(state.visibleMonth, state.visibleYear)
        val monthPadded = state.visibleMonth.toString().padStart(2, '0')

        return AvSchedulePdfData(
            // So o nome: no cartao do cabecalho o endereco nao entra.
            congregacao = settings?.nome?.takeIf { it.isNotBlank() } ?: translator("Congregação"),
            endereco = settings?.endereco?.takeIf { it.isNotBlank() },
            mesLabel = monthLabel,
            fileSlug = "ava_${state.visibleYear}-$monthPadded",
            reunioes = visible.map { item ->
                AvScheduleLine(
                    dataLabel = formatShortDate(item.date),
                    tipoLabel = if (item.isWeekend) labels.reuniaoFimSemana else labels.reuniaoMeioSemana,
                    audio = name(item, AvRole.AUDIO),
                    video = name(item, AvRole.VIDEO),
                    plataforma = names(item, AvRole.PLATAFORMA1, AvRole.PLATAFORMA2),
                    microfones = names(item, AvRole.MICROFONE1, AvRole.MICROFONE2),
                    acomodadores = names(item, AvRole.ACOMODADOR1, AvRole.ACOMODADOR2),
                    eventoLabel = item.event?.let {
                        translator("Sem reunião · {0}: {1}", translator(it.tipo.label), it.nome)
                    },
                )
            },
            labels = labels,
        )
    }

    /** "05/07/2026", como no cabecalho de cada bloco do documento. */
    private fun formatShortDate(date: LocalDate): String {
        val day = date.dayOfMonth.toString().padStart(2, '0')
        val month = date.monthNumber.toString().padStart(2, '0')
        return "$day/$month/${date.year}"
    }

    private fun shiftMonth(year: Int, month: Int, delta: Int): Pair<Int, Int> {
        val zeroBased = month - 1 + delta
        val newYear = year + Math.floorDiv(zeroBased, 12)
        val newMonth = Math.floorMod(zeroBased, 12) + 1
        return newYear to newMonth
    }
}
