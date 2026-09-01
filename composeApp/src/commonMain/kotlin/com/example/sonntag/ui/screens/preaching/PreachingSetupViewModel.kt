package com.example.sonntag.ui.screens.preaching

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonntag.data.repos.MembersRepository
import com.example.sonntag.data.repos.PreachingGroupMembersRepository
import com.example.sonntag.data.repos.PreachingGroupsRepository
import com.example.sonntag.data.repos.PreachingSpotsRepository
import com.example.sonntag.data.repos.SettingsRepository
import com.example.sonntag.data.repos.SpotKind
import com.example.sonntag.data.sqldelight.Members
import com.example.sonntag.data.sqldelight.Preaching_groups
import com.example.sonntag.data.sqldelight.Preaching_spots
import com.example.sonntag.i18n.LocaleController
import com.example.sonntag.pdf.PdfExportService
import com.example.sonntag.pdf.PreachingGroupMemberPdf
import com.example.sonntag.pdf.PreachingGroupSheetPdf
import com.example.sonntag.pdf.PreachingGroupsPdfData
import com.example.sonntag.pdf.preachingPdfStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class PreachingSetupUiState(
    val isLoading: Boolean = true,
    val spots: List<Preaching_spots> = emptyList(),
    val groups: List<Preaching_groups> = emptyList(),
    val members: List<Members> = emptyList(),
    /** id do grupo -> quem o compoe, em ordem alfabetica. */
    val membrosPorGrupo: Map<Long, List<Members>> = emptyMap(),
    /** id do membro -> id do grupo em que ele esta hoje; ninguem esta em dois. */
    val grupoDoMembro: Map<Long, Long> = emptyMap(),
    val errorMessage: String? = null,
)

/** Cadastros da pregacao: os pontos e os grupos, que alimentam o calendario. */
class PreachingSetupViewModel(
    private val spotsRepository: PreachingSpotsRepository,
    private val groupsRepository: PreachingGroupsRepository,
    private val membersRepository: MembersRepository,
    private val groupMembersRepository: PreachingGroupMembersRepository,
    private val settingsRepository: SettingsRepository,
    private val pdfExportService: PdfExportService,
    private val localeController: LocaleController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreachingSetupUiState())
    val uiState: StateFlow<PreachingSetupUiState> = _uiState.asStateFlow()

    init {
        load()
        observarMembros()
    }

    /**
     * A tela vive enquanto o app vive (e um singleton do Koin): sem observar a
     * tabela, um publicador cadastrado ou editado em Membros so apareceria aqui
     * depois de reabrir o app. A primeira emissao e descartada porque `init` ja
     * carregou.
     */
    private fun observarMembros() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { membersRepository.getAll().drop(1).collect { load() } }
        }
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val spots = spotsRepository.getAllOnce()
                val groups = groupsRepository.getAllOnce()
                val members = membersRepository.getAllOnce()
                val grupoDoMembro = groupMembersRepository.groupByMemberOnce()
                val porId = members.associateBy { it.id }
                val membrosPorGrupo = grupoDoMembro.entries
                    .groupBy({ it.value }, { it.key })
                    .mapValues { (_, ids) ->
                        ids.mapNotNull { porId[it] }.sortedWith(compareBy({ it.nome }, { it.sobrenome }))
                    }
                PreachingSetupUiState(
                    isLoading = false,
                    spots = spots,
                    groups = groups,
                    members = members,
                    membrosPorGrupo = membrosPorGrupo,
                    grupoDoMembro = grupoDoMembro,
                )
            }.onSuccess { _uiState.value = it }
                .onFailure {
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

    // ─── Membros de cada grupo ───────────────────────────────────────────────

    /** Poe o membro neste grupo; se ele estava em outro, sai de la. */
    fun addMemberToGroup(groupId: Long, memberId: Long) = edita("Erro ao salvar o grupo") {
        groupMembersRepository.assign(groupId, memberId)
    }

    fun removeMemberFromGroup(groupId: Long, memberId: Long) = edita("Erro ao salvar o grupo") {
        groupMembersRepository.remove(groupId, memberId)
    }

    // ─── Exportacao ──────────────────────────────────────────────────────────

    fun exportGroupsPdf() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { pdfExportService.exportPreachingGroups(buildGroupsExport()) }
                .onFailure {
                    _uiState.value = _uiState.value.copy(errorMessage = "Erro ao exportar a lista de grupos")
                }
        }
    }

    private fun buildGroupsExport(): PreachingGroupsPdfData {
        val s = _uiState.value
        val t = localeController.translator
        val labels = preachingPdfStrings(localeController.current)
        val settings = settingsRepository.getSettingsOnce()
        // Na folha o nome vai inteiro: a coluna e larga o bastante, e a lista serve
        // para procurar uma pessoa pelo nome completo.
        fun nomeCompleto(m: Members) = "${m.nome} ${m.sobrenome}".trim()
        val porId = s.members.associateBy { it.id }
        val pontos = s.spots.associateBy { it.id }

        return PreachingGroupsPdfData(
            congregacao = settings?.nome?.takeIf { it.isNotBlank() } ?: t("Congregação"),
            subtitulo = t.longDateWithYear(
                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            ),
            fileSlug = "grupos-de-pregacao",
            grupos = s.groups.map { grupo ->
                PreachingGroupSheetPdf(
                    nome = grupo.nome,
                    dirigente = grupo.dirigente_id?.let { porId[it] }?.let(::nomeCompleto),
                    auxiliar = grupo.auxiliar_id?.let { porId[it] }?.let(::nomeCompleto),
                    ponto = pontos[grupo.spot_id]?.nome,
                    // Dirigente e auxiliar saem sem sigla: o cargo ja esta escrito
                    // ao lado do nome deles.
                    membros = s.membrosPorGrupo[grupo.id].orEmpty().map {
                        PreachingGroupMemberPdf(nomeCompleto(it), it.siglas().ifBlank { null })
                    },
                )
            },
            labels = labels,
        )
    }
}
