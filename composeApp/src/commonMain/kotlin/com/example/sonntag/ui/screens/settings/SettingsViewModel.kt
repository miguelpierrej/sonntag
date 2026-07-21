package com.example.sonntag.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonntag.data.repos.MeetingDaysRepository
import com.example.sonntag.data.repos.MeetingsRepository
import com.example.sonntag.data.repos.SettingsRepository
import com.example.sonntag.data.sqldelight.Meeting_days
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

data class SettingsMeetingDayItem(
    val id: Long,
    val diaSemana: Long,
    val hora: String,
)

data class SettingsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val nome: String = "",
    val endereco: String = "",
    val telefone: String = "",
    val meetingDays: List<SettingsMeetingDayItem> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val meetingDaysRepository: MeetingDaysRepository,
    private val meetingsRepository: MeetingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettingsOnce()
            val days = meetingDaysRepository.getAllOnce()
            _uiState.value = SettingsUiState(
                isLoading = false,
                nome = settings?.nome.orEmpty(),
                endereco = settings?.endereco.orEmpty(),
                telefone = settings?.telefone.orEmpty(),
                meetingDays = days.map {
                    SettingsMeetingDayItem(
                        id = it.id,
                        diaSemana = it.dia_semana,
                        hora = it.hora,
                    )
                },
            )
        }
    }

    fun updateNome(value: String) {
        _uiState.value = _uiState.value.copy(nome = value, successMessage = null)
    }

    fun updateEndereco(value: String) {
        _uiState.value = _uiState.value.copy(endereco = value, successMessage = null)
    }

    fun updateTelefone(value: String) {
        _uiState.value = _uiState.value.copy(telefone = value, successMessage = null)
    }

    fun addMeetingDay() {
        val current = _uiState.value
        val item = SettingsMeetingDayItem(
            id = Clock.System.now().toEpochMilliseconds(),
            diaSemana = 1,
            hora = "19:30",
        )
        _uiState.value = current.copy(
            meetingDays = current.meetingDays + item,
            successMessage = null,
        )
    }

    fun removeMeetingDay(id: Long) {
        val current = _uiState.value
        _uiState.value = current.copy(
            meetingDays = current.meetingDays.filterNot { it.id == id },
            successMessage = null,
        )
    }

    fun updateMeetingDay(id: Long, diaSemana: Long, hora: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            meetingDays = current.meetingDays.map {
                if (it.id == id) it.copy(diaSemana = diaSemana, hora = hora) else it
            },
            successMessage = null,
        )
    }

    fun save() {
        val current = _uiState.value
        if (current.nome.isBlank()) {
            _uiState.value = current.copy(errorMessage = "Nome da congregação é obrigatório")
            return
        }
        if (current.meetingDays.isEmpty()) {
            _uiState.value = current.copy(errorMessage = "Adicione pelo menos 1 dia de reunião")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)

                val oldDays = meetingDaysRepository.getAllOnce()
                val newDays = _uiState.value.meetingDays

                settingsRepository.insertOrUpdate(
                    nome = _uiState.value.nome.trim(),
                    endereco = _uiState.value.endereco.trim().ifBlank { null },
                    telefone = _uiState.value.telefone.trim().ifBlank { null },
                )

                regenerateFutureMeetings(oldDays, newDays)

                meetingDaysRepository.replaceAll(
                    newDays.map { it.diaSemana to it.hora.trim() }
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    successMessage = "Configurações salvas",
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "Erro ao salvar configurações: ${err.message}",
                )
            }
        }
    }

    private fun regenerateFutureMeetings(
        oldDays: List<Meeting_days>,
        newDays: List<SettingsMeetingDayItem>,
    ) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val todayText = today.toString()

        val oldByDay = oldDays.associateBy { it.dia_semana }
        val newByDay = newDays.associateBy { it.diaSemana }

        val orphanProgramsLogs = mutableListOf<String>()

        val futureWithProgram = meetingsRepository.getFutureMeetingsWithProgram(todayText)
        futureWithProgram.forEach { meeting ->
            val date = LocalDate.parse(meeting.data_)
            val isoDay = date.dayOfWeek.isoDayNumber.toLong()
            val oldDef = oldByDay[isoDay]
            val newDef = newByDay[isoDay]

            if (newDef == null) {
                orphanProgramsLogs.add("Programa a realocar: reunião ${meeting.id} em ${meeting.data_} ${meeting.hora}")
            } else if (oldDef != null && oldDef.hora != newDef.hora) {
                meetingsRepository.update(meeting.id, meeting.data_, newDef.hora, meeting.tipo)
            }
        }

        meetingsRepository.deleteFutureMeetingsWithoutProgram(todayText)

        val existingFuture = meetingsRepository.getFutureMeetings(todayText)
        val existingKeys = existingFuture
            .map { "${it.data_}|${it.tipo}" }
            .toMutableSet()

        val endDate = today.plus(DatePeriod(months = 12))
        var cursor = today.plus(DatePeriod(days = 1))
        while (cursor <= endDate) {
            val isoDay = cursor.dayOfWeek.isoDayNumber.toLong()
            val def = newByDay[isoDay]
            if (def != null) {
                val tipo = if (isoDay >= 6) "WEEKEND" else "WEEKDAY"
                val key = "${cursor}|$tipo"
                if (!existingKeys.contains(key)) {
                    meetingsRepository.insert(cursor.toString(), def.hora, tipo)
                    existingKeys.add(key)
                }
            }
            cursor = cursor.plus(DatePeriod(days = 1))
        }

        orphanProgramsLogs.forEach { println(it) }
    }
}

