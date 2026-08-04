package com.example.sonntag.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonntag.data.repos.CleaningAssignmentsRepository
import com.example.sonntag.data.repos.CleaningGroupsRepository
import com.example.sonntag.data.repos.MeetingsRepository
import com.example.sonntag.data.repos.MembersRepository
import com.example.sonntag.data.repos.MidweekProgramsRepository
import com.example.sonntag.data.repos.WeekendProgramsRepository
import com.example.sonntag.data.sqldelight.Meetings
import com.example.sonntag.data.sqldelight.Members
import com.example.sonntag.domain.usecases.MeetingGenerator
import com.example.sonntag.domain.usecases.isoYearWeek
import com.example.sonntag.domain.usecases.weekStart
import com.example.sonntag.i18n.LocaleController
import kotlinx.coroutines.Dispatchers
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

/** Janela usada pelo card de pendencias. */
private const val PENDING_WINDOW_DAYS = 28

/** Destino de navegacao ao clicar em um card (ids de MainNavigationShell). */
const val NAV_WEEKEND = "weekend"
const val NAV_MIDWEEK = "midweek"
const val NAV_CLEANING = "limpeza"

/** Linha "rotulo: valor" exibida dentro de um card. */
data class DashboardDetail(val label: String, val value: String)

data class NextMeetingInfo(
    val dateLabel: String,
    val time: String,
    val typeLabel: String,
    val relativeLabel: String,
    val details: List<DashboardDetail>,
    val navTarget: String,
)

data class CleaningWeekInfo(
    val periodText: String,
    val groupName: String?,
    val nextWeekGroupName: String?,
)

data class PendingProgramItem(
    val dateLabel: String,
    val typeLabel: String,
    val navTarget: String,
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val nextMeeting: NextMeetingInfo? = null,
    val cleaning: CleaningWeekInfo? = null,
    val pendingItems: List<PendingProgramItem> = emptyList(),
    val pendingWindowDays: Int = PENDING_WINDOW_DAYS,
)

class DashboardViewModel(
    private val meetingsRepository: MeetingsRepository,
    private val weekendProgramsRepository: WeekendProgramsRepository,
    private val midweekProgramsRepository: MidweekProgramsRepository,
    private val cleaningGroupsRepository: CleaningGroupsRepository,
    private val cleaningAssignmentsRepository: CleaningAssignmentsRepository,
    private val membersRepository: MembersRepository,
    private val meetingGenerator: MeetingGenerator,
    private val localeController: LocaleController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            meetingGenerator.generateNext12Months()

            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val horizon = today.plus(DatePeriod(days = PENDING_WINDOW_DAYS))
            val upcoming = meetingsRepository
                .getByDateRangeOnce(today.toString(), horizon.toString())
                .sortedWith(compareBy({ it.data_ }, { it.hora }))
            val members = membersRepository.getAllOnce().associateBy { it.id }

            _uiState.value = DashboardUiState(
                isLoading = false,
                nextMeeting = upcoming.firstOrNull()?.let { buildNextMeeting(it, today, members) },
                cleaning = buildCleaningWeek(today),
                pendingItems = upcoming.filter { isPending(it) }.map { buildPendingItem(it) },
            )
        }
    }

    private fun buildNextMeeting(
        meeting: Meetings,
        today: LocalDate,
        members: Map<Long, Members>,
    ): NextMeetingInfo {
        val t = localeController.translator
        val date = LocalDate.parse(meeting.data_)
        val isWeekend = meeting.tipo == "WEEKEND"

        val details = if (isWeekend) {
            val program = weekendProgramsRepository.getByMeetingIdOnce(meeting.id)
            listOfNotNull(
                program?.titulo_discurso?.takeIf { it.isNotBlank() }
                    ?.let { DashboardDetail(t("Discurso"), it) },
                (program?.orador_id?.let { members[it]?.fullName() }
                    ?: program?.orador_nome?.takeIf { it.isNotBlank() })
                    ?.let { DashboardDetail(t("Orador"), it) },
                program?.presidente_id?.let { members[it] }
                    ?.let { DashboardDetail(t("Presidente"), it.fullName()) },
            )
        } else {
            val program = midweekProgramsRepository.getByMeetingIdOnce(meeting.id)
            listOfNotNull(
                program?.leitura_semanal?.takeIf { it.isNotBlank() }
                    ?.let { DashboardDetail(t("Leitura"), it) },
                program?.presidente_id?.let { members[it] }
                    ?.let { DashboardDetail(t("Presidente"), it.fullName()) },
            )
        }

        return NextMeetingInfo(
            dateLabel = t.longDate(date),
            time = meeting.hora,
            typeLabel = if (isWeekend) t("Fim de semana") else t("Meio de semana"),
            relativeLabel = relativeLabel(date, today),
            details = details,
            navTarget = if (isWeekend) NAV_WEEKEND else NAV_MIDWEEK,
        )
    }

    private fun buildCleaningWeek(today: LocalDate): CleaningWeekInfo {
        val monday = weekStart(today)
        val sunday = monday.plus(DatePeriod(days = 6))
        val groupsById = cleaningGroupsRepository.getAllOnce().associateBy { it.id }

        fun groupNameFor(date: LocalDate): String? {
            val (isoYear, isoWeek) = isoYearWeek(date)
            val assignment = cleaningAssignmentsRepository
                .getByWeekOnce(isoWeek.toLong(), isoYear.toLong())
            return assignment?.group_id?.let { groupsById[it]?.nome }
        }

        return CleaningWeekInfo(
            periodText = localeController.translator.weekRange(monday, sunday),
            groupName = groupNameFor(today),
            nextWeekGroupName = groupNameFor(monday.plus(DatePeriod(days = 7))),
        )
    }

    private fun buildPendingItem(meeting: Meetings): PendingProgramItem {
        val t = localeController.translator
        val isWeekend = meeting.tipo == "WEEKEND"
        return PendingProgramItem(
            dateLabel = t.longDate(LocalDate.parse(meeting.data_)),
            typeLabel = if (isWeekend) t("Fim de semana") else t("Meio de semana"),
            navTarget = if (isWeekend) NAV_WEEKEND else NAV_MIDWEEK,
        )
    }

    /** Uma reuniao esta pendente enquanto faltar o conteudo principal ou o presidente. */
    private fun isPending(meeting: Meetings): Boolean {
        return if (meeting.tipo == "WEEKEND") {
            val program = weekendProgramsRepository.getByMeetingIdOnce(meeting.id)
            program == null ||
                program.titulo_discurso.isNullOrBlank() ||
                (program.orador_id == null && program.orador_nome.isNullOrBlank()) ||
                program.presidente_id == null
        } else {
            val program = midweekProgramsRepository.getByMeetingIdOnce(meeting.id)
            program == null ||
                program.tesouros_titulo.isNullOrBlank() ||
                program.presidente_id == null
        }
    }

    private fun relativeLabel(date: LocalDate, today: LocalDate): String {
        val t = localeController.translator
        return when (val days = date.toEpochDays() - today.toEpochDays()) {
            0 -> t("Hoje")
            1 -> t("Amanhã")
            else -> t("Em {0} dias", days)
        }
    }

    private fun Members.fullName(): String = "$nome $sobrenome"
}
