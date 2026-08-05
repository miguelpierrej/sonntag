package com.example.sonntag.i18n

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * Tradutor leve: o texto em portugues e a chave/fonte; para espanhol usa
 * [EsTranslations]. Textos sem traducao caem no portugues (fallback visivel).
 */
class Translator(val language: AppLanguage) {

    operator fun invoke(pt: String): String =
        if (language == AppLanguage.PT_BR) pt else EsTranslations.map[pt] ?: pt

    /** Versao com parametros posicionais {0}, {1}, ... */
    operator fun invoke(pt: String, vararg args: Any?): String {
        var out = invoke(pt)
        args.forEachIndexed { i, a -> out = out.replace("{$i}", a?.toString() ?: "") }
        return out
    }

    fun monthName(month: Int): String =
        (if (language == AppLanguage.ES) MONTHS_ES else MONTHS_PT).getOrElse(month - 1) { "" }

    fun monthNameCapitalized(month: Int): String =
        monthName(month).replaceFirstChar { it.uppercase() }

    fun dayName(day: DayOfWeek): String =
        (if (language == AppLanguage.ES) DAYS_ES else DAYS_PT).getOrElse(day.ordinal) { "" }

    /** "Domingo, 17 de maio" / "Domingo, 17 de mayo" */
    fun longDate(date: LocalDate): String =
        "${dayName(date.dayOfWeek)}, ${date.dayOfMonth} de ${monthName(date.monthNumber)}"

    /** "... de 2026" */
    fun longDateWithYear(date: LocalDate): String =
        "${longDate(date)} de ${date.year}"

    /** "Maio de 2026" / "Mayo de 2026" */
    fun monthYearLabel(month: Int, year: Int): String =
        "${monthNameCapitalized(month)} ${invoke("de")} $year"

    /** "6 a 12 de agosto" / "6 al 12 de agosto" (ou com dois meses). */
    fun weekRange(start: LocalDate, end: LocalDate): String {
        val sep = if (language == AppLanguage.ES) "al" else "a"
        return if (start.monthNumber == end.monthNumber) {
            "${start.dayOfMonth} $sep ${end.dayOfMonth} de ${monthName(end.monthNumber)}"
        } else {
            "${start.dayOfMonth} de ${monthName(start.monthNumber)} $sep " +
                "${end.dayOfMonth} de ${monthName(end.monthNumber)}"
        }
    }

    /** "Segunda-feira (6)" / "Lunes (6)" */
    fun dayWithDate(date: LocalDate): String = "${dayName(date.dayOfWeek)} (${date.dayOfMonth})"

    companion object {
        private val MONTHS_PT = listOf(
            "janeiro", "fevereiro", "março", "abril", "maio", "junho",
            "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
        )
        private val MONTHS_ES = listOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
        )
        private val DAYS_PT = listOf(
            "Segunda-feira", "Terça-feira", "Quarta-feira", "Quinta-feira",
            "Sexta-feira", "Sábado", "Domingo",
        )
        private val DAYS_ES = listOf(
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo",
        )
    }
}

object EsTranslations {
    val map: Map<String, String> = mapOf(
        // Navegação / geral
        "Programação do Salão" to "Programa del Salón",
        "Dashboard" to "Panel",
        "Programações" to "Programas",
        "Fim de semana" to "Fin de semana",
        "Meio de semana" to "Entre semana",
        "Membros" to "Publicadores",
        "Limpeza" to "Limpieza",
        "Configurações" to "Configuración",
        "Geral" to "General",
        "de" to "de",
        "Hoje" to "Hoy",
        "Cancelar" to "Cancelar",
        "Salvar" to "Guardar",
        "Salvando..." to "Guardando...",
        "Salvar alterações" to "Guardar cambios",
        "Editar" to "Editar",
        "Excluir" to "Eliminar",
        "Remover" to "Eliminar",
        "Selecione" to "Seleccione",
        "OK" to "OK",
        "Ações" to "Acciones",
        "Sem resultados" to "Sin resultados",
        "Confirmar exclusão" to "Confirmar eliminación",
        "Idioma" to "Idioma",
        "Mês anterior" to "Mes anterior",
        "Próximo mês" to "Mes siguiente",

        // Dias curtos (seletor de dias)
        "Segunda" to "Lunes",
        "Terça" to "Martes",
        "Quarta" to "Miércoles",
        "Quinta" to "Jueves",
        "Sexta" to "Viernes",
        "Sábado" to "Sábado",
        "Domingo" to "Domingo",

        // Dashboard
        "Visão geral da congregação" to "Vista general de la congregación",
        "Próxima reunião" to "Próxima reunión",
        "Programações pendentes" to "Programas pendientes",
        "Limpeza da semana" to "Limpieza de la semana",
        "Sem grupo atribuído" to "Sin grupo asignado",
        "Carregando..." to "Cargando...",
        "Nenhuma reunião agendada" to "Ninguna reunión programada",
        "Amanhã" to "Mañana",
        "Em {0} dias" to "En {0} días",
        "Discurso" to "Discurso",
        "Leitura" to "Lectura",
        "Próxima semana" to "Próxima semana",
        "Tudo em dia" to "Todo al día",
        "Próximos {0} dias" to "Próximos {0} días",
        "reunião sem programação completa" to "reunión sin programa completo",
        "reuniões sem programação completa" to "reuniones sin programa completo",
        "+{0} outras" to "+{0} más",

        // Membros
        "Novo membro" to "Nuevo publicador",
        "Editar membro" to "Editar publicador",
        "Nome" to "Nombre",
        "Sobrenome" to "Apellido",
        "Buscar por nome/sobrenome" to "Buscar por nombre/apellido",
        "Nenhum membro cadastrado" to "Ningún publicador registrado",
        "Nenhum membro encontrado" to "Ningún publicador encontrado",
        "Adicione membros para escalá-los nas reuniões e atribuições." to
            "Añada publicadores para asignarlos en las reuniones y tareas.",
        "Cadastrar primeiro membro" to "Registrar primer publicador",
        "{0} cadastrados" to "{0} registrados",
        "Deseja realmente remover este membro?" to "¿Seguro que desea eliminar este publicador?",
        "Nenhum membro encontrado para o termo" to "Ningún publicador encontrado para el término",
        "Erro ao carregar membros" to "Error al cargar los publicadores",
        "Erro ao adicionar membro" to "Error al añadir el publicador",
        "Erro ao editar membro" to "Error al editar el publicador",
        "Erro ao remover membro" to "Error al eliminar el publicador",

        // Grupos de limpeza
        "Grupos de limpeza" to "Grupos de limpieza",
        "Novo grupo" to "Nuevo grupo",
        "Editar grupo" to "Editar grupo",
        "Nome do grupo" to "Nombre del grupo",
        "Buscar grupo" to "Buscar grupo",
        "Nenhum grupo de limpeza" to "Ningún grupo de limpieza",
        "Crie grupos para escalar os responsáveis pela limpeza semanal." to
            "Cree grupos para asignar a los responsables de la limpieza semanal.",
        "Criar primeiro grupo" to "Crear primer grupo",
        "Deseja realmente remover este grupo?" to "¿Seguro que desea eliminar este grupo?",
        "Nenhum grupo encontrado para o termo" to "Ningún grupo encontrado para el término",
        "Erro ao carregar grupos" to "Error al cargar los grupos",
        "Erro ao adicionar grupo" to "Error al añadir el grupo",
        "Erro ao editar grupo" to "Error al editar el grupo",
        "Erro ao remover grupo" to "Error al eliminar el grupo",

        // Limpeza (escala)
        "Atribuição semanal de grupos" to "Asignación semanal de grupos",
        "Grupo responsável" to "Grupo responsable",
        "Responsável" to "Responsable",
        "Cadastre grupos em Configurações" to "Registre grupos en Configuración",
        "Nenhuma reunião neste mês" to "Ninguna reunión en este mes",
        "Navegue para outro mês ou cadastre dias de reunião em Configurações." to
            "Vaya a otro mes o registre días de reunión en Configuración.",
        "Presidente" to "Presidente",

        // Áudio/vídeo e acomodadores
        "Áudio/vídeo e acomodadores" to "Audio/video y acomodadores",
        "Áudio/vídeo" to "Audio/video",
        "Designações técnicas de cada reunião" to "Asignaciones técnicas de cada reunión",
        "Áudio e vídeo" to "Audio y video",
        "Áudio" to "Audio",
        "Vídeo" to "Video",
        "Plataforma" to "Plataforma",
        "Plataforma 1" to "Plataforma 1",
        "Plataforma 2" to "Plataforma 2",
        "Microfones" to "Micrófonos",
        "Microfone 1" to "Micrófono 1",
        "Microfone 2" to "Micrófono 2",
        "Acomodadores" to "Acomodadores",
        "Acomodador do auditório" to "Acomodador auditorio",
        "Acomodador da entrada" to "Acomodador entrada",
        "{0} designados" to "{0} asignados",
        "Reunião do fim de semana às {0}" to "Reunión del fin de semana a las {0}",
        "Conflito de designação" to "Conflicto de asignación",
        "Já designado nesta reunião como: {0}" to "Ya asignado en esta reunión como: {0}",
        "{0} com conflito" to "{0} con conflicto",

        // Configurações / setup
        "Configuração Inicial" to "Configuración inicial",
        "Salvar Configuração" to "Guardar configuración",
        "Configurações salvas" to "Configuración guardada",
        "Carregando configurações..." to "Cargando configuración...",
        "Dados gerais e dias de reunião" to "Datos generales y días de reunión",
        "Dias e horários de reunião" to "Días y horarios de reunión",
        "Nome da Congregação *" to "Nombre de la congregación *",
        "Nome da congregação" to "Nombre de la congregación",
        "Nome da congregação é obrigatório" to "El nombre de la congregación es obligatorio",
        "Endereço" to "Dirección",
        "Telefone" to "Teléfono",
        "Congregação" to "Congregación",
        "Dias de Reunião *" to "Días de reunión *",
        "+ Adicionar dia" to "+ Añadir día",
        "Dia" to "Día",
        "Horário" to "Horario",
        "Adicione pelo menos 1 dia de reunião" to "Añada al menos 1 día de reunión",
        "Adicione pelo menos um dia de reunião" to "Añada al menos un día de reunión",
        "Erro ao salvar configurações: {0}" to "Error al guardar la configuración: {0}",
        "Erro ao salvar: {0}" to "Error al guardar: {0}",

        // Programa de fim de semana
        "Programações de fim de semana" to "Programa de fin de semana",
        "Discurso público e estudo de A Sentinela" to "Discurso público y estudio de La Atalaya",
        "Título do discurso" to "Título del discurso",
        "Orador" to "Orador",
        "Dirigente do estudo" to "Conductor del estudio",
        "Dirigente" to "Conductor",
        "Leitor" to "Lector",
        "Selecione uma reunião para editar" to "Seleccione una reunión para editar",
        "Escolha uma reunião na lista ao lado para preencher a programação." to
            "Elija una reunión de la lista para completar el programa.",
        "Reunião pública às {0}" to "Reunión pública a las {0}",
        "Realizada" to "Realizada",
        "Sem programação" to "Sin programa",
        "Exportar" to "Exportar",
        "Exportar PDF" to "Exportar PDF",
        "Exportar PNG" to "Exportar PNG",
        "Esta reunião (PDF)" to "Esta reunión (PDF)",
        "Esta reunião (PNG)" to "Esta reunión (PNG)",
        "Este mês (PDF)" to "Este mes (PDF)",
        "Este mês (PNG)" to "Este mes (PNG)",
        "Selecionar membro..." to "Seleccionar publicador...",
        "Nome ou selecionar da lista..." to "Nombre o seleccionar de la lista...",
        "Limpar seleção" to "Quitar selección",

        // Programa de meio de semana (S-140)
        "Programações de meio de semana" to "Programa de entre semana",
        "Nossa Vida e Ministério Cristão (S-140)" to "Vida y Ministerio Cristianos (S-140)",
        "Cabeçalho" to "Encabezado",
        "Leitura semanal da Bíblia" to "Lectura semanal de la Biblia",
        "Conselheiro da sala auxiliar" to "Consejero de la sala auxiliar",
        "Cântico inicial" to "Canción inicial",
        "Oração inicial" to "Oración inicial",
        "Tesouros da Palavra de Deus" to "Tesoros de la Biblia",
        "Discurso — título (10 min)" to "Discurso — título (10 min)",
        "Orador do discurso" to "Orador del discurso",
        "Joias espirituais (10 min)" to "Busquemos perlas escondidas (10 min)",
        "Leitura da Bíblia (4 min) — Estudante" to "Lectura de la Biblia (4 min) — Estudiante",
        "Faça seu melhor no ministério" to "Seamos mejores maestros",
        "Parte {0}" to "Parte {0}",
        "Título / designação" to "Título / asignación",
        "Estudante" to "Estudiante",
        "Ajudante" to "Ayudante",
        "Nossa Vida Cristã" to "Nuestra Vida Cristiana",
        "Cântico do meio" to "Canción intermedia",
        "Título da parte" to "Título de la parte",
        "Estudo bíblico de congregação (30 min)" to "Estudio bíblico de la congregación (30 min)",
        "Cântico final" to "Canción final",
        "Oração final" to "Oración final",
        "Min." to "Min.",
        "Reunião de meio de semana às {0}" to "Reunión de entre semana a las {0}",
        "Escolha uma reunião na lista ao lado para preencher a programação de meio de semana." to
            "Elija una reunión de la lista para completar el programa de entre semana.",
        "Importar apostila" to "Importar guía",
        "Importando..." to "Importando...",
        "Selecionar apostila (PDF)" to "Seleccionar guía (PDF)",
        "Documentos PDF" to "Documentos PDF",
        "Designações (S-89)" to "Asignaciones (S-89)",
        "Importadas {0} de {1} semanas. Agora só faltam as designações." to
            "Importadas {0} de {1} semanas. Ahora solo faltan las asignaciones.",
        "Nenhuma reunião do período corresponde às {0} semanas do PDF." to
            "Ninguna reunión del período corresponde a las {0} semanas del PDF.",
        "Nenhuma semana encontrada no PDF. Verifique se é a apostila (mwb) correta." to
            "No se encontró ninguna semana en el PDF. Verifique que sea la guía (mwb) correcta.",
        "Erro ao importar: {0}" to "Error al importar: {0}",

        // Importação do S-34 (bosquejos de discursos públicos)
        "Importar S-34" to "Importar S-34",
        "Selecionar S-34 (.jwpub)" to "Seleccionar S-34 (.jwpub)",
        "Publicações JWPUB" to "Publicaciones JWPUB",
        "Digite um título ou escolha um bosquejo do S-34..." to
            "Escriba un título o elija un bosquejo del S-34...",
        "Nenhum bosquejo importado — use \"Importar S-34\"" to
            "Ningún bosquejo importado — use \"Importar S-34\"",
        "Nenhum bosquejo encontrado" to "Ningún bosquejo encontrado",
        "Importados {0} bosquejos. Agora eles aparecem na lista do título do discurso." to
            "Importados {0} bosquejos. Ahora aparecen en la lista del título del discurso.",
        "Nenhum bosquejo encontrado no arquivo. Verifique se é o S-34 (.jwpub) correto." to
            "No se encontró ningún bosquejo en el archivo. Verifique que sea el S-34 (.jwpub) correcto.",

        // Exportação/importação de dados entre instalações
        "Dados" to "Datos",
        "Exportar e importar entre instalações" to "Exportar e importar entre instalaciones",
        "Exportar dados" to "Exportar datos",
        "Escolha o que vai no arquivo" to "Elija qué va en el archivo",
        "Congregação e dias de reunião" to "Congregación y días de reunión",
        "Reuniões" to "Reuniones",
        "Programas de fim de semana" to "Programas de fin de semana",
        "Programas de meio de semana" to "Programas de entre semana",
        "Necessário para os blocos escolhidos" to "Necesario para los bloques elegidos",
        "Proteger com senha" to "Proteger con contraseña",
        "Senha" to "Contraseña",
        "Mínimo de {0} caracteres" to "Mínimo de {0} caracteres",
        "Quem importar precisará da senha. Combine-a por outro caminho, não junto do arquivo." to
            "Quien importe necesitará la contraseña. Acuérdenla por otra vía, no junto al archivo.",
        "Sem senha o arquivo abre em qualquer instalação do app — não protege os nomes." to
            "Sin contraseña el archivo se abre en cualquier instalación de la app — no protege los nombres.",
        "Exportar arquivo" to "Exportar archivo",
        "Salvar pacote de dados" to "Guardar paquete de datos",
        "Pacote do Sonntag" to "Paquete de Sonntag",
        "Pacote salvo em {0}" to "Paquete guardado en {0}",
        "Erro ao exportar: {0}" to "Error al exportar: {0}",
        "Importar dados" to "Importar datos",
        "Abra um pacote recebido de outra instalação" to "Abra un paquete recibido de otra instalación",
        "Nada é gravado antes de você conferir o resumo das mudanças." to
            "Nada se guarda antes de que revise el resumen de los cambios.",
        "Escolher arquivo" to "Elegir archivo",
        "Selecionar pacote de dados" to "Seleccionar paquete de datos",
        "Este arquivo não é um pacote do Sonntag." to "Este archivo no es un paquete de Sonntag.",
        "Arquivo protegido" to "Archivo protegido",
        "Este pacote foi exportado com senha." to "Este paquete se exportó con contraseña.",
        "Senha incorreta ou arquivo alterado." to "Contraseña incorrecta o archivo alterado.",
        "Abrir" to "Abrir",
        "Conferir antes de aplicar" to "Revisar antes de aplicar",
        "Aplicar" to "Aplicar",
        "{0} registros novos" to "{0} registros nuevos",
        "{0} atualizações" to "{0} actualizaciones",
        "{0} ignorados por referência ausente" to "{0} ignorados por referencia ausente",
        "Estes registros mudaram dos dois lados. Marque os que devem vir do arquivo; os demais mantêm o que está aqui." to
            "Estos registros cambiaron en ambos lados. Marque los que deben venir del archivo; los demás conservan lo de aquí.",
        "aqui: {0} · arquivo: {1}" to "aquí: {0} · archivo: {1}",
        "Importação concluída: {0} registros aplicados." to "Importación finalizada: {0} registros aplicados.",

        // Sincronização pela rede local
        "Rede local" to "Red local",
        "Trocar dados com quem está na mesma rede" to "Intercambiar datos con quien está en la misma red",
        "Ficar visível na rede" to "Hacerse visible en la red",
        "Os dois aparelhos precisam estar visíveis, na mesma rede." to
            "Los dos aparatos deben estar visibles, en la misma red.",
        "Seu código" to "Su código",
        "Informe-o a quem for iniciar a troca." to "Indíqueselo a quien inicie el intercambio.",
        "Procurando aparelhos..." to "Buscando aparatos...",
        "Sincronizar" to "Sincronizar",
        "Trocando com {0}..." to "Intercambiando con {0}...",
        "Código de {0}" to "Código de {0}",
        "Digite os quatro dígitos que aparecem no outro aparelho." to
            "Escriba los cuatro dígitos que aparecen en el otro aparato.",
        "Código" to "Código",
        "Código incorreto." to "Código incorrecto.",
        "Nada novo de {0}." to "Nada nuevo de {0}.",
        "Não foi possível falar com {0}." to "No se pudo contactar con {0}.",
        "Aparelho" to "Aparato",

        // Comuns de reunião
        "Selecione uma reunião para editar (meio de semana)" to "Seleccione una reunión (entre semana)",
    )
}
