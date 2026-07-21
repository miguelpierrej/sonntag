package com.example.sonntag.ui.screens.setup

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonntag.data.repos.MeetingDaysRepository
import com.example.sonntag.data.repos.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MeetingDayInput(
    val id: Long = System.currentTimeMillis(),
    val diaSemana: Long = 1,
    val hora: String = "19:30"
)

data class SetupFormState(
    val nomesCongregacao: String = "",
    val endereco: String = "",
    val telefone: String = "",
    val meetingDays: List<MeetingDayInput> = listOf(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val completed: Boolean = false
)

class InitialSetupViewModel(
    private val settingsRepo: SettingsRepository,
    private val meetingDaysRepo: MeetingDaysRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(SetupFormState())
    val formState: StateFlow<SetupFormState> = _formState

    fun updateNome(nome: String) {
        _formState.value = _formState.value.copy(nomesCongregacao = nome)
    }

    fun updateEndereco(endereco: String) {
        _formState.value = _formState.value.copy(endereco = endereco)
    }

    fun updateTelefone(telefone: String) {
        _formState.value = _formState.value.copy(telefone = telefone)
    }

    fun addMeetingDay() {
        val newDay = MeetingDayInput()
        val updatedDays = _formState.value.meetingDays + newDay
        _formState.value = _formState.value.copy(meetingDays = updatedDays)
    }

    fun removeMeetingDay(id: Long) {
        val updatedDays = _formState.value.meetingDays.filterNot { it.id == id }
        _formState.value = _formState.value.copy(meetingDays = updatedDays)
    }

    fun updateMeetingDay(id: Long, diaSemana: Long, hora: String) {
        val updatedDays = _formState.value.meetingDays.map {
            if (it.id == id) it.copy(diaSemana = diaSemana, hora = hora) else it
        }
        _formState.value = _formState.value.copy(meetingDays = updatedDays)
    }

    fun saveSetup() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val state = _formState.value

                // Validação
                if (state.nomesCongregacao.isBlank()) {
                    _formState.value = state.copy(errorMessage = "Nome da congregação é obrigatório")
                    return@launch
                }

                if (state.meetingDays.isEmpty()) {
                    _formState.value = state.copy(errorMessage = "Adicione pelo menos um dia de reunião")
                    return@launch
                }

                _formState.value = state.copy(isLoading = true, errorMessage = null)

                // Salva settings
                settingsRepo.insertOrUpdate(
                    nome = state.nomesCongregacao,
                    endereco = state.endereco.ifBlank { null },
                    telefone = state.telefone.ifBlank { null }
                )

                // Salva meeting days
                state.meetingDays.forEach { day ->
                    meetingDaysRepo.insert(day.diaSemana, day.hora)
                }

                _formState.value = state.copy(isLoading = false, completed = true)
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(
                    isLoading = false,
                    errorMessage = "Erro ao salvar: ${e.message}"
                )
            }
        }
    }
}

