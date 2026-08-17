package com.example.sonntag.pdf

data class MeetingProgramPdfData(
    val congregacao: String,
    val dateLabel: String,
    val hora: String,
    val fileSlug: String,
    val tituloDiscurso: String?,
    val orador: String?,
    val presidente: String?,
    val dirigenteEstudo: String?,
    val leitor: String?,
    val labels: WeekendPdfStrings,
)

data class PdfMeetingLine(
    val dateLabel: String,
    val hora: String,
    val tituloDiscurso: String?,
    val orador: String?,
    val presidente: String?,
    val dirigenteEstudo: String?,
    val leitor: String?,
    val grupoLimpeza: String? = null,
)

data class WeeklyProgramPdfData(
    val congregacao: String,
    val semanaLabel: String,
    val periodo: String,
    val grupoLimpeza: String,
    val reunioes: List<PdfMeetingLine>,
)

data class MonthlyProgramPdfData(
    val congregacao: String,
    val mesLabel: String,
    val fileSlug: String,
    val reunioes: List<PdfMeetingLine>,
    val labels: WeekendPdfStrings,
)

// ─── Meio de semana (S-140 / S-89) ───────────────────────────────────────────

/** Uma parte numerada do programa de meio de semana, ja resolvida em texto. */
data class MidweekPartPdf(
    val numero: Int,
    val titulo: String,
    val minutos: String?,
    val nome1: String? = null,
    val nome2: String? = null,
)

/** Programa de uma reuniao de meio de semana (uma coluna no S-140). */
data class MidweekWeekPdf(
    val periodo: String,
    val leitura: String,
    val presidente: String?,
    val oracaoInicial: String?,
    val canticoInicial: String?,
    val tesouros: MidweekPartPdf,
    val joias: MidweekPartPdf,
    val leituraBiblia: MidweekPartPdf,
    val ministerio: List<MidweekPartPdf>,
    val canticoMeio: String?,
    val vida: List<MidweekPartPdf>,
    val estudo: MidweekPartPdf,
    val canticoFinal: String?,
    val oracaoFinal: String?,
)

data class MidweekProgramPdfData(
    val congregacao: String,
    val subtitulo: String?,
    val mesLabel: String,
    val fileSlug: String,
    val semanas: List<MidweekWeekPdf>,
    val labels: MidweekPdfStrings,
)

/** Um comprovante de designacao (S-89). */
data class MidweekAssignmentPdf(
    val nome: String,
    val ajudante: String?,
    val data: String,
    val numeroParte: String,
    val salaAuxiliar: Boolean,
)

data class MidweekAssignmentsPdfData(
    val congregacao: String,
    val mesLabel: String,
    val fileSlug: String,
    val designacoes: List<MidweekAssignmentPdf>,
    val labels: AssignmentPdfStrings,
)

data class CleaningScheduleLine(
    val periodo: String,
    val diasReuniao: String,
    val grupoResponsavel: String?,
)

data class CleaningSchedulePdfData(
    val congregacao: String,
    val endereco: String?,
    val mesLabel: String,
    val fileSlug: String,
    val semanas: List<CleaningScheduleLine>,
    val labels: CleaningPdfStrings,
)

// ─── Audio/video e acomodadores ──────────────────────────────────────────────

/** Uma reuniao com as designacoes tecnicas ja resolvidas em nomes. */
data class AvScheduleLine(
    val dataLabel: String,
    val tipoLabel: String,
    val audio: String?,
    val video: String?,
    val plataforma: List<String>,
    val microfones: List<String>,
    val acomodadores: List<String>,
)

data class AvSchedulePdfData(
    val congregacao: String,
    val endereco: String?,
    val mesLabel: String,
    val fileSlug: String,
    val reunioes: List<AvScheduleLine>,
    val labels: AvPdfStrings,
)

// ─── Pregacao (carrinhos e pregacao de campo) ────────────────────────────────

/**
 * Nome como cabe na celula do calendario: "Maria Victoria M.".
 *
 * A celula tem cerca de 70 pontos de largura; o nome inteiro nao entra e sairia
 * cortado no meio ("Maria Victoria Mo..."), que nao serve para ninguem. A inicial do
 * sobrenome basta para desempatar homonimos na pratica.
 */
fun nomeCurtoDeCalendario(nome: String, sobrenome: String): String {
    val inicial = sobrenome.trim().firstOrNull()?.let { " $it." }.orEmpty()
    return nome.trim() + inicial
}

/** Um turno como sai na celula do calendario. */
data class PreachingShiftPdf(
    val hora: String,
    val ponto: String?,
    val nomes: List<String>,
    val nota: String?,
)

data class PreachingDayPdf(
    val dia: Int,
    val doMes: Boolean,
    val turnos: List<PreachingShiftPdf>,
)

/** Uma linha do rodape do programa de pregacao. */
data class PreachingGroupPdf(
    val nome: String,
    val dirigente: String?,
    val local: String?,
)

data class PreachingProgramPdfData(
    val congregacao: String,
    val titulo: String,
    val mesLabel: String,
    val fileSlug: String,
    /** Semanas de domingo a sabado, na mesma grade da tela. */
    val semanas: List<List<PreachingDayPdf>>,
    val grupos: List<PreachingGroupPdf>,
    val observacao: String?,
    val labels: PreachingPdfStrings,
)

interface PdfExportService {
    suspend fun exportMeetingProgram(data: MeetingProgramPdfData): Boolean
    suspend fun exportWeeklyProgram(data: WeeklyProgramPdfData): Boolean
    suspend fun exportMonthlyProgram(data: MonthlyProgramPdfData): Boolean
    suspend fun exportMeetingProgramPng(data: MeetingProgramPdfData): Boolean
    suspend fun exportWeeklyProgramPng(data: WeeklyProgramPdfData): Boolean
    suspend fun exportMonthlyProgramPng(data: MonthlyProgramPdfData): Boolean
    suspend fun exportCleaningSchedule(data: CleaningSchedulePdfData): Boolean
    suspend fun exportCleaningSchedulePng(data: CleaningSchedulePdfData): Boolean
    suspend fun exportMidweekProgram(data: MidweekProgramPdfData): Boolean
    suspend fun exportMidweekAssignments(data: MidweekAssignmentsPdfData): Boolean
    suspend fun exportAvSchedule(data: AvSchedulePdfData): Boolean
    suspend fun exportPreachingProgram(data: PreachingProgramPdfData): Boolean
}

expect fun createPdfExportService(): PdfExportService
