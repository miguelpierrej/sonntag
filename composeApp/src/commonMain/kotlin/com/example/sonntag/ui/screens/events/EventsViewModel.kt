package com.example.sonntag.ui.screens.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonntag.data.repos.EventsRepository
import com.example.sonntag.domain.usecases.CongregationEvent
import com.example.sonntag.domain.usecases.EventType
import com.example.sonntag.domain.usecases.toDomain
import com.example.sonntag.i18n.LocaleController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class EventsUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate(1970, 1, 1),
    val events: List<CongregationEvent> = emptyList(),
    val errorMessage: String? = null,
)

class EventsViewModel(
    private val eventsRepository: EventsRepository,
    private val localeController: LocaleController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                eventsRepository.getAllOnce().map { it.toDomain() }
            }.onSuccess { events ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
                    events = events,
                    errorMessage = null,
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = localeController.translator("Erro ao carregar eventos"),
                )
            }
        }
    }

    fun addEvent(nome: String, date: LocalDate, tipo: EventType) {
        write("Erro ao adicionar evento") {
            eventsRepository.insert(nome.trim(), date.toString(), tipo.id)
        }
    }

    fun updateEvent(id: Long, nome: String, date: LocalDate, tipo: EventType) {
        write("Erro ao editar evento") {
            eventsRepository.update(id, nome.trim(), date.toString(), tipo.id)
        }
    }

    fun deleteEvent(id: Long) {
        write("Erro ao remover evento") { eventsRepository.delete(id) }
    }

    private fun write(erro: String, block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching(block)
                .onSuccess { load() }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = localeController.translator(erro),
                    )
                }
        }
    }
}
