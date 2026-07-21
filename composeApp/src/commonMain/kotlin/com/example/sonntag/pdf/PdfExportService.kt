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
)

interface PdfExportService {
    fun exportMeetingProgram(data: MeetingProgramPdfData): Boolean
    fun exportWeeklyProgram(data: WeeklyProgramPdfData): Boolean
    fun exportMonthlyProgram(data: MonthlyProgramPdfData): Boolean
    fun exportMeetingProgramPng(data: MeetingProgramPdfData): Boolean
    fun exportWeeklyProgramPng(data: WeeklyProgramPdfData): Boolean
    fun exportMonthlyProgramPng(data: MonthlyProgramPdfData): Boolean
    fun exportCleaningSchedule(data: CleaningSchedulePdfData): Boolean
    fun exportCleaningSchedulePng(data: CleaningSchedulePdfData): Boolean
    fun exportMidweekProgram(data: MidweekProgramPdfData): Boolean
    fun exportMidweekAssignments(data: MidweekAssignmentsPdfData): Boolean
}

expect fun createPdfExportService(): PdfExportService
