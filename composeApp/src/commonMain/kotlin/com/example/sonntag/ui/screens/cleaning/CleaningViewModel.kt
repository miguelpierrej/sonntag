package com.example.sonntag.ui.screens.cleaning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonntag.data.repos.CleaningAssignmentsRepository
import com.example.sonntag.data.repos.CleaningGroupsRepository
import com.example.sonntag.data.repos.MeetingsRepository
import com.example.sonntag.data.repos.SettingsRepository
import com.example.sonntag.data.sqldelight.Cleaning_groups
import com.example.sonntag.domain.usecases.MeetingGenerator
import com.example.sonntag.pdf.CleaningScheduleLine
import com.example.sonntag.pdf.CleaningSchedulePdfData
import com.example.sonntag.pdf.PdfExportService
import com.example.sonntag.ui.util.dayNamePt
import com.example.sonntag.ui.util.monthNamePt
import com.example.sonntag.ui.util.monthNamePtCapitalized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class CleaningWeekItem(
    val isoYear: Int,
    val isoWeek: Int,
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val anchorYear: Int,
    val anchorMonth: Int,
    val periodText: String,
    val meetingDayTexts: List<String>,
    val isPast: Boolean,
    val assignedGroupId: Long?,
)

data class CleaningUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate(1970, 1, 1),
    val visibleYear: Int = 1970,
    val visibleMonth: Int = 1,
    val weekItems: List<CleaningWeekItem> = emptyList(),
    val groups: List<Cleaning_groups> = emptyList(),
)

class CleaningViewModel(
    private val meetingsRepository: MeetingsRepository,
    private val cleaningGroupsRepository: CleaningGroupsRepository,
    private val cleaningAssignmentsRepository: CleaningAssignmentsRepository,
    private val meetingGenerator: MeetingGenerator,
    private val settingsRepository: SettingsRepository,
    private val pdfExportService: PdfExportService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CleaningUiState())
    val uiState: StateFlow<CleaningUiState> = _uiState.asStateFlow()

    init {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        _uiState.value = _uiState.value.copy(
            today = today,
            visibleYear = today.year,
            visibleMonth = today.monthNumber,
        )
        load()
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            meetingGenerator.generateNext12Months()

            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val rangeStart = today.plus(DatePeriod(months = -12))
            val rangeEnd = today.plus(DatePeriod(months = 12))
            val thisWeekMonday = weekStart(today)

            val meetings = meetingsRepository.getByDateRangeOnce(rangeStart.toString(), rangeEnd.toString())
            val groups = cleaningGroupsRepository.getAllOnce()
            val assignments = cleaningAssignmentsRepository.getAllOnce()
                .associateBy { "${it.ano}-${it.semana_iso}" }

            val weekItems = meetings
                .groupBy {
                    val date = LocalDate.parse(it.data_)
                    isoYearWeek(date)
                }
                .map { (yw, list) ->
                    val sortedDates = list.map { LocalDate.parse(it.data_) }.distinct().sorted()
                    val monday = weekStart(sortedDates.first())
                    val sunday = monday.plus(DatePeriod(days = 6))
                    val key = "${yw.first.toLong()}-${yw.second.toLong()}"
                    val assigned = assignments[key]?.group_id
                    val thursday = monday.plus(DatePeriod(days = 3))
                    CleaningWeekItem(
                        isoYear = yw.first,
                        isoWeek = yw.second,
                        weekStart = monday,
                        weekEnd = sunday,
                        anchorYear = thursday.year,
                        anchorMonth = thursday.monthNumber,
                        periodText = formatPeriod(monday, sunday),
                        meetingDayTexts = sortedDates.map { formatMeetingDay(it) },
                        isPast = monday < thisWeekMonday,
                        assignedGroupId = assigned,
                    )
                }
                .sortedBy { it.weekStart }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                today = today,
                weekItems = weekItems,
                groups = groups,
            )
        }
    }

    fun showPreviousMonth() {
        val state = _uiState.value
        val (y, m) = shiftMonth(state.visibleYear, state.visibleMonth, -1)
        _uiState.value = state.copy(visibleYear = y, visibleMonth = m)
    }

    fun showNextMonth() {
        val state = _uiState.value
        val (y, m) = shiftMonth(state.visibleYear, state.visibleMonth, 1)
        _uiState.value = state.copy(visibleYear = y, visibleMonth = m)
    }

    fun showCurrentMonth() {
        val today = _uiState.value.today
        _uiState.value = _uiState.value.copy(
            visibleYear = today.year,
            visibleMonth = today.monthNumber,
        )
    }

    fun exportVisibleMonthPdf() {
        viewModelScope.launch(Dispatchers.IO) {
            pdfExportService.exportCleaningSchedule(buildCurrentMonthExport())
        }
    }

    fun exportVisibleMonthPng() {
        viewModelScope.launch(Dispatchers.IO) {
            pdfExportService.exportCleaningSchedulePng(buildCurrentMonthExport())
        }
    }

    private fun buildCurrentMonthExport(): CleaningSchedulePdfData {
        val state = _uiState.value
        val settings = settingsRepository.getSettingsOnce()
        val groupsById = state.groups.associateBy { it.id }
        val visibleWeeks = state.weekItems.filter {
            it.anchorYear == state.visibleYear && it.anchorMonth == state.visibleMonth
        }
        val monthLabel = "${monthNamePtCapitalized(state.visibleMonth)} ${state.visibleYear}"
        val slug = "limpeza-${monthNamePt(state.visibleMonth)}-${state.visibleYear}"
        return CleaningSchedulePdfData(
            congregacao = settings?.nome?.takeIf { it.isNotBlank() } ?: "Congregação",
            endereco = settings?.endereco?.takeIf { it.isNotBlank() },
            mesLabel = monthLabel,
            fileSlug = slug,
            semanas = visibleWeeks.map { week ->
                CleaningScheduleLine(
                    periodo = week.periodText,
                    diasReuniao = week.meetingDayTexts.joinToString(" • "),
                    grupoResponsavel = week.assignedGroupId?.let { groupsById[it]?.nome },
                )
            },
        )
    }

    fun onGroupSelected(item: CleaningWeekItem, groupId: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            cleaningAssignmentsRepository.upsertByWeek(item.isoWeek.toLong(), item.isoYear.toLong(), groupId)
            _uiState.value = _uiState.value.copy(
                weekItems = _uiState.value.weekItems.map {
                    if (it.isoYear == item.isoYear && it.isoWeek == item.isoWeek) {
                        it.copy(assignedGroupId = groupId)
                    } else {
                        it
                    }
                },
            )
        }
    }

    private fun shiftMonth(year: Int, month: Int, delta: Int): Pair<Int, Int> {
        val zeroBased = month - 1 + delta
        val newYear = year + Math.floorDiv(zeroBased, 12)
        val newMonth = Math.floorMod(zeroBased, 12) + 1
        return newYear to newMonth
    }

    private fun weekStart(date: LocalDate): LocalDate {
        val shift = date.dayOfWeek.isoDayNumber - 1
        return date.plus(DatePeriod(days = -shift))
    }

    private fun isoYearWeek(date: LocalDate): Pair<Int, Int> {
        val day = date.dayOfWeek.isoDayNumber
        val thursday = date.plus(DatePeriod(days = 4 - day))
        val weekYear = thursday.year

        val jan4 = LocalDate(weekYear, 1, 4)
        val jan4Day = jan4.dayOfWeek.isoDayNumber
        var firstThursday = jan4.plus(DatePeriod(days = 4 - jan4Day))

        var week = 1
        while (firstThursday < thursday) {
            firstThursday = firstThursday.plus(DatePeriod(days = 7))
            week++
        }

        return weekYear to week
    }

    private fun formatPeriod(start: LocalDate, end: LocalDate): String {
        val startMonth = monthNamePt(start.monthNumber)
        val endMonth = monthNamePt(end.monthNumber)
        return if (start.monthNumber == end.monthNumber && start.year == end.year) {
            "${start.dayOfMonth} a ${end.dayOfMonth} de $endMonth"
        } else {
            "${start.dayOfMonth} de $startMonth a ${end.dayOfMonth} de $endMonth"
        }
    }

    private fun formatMeetingDay(date: LocalDate): String {
        return "${dayNamePt(date.dayOfWeek)} (${date.dayOfMonth})"
    }
}

