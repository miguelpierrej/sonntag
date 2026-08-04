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
}

expect fun createPdfExportService(): PdfExportService
