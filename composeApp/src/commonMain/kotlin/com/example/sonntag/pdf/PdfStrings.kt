package com.example.sonntag.pdf

import com.example.sonntag.i18n.AppLanguage

/** Rotulos compartilhados por todos os documentos exportados (por idioma). */
data class CommonPdfStrings(
    val geradoEm: String,
    val paginaTemplate: String,
    val dialogTitle: String,
    val aDefinir: String,
    val discursoADefinir: String,
    val congregacao: String,
) {
    /** "Gerado em 02/07/2026 08:46" */
    fun geradoEm(timestamp: String): String = "$geradoEm $timestamp"

    /** "Página 1 de 3" */
    fun pagina(atual: Int, total: Int): String =
        paginaTemplate.replace("{0}", atual.toString()).replace("{1}", total.toString())
}

/** Rotulos da programacao de fim de semana (mensal e por reuniao). */
data class WeekendPdfStrings(
    val common: CommonPdfStrings,
    val tituloMensal: String,
    val tituloReuniao: String,
    val vazio: String,
    val titulo: String,
    val orador: String,
    val presidente: String,
    val dirigente: String,
    val leitor: String,
)

/** Rotulos da escala de limpeza. */
data class CleaningPdfStrings(
    val common: CommonPdfStrings,
    val title: String,
    val semana: String,
    val reunioes: String,
    val grupo: String,
    val diasReuniao: String,
    val grupoResponsavel: String,
    val vazio: String,
)

fun commonPdfStrings(lang: AppLanguage): CommonPdfStrings = when (lang) {
    AppLanguage.ES -> CommonPdfStrings(
        geradoEm = "Generado el",
        paginaTemplate = "Página {0} de {1}",
        dialogTitle = "Guardar como",
        aDefinir = "Por definir",
        discursoADefinir = "Discurso por definir",
        congregacao = "Congregación",
    )
    AppLanguage.PT_BR -> CommonPdfStrings(
        geradoEm = "Gerado em",
        paginaTemplate = "Página {0} de {1}",
        dialogTitle = "Salvar como",
        aDefinir = "A definir",
        discursoADefinir = "Discurso a definir",
        congregacao = "Congregação",
    )
}

fun weekendPdfStrings(lang: AppLanguage): WeekendPdfStrings = when (lang) {
    AppLanguage.ES -> WeekendPdfStrings(
        common = commonPdfStrings(lang),
        tituloMensal = "Programa de Fin de Semana",
        tituloReuniao = "Programa de la Reunión",
        vazio = "Ninguna reunión configurada para este mes.",
        titulo = "Título",
        orador = "Orador",
        presidente = "Presidente",
        dirigente = "Conductor del estudio",
        leitor = "Lector",
    )
    AppLanguage.PT_BR -> WeekendPdfStrings(
        common = commonPdfStrings(lang),
        tituloMensal = "Programação de Fim de Semana",
        tituloReuniao = "Programação da Reunião",
        vazio = "Nenhuma reunião configurada para este mês.",
        titulo = "Título",
        orador = "Orador",
        presidente = "Presidente",
        dirigente = "Dirigente do estudo",
        leitor = "Leitor",
    )
}

fun cleaningPdfStrings(lang: AppLanguage): CleaningPdfStrings = when (lang) {
    AppLanguage.ES -> CleaningPdfStrings(
        common = commonPdfStrings(lang),
        title = "Programa de Limpieza",
        semana = "Semana",
        reunioes = "Reuniones",
        grupo = "Grupo",
        diasReuniao = "Días de reunión",
        grupoResponsavel = "Grupo responsable",
        vazio = "Ninguna semana con reunión en este mes.",
    )
    AppLanguage.PT_BR -> CleaningPdfStrings(
        common = commonPdfStrings(lang),
        title = "Escala de Limpeza",
        semana = "Semana",
        reunioes = "Reuniões",
        grupo = "Grupo",
        diasReuniao = "Dias de reunião",
        grupoResponsavel = "Grupo responsável",
        vazio = "Nenhuma semana com reunião neste mês.",
    )
}

/** Rotulos fixos do formulario S-140 (por idioma). */
data class MidweekPdfStrings(
    val headerTitle: String,
    val headerSubtitle: String,
    val headerGuide: String,
    val presidente: String,
    val oracaoInicial: String,
    val cancion: String,
    val tesouros1: String,
    val tesouros2: String,
    val seamos1: String,
    val seamos2: String,
    val vida1: String,
    val vida2: String,
    val conductor: String,
    val lector: String,
    val conclusion: String,
    val oracaoFinal: String,
    val mins: String,
    val perlasTitulo: String,
    val lecturaTitulo: String,
    val estudioTitulo: String,
    val tesourosFallback: String,
    val dialogTitle: String,
)

/** Rotulos fixos do comprovante de designacao S-89 (por idioma). */
data class AssignmentPdfStrings(
    val title1: String,
    val title2: String,
    val nombre: String,
    val ayudante: String,
    val fecha: String,
    val intervencion: String,
    val presentaraEn: String,
    val salaPrincipal: String,
    val salaAuxiliar: String,
    val nota: String,
    val vazio: String,
    val dialogTitle: String,
)

/** Rotulos fixos da folha de audio/video e acomodadores (por idioma). */
data class AvPdfStrings(
    val title: String,
    val audioVideo: String,
    val plataforma: String,
    val microfones: String,
    val acomodadores: String,
    val audioTag: String,
    val videoTag: String,
    val reuniaoFimSemana: String,
    val reuniaoMeioSemana: String,
    val vazio: String,
    val dialogTitle: String,
)

fun avPdfStrings(lang: AppLanguage): AvPdfStrings = when (lang) {
    AppLanguage.ES -> AvPdfStrings(
        title = "Audio, video, y acomodadores",
        audioVideo = "Audio y video",
        plataforma = "Plataforma",
        microfones = "Micrófonos",
        acomodadores = "Acomodador auditorio",
        audioTag = "Audio",
        videoTag = "Video",
        reuniaoFimSemana = "Reunión del fin de semana",
        reuniaoMeioSemana = "Reunión de entre semana",
        vazio = "Ninguna reunión en este mes.",
        dialogTitle = "Guardar como",
    )
    AppLanguage.PT_BR -> AvPdfStrings(
        title = "Áudio, vídeo e acomodadores",
        audioVideo = "Áudio e vídeo",
        plataforma = "Plataforma",
        microfones = "Microfones",
        acomodadores = "Acomodador do auditório",
        audioTag = "Áudio",
        videoTag = "Vídeo",
        reuniaoFimSemana = "Reunião do fim de semana",
        reuniaoMeioSemana = "Reunião de meio de semana",
        vazio = "Nenhuma reunião neste mês.",
        dialogTitle = "Salvar como",
    )
}

fun midweekPdfStrings(lang: AppLanguage): MidweekPdfStrings = when (lang) {
    AppLanguage.ES -> MidweekPdfStrings(
        headerTitle = "Reunión de entre semana",
        headerSubtitle = "Vida y Ministerio Cristianos",
        headerGuide = "GUÍA DE ACTIVIDADES PARA LA REUNIÓN",
        presidente = "Presidente",
        oracaoInicial = "Oración inicial",
        cancion = "Canción",
        tesouros1 = "TESOROS",
        tesouros2 = "DE LA BIBLIA",
        seamos1 = "SEAMOS",
        seamos2 = "MEJORES MAESTROS",
        vida1 = "NUESTRA",
        vida2 = "VIDA CRISTIANA",
        conductor = "Conductor",
        lector = "Lector",
        conclusion = "Palabras de conclusión y canción",
        oracaoFinal = "Oración conclusión",
        mins = "mins.",
        perlasTitulo = "Busquemos perlas escondidas",
        lecturaTitulo = "Lectura de la Biblia",
        estudioTitulo = "Estudio bíblico de la congregación",
        tesourosFallback = "Tesoros de la Biblia",
        dialogTitle = "Guardar como",
    )
    AppLanguage.PT_BR -> MidweekPdfStrings(
        headerTitle = "Reunião de meio de semana",
        headerSubtitle = "Vida e Ministério Cristão",
        headerGuide = "GUIA DE ATIVIDADES PARA A REUNIÃO",
        presidente = "Presidente",
        oracaoInicial = "Oração inicial",
        cancion = "Cântico",
        tesouros1 = "TESOUROS",
        tesouros2 = "DA PALAVRA DE DEUS",
        seamos1 = "FAÇA SEU",
        seamos2 = "MELHOR NO MINISTÉRIO",
        vida1 = "NOSSA",
        vida2 = "VIDA CRISTÃ",
        conductor = "Dirigente",
        lector = "Leitor",
        conclusion = "Comentários finais e cântico",
        oracaoFinal = "Oração final",
        mins = "min.",
        perlasTitulo = "Joias espirituais",
        lecturaTitulo = "Leitura da Bíblia",
        estudioTitulo = "Estudo bíblico de congregação",
        tesourosFallback = "Tesouros da Palavra de Deus",
        dialogTitle = "Salvar como",
    )
}

fun assignmentPdfStrings(lang: AppLanguage): AssignmentPdfStrings = when (lang) {
    AppLanguage.ES -> AssignmentPdfStrings(
        title1 = "ASIGNACIÓN PARA LA REUNIÓN",
        title2 = "VIDA Y MINISTERIO CRISTIANOS",
        nombre = "Nombre: ",
        ayudante = "Ayudante: ",
        fecha = "Fecha: ",
        intervencion = "Intervención núm.: ",
        presentaraEn = "Se presentará en:",
        salaPrincipal = "Sala principal",
        salaAuxiliar = "Sala auxiliar",
        nota = "Nota al estudiante: En la Guía de actividades encontrará la información " +
            "que necesita para su intervención.",
        vazio = "Ninguna asignación de estudiante en el mes.",
        dialogTitle = "Guardar como",
    )
    AppLanguage.PT_BR -> AssignmentPdfStrings(
        title1 = "DESIGNAÇÃO PARA A REUNIÃO",
        title2 = "VIDA E MINISTÉRIO CRISTÃO",
        nombre = "Nome: ",
        ayudante = "Ajudante: ",
        fecha = "Data: ",
        intervencion = "Designação nº: ",
        presentaraEn = "Será feita na:",
        salaPrincipal = "Sala principal",
        salaAuxiliar = "Sala auxiliar",
        nota = "Nota ao estudante: no Guia de atividades você encontrará as informações " +
            "necessárias para a sua designação.",
        vazio = "Nenhuma designação de estudante no mês.",
        dialogTitle = "Salvar como",
    )
}
