package com.example.sonntag.ui.screens.cleaninggroups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonntag.data.repos.CleaningGroupsRepository
import com.example.sonntag.data.sqldelight.Cleaning_groups
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CleaningGroupsUiState(
    val isLoading: Boolean = true,
    val groups: List<Cleaning_groups> = emptyList(),
    val search: String = "",
    val errorMessage: String? = null,
)

class CleaningGroupsViewModel(
    private val cleaningGroupsRepository: CleaningGroupsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CleaningGroupsUiState())
    val uiState: StateFlow<CleaningGroupsUiState> = _uiState.asStateFlow()

    init {
        loadGroups()
    }

    private fun loadGroups() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                cleaningGroupsRepository.getAllOnce()
            }.onSuccess { groups ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    groups = groups,
                    errorMessage = null,
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Erro ao carregar grupos",
                )
            }
        }
    }

    fun onSearchChanged(value: String) {
        _uiState.value = _uiState.value.copy(search = value)
    }

    fun addGroup(nome: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                cleaningGroupsRepository.insert(nome.trim())
            }.onSuccess {
                loadGroups()
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = "Erro ao adicionar grupo")
            }
        }
    }

    fun updateGroup(id: Long, nome: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                cleaningGroupsRepository.update(id, nome.trim())
            }.onSuccess {
                loadGroups()
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = "Erro ao editar grupo")
            }
        }
    }

    fun deleteGroup(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                cleaningGroupsRepository.delete(id)
            }.onSuccess {
                loadGroups()
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = "Erro ao remover grupo")
            }
        }
    }
}

