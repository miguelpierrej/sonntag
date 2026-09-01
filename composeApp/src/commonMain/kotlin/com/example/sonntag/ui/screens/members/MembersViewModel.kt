package com.example.sonntag.ui.screens.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonntag.data.repos.MembersRepository
import com.example.sonntag.data.repos.Responsabilidades
import com.example.sonntag.data.sqldelight.Members
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MembersUiState(
    val isLoading: Boolean = true,
    val members: List<Members> = emptyList(),
    val search: String = "",
    val errorMessage: String? = null,
)

class MembersViewModel(
    private val membersRepository: MembersRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MembersUiState())
    val uiState: StateFlow<MembersUiState> = _uiState.asStateFlow()

    init {
        observeMembers()
    }

    private fun observeMembers() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                membersRepository.getAll().collect { members ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        members = members,
                        errorMessage = null,
                    )
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Erro ao carregar membros",
                )
            }
        }
    }

    fun onSearchChanged(value: String) {
        _uiState.value = _uiState.value.copy(search = value)
    }

    fun addMember(nome: String, sobrenome: String, responsabilidades: Responsabilidades) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                membersRepository.insert(nome.trim(), sobrenome.trim(), responsabilidades)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = "Erro ao adicionar membro")
            }
        }
    }

    fun updateMember(id: Long, nome: String, sobrenome: String, responsabilidades: Responsabilidades) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                membersRepository.update(id, nome.trim(), sobrenome.trim(), responsabilidades)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = "Erro ao editar membro")
            }
        }
    }

    fun deleteMember(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                membersRepository.delete(id)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = "Erro ao remover membro")
            }
        }
    }
}

