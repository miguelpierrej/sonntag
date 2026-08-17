package com.example.sonntag.ui.screens.preaching

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonntag.data.repos.MembersRepository
import com.example.sonntag.data.repos.PreachingGroupsRepository
import com.example.sonntag.data.repos.PreachingSpotsRepository
import com.example.sonntag.data.repos.SpotKind
import com.example.sonntag.data.sqldelight.Members
import com.example.sonntag.data.sqldelight.Preaching_groups
import com.example.sonntag.data.sqldelight.Preaching_spots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PreachingSetupUiState(
    val isLoading: Boolean = true,
    val spots: List<Preaching_spots> = emptyList(),
    val groups: List<Preaching_groups> = emptyList(),
    val members: List<Members> = emptyList(),
    val errorMessage: String? = null,
)

/** Cadastros da pregacao: os pontos e os grupos, que alimentam o calendario. */
class PreachingSetupViewModel(
    private val spotsRepository: PreachingSpotsRepository,
    private val groupsRepository: PreachingGroupsRepository,
    private val membersRepository: MembersRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreachingSetupUiState())
    val uiState: StateFlow<PreachingSetupUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                Triple(
                    spotsRepository.getAllOnce(),
                    groupsRepository.getAllOnce(),
                    membersRepository.getAllOnce(),
                )
            }.onSuccess { (spots, groups, members) ->
                _uiState.value = PreachingSetupUiState(
                    isLoading = false,
                    spots = spots,
                    groups = groups,
                    members = members,
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Erro ao carregar os cadastros de pregação",
                )
            }
        }
    }

    private fun edita(erro: String, bloco: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching(bloco)
                .onSuccess { load() }
                .onFailure { _uiState.value = _uiState.value.copy(errorMessage = erro) }
        }
    }

    // ─── Pontos ──────────────────────────────────────────────────────────────

    fun addSpot(nome: String, endereco: String, tipo: SpotKind) = edita("Erro ao salvar o ponto") {
        spotsRepository.insert(nome.trim(), endereco.trim().ifBlank { null }, tipo)
    }

    fun updateSpot(id: Long, nome: String, endereco: String, tipo: SpotKind) = edita("Erro ao salvar o ponto") {
        spotsRepository.update(id, nome.trim(), endereco.trim().ifBlank { null }, tipo)
    }

    fun deleteSpot(id: Long) = edita("Erro ao remover o ponto") {
        spotsRepository.delete(id)
    }

    // ─── Grupos ──────────────────────────────────────────────────────────────

    fun addGroup(nome: String, dirigenteId: Long?, auxiliarId: Long?, spotId: Long?) =
        edita("Erro ao salvar o grupo") {
            groupsRepository.insert(nome.trim(), dirigenteId, auxiliarId, spotId, groupsRepository.nextOrdem())
        }

    fun updateGroup(id: Long, nome: String, dirigenteId: Long?, auxiliarId: Long?, spotId: Long?) =
        edita("Erro ao salvar o grupo") {
            val ordem = groupsRepository.getAllOnce().firstOrNull { it.id == id }?.ordem ?: 0L
            groupsRepository.update(id, nome.trim(), dirigenteId, auxiliarId, spotId, ordem)
        }

    fun deleteGroup(id: Long) = edita("Erro ao remover o grupo") {
        groupsRepository.delete(id)
    }

    /** Troca o grupo de lugar com o vizinho; a ordem e a do rodape do documento. */
    fun moveGroup(id: Long, delta: Int) = edita("Erro ao reordenar os grupos") {
        val lista = groupsRepository.getAllOnce()
        val atual = lista.indexOfFirst { it.id == id }
        val destino = atual + delta
        if (atual < 0 || destino !in lista.indices) return@edita
        val a = lista[atual]
        val b = lista[destino]
        groupsRepository.update(a.id, a.nome, a.dirigente_id, a.auxiliar_id, a.spot_id, b.ordem)
        groupsRepository.update(b.id, b.nome, b.dirigente_id, b.auxiliar_id, b.spot_id, a.ordem)
    }
}
