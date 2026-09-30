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
        "Responsabilidades (opcional)" to "Responsabilidades (opcional)",
        "Ancião" to "Anciano",
        "Servo ministerial" to "Siervo ministerial",
        "Pioneiro" to "Precursor",
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

        // Pregação (pontos, grupos e calendário)
        "Pregação" to "Predicación",
        "Pontos e grupos de pregação" to "Puntos y grupos de predicación",
        "Pontos de pregação" to "Puntos de predicación",
        "Onde o carrinho fica e de onde os grupos saem." to
            "Dónde está el carrito y desde dónde salen los grupos.",
        "Novo ponto" to "Nuevo punto",
        "Editar ponto" to "Editar punto",
        "Nome do ponto" to "Nombre del punto",
        "Nenhum ponto cadastrado" to "Ningún punto registrado",
        "Deseja realmente remover este ponto?" to "¿Seguro que desea eliminar este punto?",
        "Usado em" to "Se usa en",
        "Carrinho" to "Carrito",
        "Carrinho e pregação" to "Carrito y predicación",
        "Grupos de pregação" to "Grupos de predicación",
        "Saem no rodapé do programa, na ordem desta lista." to
            "Salen al pie del programa, en el orden de esta lista.",
        "Nenhum grupo cadastrado" to "Ningún grupo registrado",
        "Auxiliar" to "Auxiliar",
        "Ponto de encontro" to "Punto de encuentro",
        "Sem dirigente" to "Sin conductor",
        "Erro ao carregar os cadastros de pregação" to "Error al cargar los registros de predicación",
        "Erro ao salvar o ponto" to "Error al guardar el punto",
        "Erro ao remover o ponto" to "Error al eliminar el punto",
        "Erro ao salvar o grupo" to "Error al guardar el grupo",
        "Erro ao remover o grupo" to "Error al eliminar el grupo",
        "Erro ao reordenar os grupos" to "Error al reordenar los grupos",
        "Exportar lista" to "Exportar lista",
        "Publicadores ({0})" to "Publicadores ({0})",
        "Ocultar publicadores" to "Ocultar publicadores",
        "Nenhum publicador neste grupo" to "Ningún publicador en este grupo",
        "Adicionar publicador" to "Añadir publicador",
        "Publicador" to "Publicador",
        "Cada publicador pertence a um grupo só; escolher aqui tira do grupo anterior." to
            "Cada publicador pertenece a un solo grupo; elegirlo aquí lo quita del grupo anterior.",
        "Todos os publicadores já estão neste grupo" to "Todos los publicadores ya están en este grupo",
        "Erro ao exportar a lista de grupos" to "Error al exportar la lista de grupos",
        "Escreva o nome" to "Escriba el nombre",

        // Calendário de pregação
        "Programação dos carrinhos" to "Programación de los carritos",
        "Programação da pregação" to "Programación de predicación",
        "Carrinhos" to "Carritos",
        "Cadastros" to "Registros",
        "Padrão semanal" to "Patrón semanal",
        "Gerar mês" to "Generar mes",
        "Adicionar ao padrão" to "Añadir al patrón",
        "Adicionar" to "Añadir",
        "Os turnos que se repetem toda semana. \"Gerar mês\" cria o que falta, sem mexer no que já existe." to
            "Los turnos que se repiten cada semana. \"Generar mes\" crea lo que falta, sin tocar lo que ya existe.",
        "Defina o padrão semanal antes de gerar o mês." to
            "Defina el patrón semanal antes de generar el mes.",
        "O mês já estava completo." to "El mes ya estaba completo.",
        "{0} turnos criados." to "{0} turnos creados.",
        "Nenhum turno neste dia" to "Ningún turno en este día",
        "Novo turno" to "Nuevo turno",
        "Editar turno" to "Editar turno",
        "Início" to "Inicio",
        "Fim" to "Fin",
        "Ponto" to "Punto",
        "Designado {0}" to "Asignado {0}",
        "Destaque" to "Destacado",
        "Ex.: Todos os grupos no Salão" to "Ej.: Todos los grupos en el Salón",
        "Observação do rodapé" to "Observación al pie",
        "Sai abaixo do calendário no documento, junto com os grupos." to
            "Sale debajo del calendario en el documento, junto con los grupos.",
        "Erro ao carregar o calendário" to "Error al cargar el calendario",
        "Erro ao salvar o turno" to "Error al guardar el turno",
        "Erro ao remover o turno" to "Error al eliminar el turno",
        "Erro ao salvar o padrão" to "Error al guardar el patrón",
        "Erro ao remover o padrão" to "Error al eliminar el patrón",
        "Erro ao gerar o mês" to "Error al generar el mes",
        "Erro ao salvar a observação" to "Error al guardar la observación",
        "Dom" to "Dom",
        "Seg" to "Lun",
        "Ter" to "Mar",
        "Qua" to "Mié",
        "Qui" to "Jue",
        "Sex" to "Vie",
        "Sáb" to "Sáb",

        // Revisão da importação
        "Só os registros novos vêm marcados. Atualizações e exclusões mudam o que já existe aqui — marque o que quiser aceitar. Nas divergências, escolha a versão de cada registro." to
            "Solo los registros nuevos vienen marcados. Las actualizaciones y las eliminaciones cambian lo que ya existe aquí — marque lo que quiera aceptar. En las divergencias, elija la versión de cada registro.",
        "Aplicar {0}" to "Aplicar {0}",
        "{0} novos" to "{0} nuevos",
        "{0} exclusões" to "{0} eliminaciones",
        "{0} divergências" to "{0} divergencias",
        "{0} de {1} marcados" to "{0} de {1} marcados",
        "Revisar" to "Revisar",
        "Fechar" to "Cerrar",
        "Outros" to "Otros",
        "{0} ignorados: referência ausente ou duplicata já apagada aqui" to
            "{0} ignorados: referencia ausente o duplicado ya borrado aquí",

        // Eventos (assembleias, congressos e comemorações)
        "Eventos" to "Eventos",
        "Semanas sem reunião por assembleia, congresso ou comemoração" to
            "Semanas sin reunión por asamblea, congreso o conmemoración",
        "Assembleias, congressos e comemorações substituem as reuniões da semana." to
            "Las asambleas, los congresos y las conmemoraciones sustituyen las reuniones de la semana.",
        "Novo evento" to "Nuevo evento",
        "Editar evento" to "Editar evento",
        "Nome do evento" to "Nombre del evento",
        "Data" to "Fecha",
        "Tipo" to "Tipo",
        "Use o formato dd/mm/aaaa" to "Use el formato dd/mm/aaaa",
        "Assembleia" to "Asamblea",
        "Congresso" to "Congreso",
        "Comemoração" to "Conmemoración",
        "Outro" to "Otro",
        "Nenhum evento cadastrado" to "Ningún evento registrado",
        "Cadastre assembleias, congressos e comemorações para que as semanas afetadas não peçam designações." to
            "Registre asambleas, congresos y conmemoraciones para que las semanas afectadas no pidan asignaciones.",
        "Cadastrar primeiro evento" to "Registrar primer evento",
        "Deseja realmente remover este evento? As reuniões da semana voltam a pedir designações." to
            "¿Seguro que desea eliminar este evento? Las reuniones de la semana volverán a pedir asignaciones.",
        "Sem reunião de meio de semana nem de fim de semana nesta semana." to
            "Sin reunión entre semana ni de fin de semana en esta semana.",
        "Substitui a reunião deste mesmo dia; a de meio de semana acontece normalmente." to
            "Sustituye la reunión de ese mismo día; la de entre semana se celebra normalmente.",
        "Sem reunião de meio de semana nesta semana; o fim de semana é normal." to
            "Sin reunión entre semana en esta semana; el fin de semana es normal.",
        "Apenas anunciado: nenhuma reunião é cancelada." to
            "Solo se anuncia: no se cancela ninguna reunión.",
        "Sem reunião · {0}: {1}" to "Sin reunión · {0}: {1}",
        "Sem reunião · {0}" to "Sin reunión · {0}",
        "Próximos eventos" to "Próximos eventos",
        "Erro ao carregar eventos" to "Error al cargar los eventos",
        "Erro ao adicionar evento" to "Error al añadir el evento",
        "Erro ao editar evento" to "Error al editar el evento",
        "Erro ao remover evento" to "Error al eliminar el evento",

        // Sincronização na nuvem
        "Sincronização na nuvem" to "Sincronización en la nube",
        "Compartilhar os dados com outros aparelhos pela internet" to
            "Compartir los datos con otros dispositivos por internet",
        "URL repartida: usuário, senha, porta e banco foram para os campos abaixo." to
            "URL separada: usuario, contraseña, puerto y base de datos pasaron a los campos de abajo.",
        "Cole a URL de conexão do PostgreSQL (Supabase, Neon ou servidor próprio) ou preencha os campos." to
            "Pegue la URL de conexión de PostgreSQL (Supabase, Neon o servidor propio) o complete los campos.",
        "Servidor ou URL de conexão" to "Servidor o URL de conexión",
        "Porta" to "Puerto",
        "Banco" to "Base de datos",
        "Usuário" to "Usuario",
        "Senha do banco" to "Contraseña de la base de datos",
        "Senha da congregação" to "Contraseña de la congregación",
        "Cifra os dados na nuvem. Todos os aparelhos usam a mesma; sem ela ninguém lê os dados, nem quem administra o servidor." to
            "Cifra los datos en la nube. Todos los dispositivos usan la misma; sin ella nadie lee los datos, ni quien administra el servidor.",
        "Conectar" to "Conectar",
        "Conectando..." to "Conectando...",
        "Conectado a {0}" to "Conectado a {0}",
        "Última sincronização: {0}" to "Última sincronización: {0}",
        "Ainda não sincronizado" to "Aún no sincronizado",
        "Sincronizar agora" to "Sincronizar ahora",
        "Desconectar" to "Desconectar",
        "Desconectar da nuvem?" to "¿Desconectar de la nube?",
        "Os dados continuam neste aparelho, mas deixam de ser sincronizados. Para reconectar, será preciso digitar as senhas de novo." to
            "Los datos siguen en este dispositivo, pero dejan de sincronizarse. Para reconectar habrá que escribir las contraseñas de nuevo.",
        "A nuvem já tem dados" to "La nube ya tiene datos",
        "Este aparelho também tem. Como juntar?" to "Este dispositivo también tiene. ¿Cómo combinarlos?",
        "Usar os dados da nuvem: o que só existe neste aparelho é descartado. Recomendado para quem está entrando numa nuvem já em uso." to
            "Usar los datos de la nube: lo que solo existe en este dispositivo se descarta. Recomendado para quien se une a una nube que ya está en uso.",
        "Combinar: junta os dois lados e, em cada registro, fica a versão mais recente. Se as bases foram criadas separadamente, os membros podem ficar duplicados." to
            "Combinar: une ambos lados y, en cada registro, queda la versión más reciente. Si las bases se crearon por separado, los miembros pueden quedar duplicados.",
        "Combinar" to "Combinar",
        "Usar os dados da nuvem" to "Usar los datos de la nube",
        "{0} recebidos, {1} enviados." to "{0} recibidos, {1} enviados.",
        "{0} edições simultâneas: ficou a mais recente." to "{0} ediciones simultáneas: quedó la más reciente.",
        "{0} registros ignorados." to "{0} registros ignorados.",
        "Erro inesperado: {0}" to "Error inesperado: {0}",
        "Não foi possível alcançar o servidor. Confira o endereço, a porta e a internet." to
            "No se pudo alcanzar el servidor. Revise la dirección, el puerto y la conexión a internet.",
        "Usuário ou senha do banco incorretos." to "Usuario o contraseña de la base de datos incorrectos.",
        "O banco de dados informado não existe no servidor." to "La base de datos indicada no existe en el servidor.",
        "Este usuário não tem permissão para criar tabelas no banco." to
            "Este usuario no tiene permiso para crear tablas en la base de datos.",
        "Estes dados foram criados por uma versão mais nova do Sonntag. Atualize o aplicativo." to
            "Estos datos fueron creados por una versión más nueva de Sonntag. Actualice la aplicación.",
        "A senha da congregação não confere com a usada nesta nuvem." to
            "La contraseña de la congregación no coincide con la usada en esta nube.",
        "A nuvem não está configurada. Conecte-se de novo." to "La nube no está configurada. Vuelva a conectarse.",
        "Erro no servidor: {0}" to "Error en el servidor: {0}",
        "{0} alterações aguardando envio" to "{0} cambios esperando envío",
        "{0} edições simultâneas registradas" to "{0} ediciones simultáneas registradas",
        "Última troca: {0} recebidos, {1} enviados" to "Último intercambio: {0} recibidos, {1} enviados",
        "Iniciando..." to "Iniciando...",
        "Sincronizando..." to "Sincronizando...",
        "Sincronizado {0}" to "Sincronizado {0}",
        "Sincronizado" to "Sincronizado",
        "Sem conexão. As alterações ficam guardadas e sobem quando a conexão voltar." to
            "Sin conexión. Los cambios quedan guardados y se envían cuando vuelva la conexión.",
        "Falha ao sincronizar. Última vez {0}" to "Error al sincronizar. Última vez {0}",
        "Falha ao sincronizar" to "Error al sincronizar",
        "agora mesmo" to "ahora mismo",
        "há {0} s" to "hace {0} s",
        "há {0} min" to "hace {0} min",
        "há {0} h" to "hace {0} h",
        "há {0} dias" to "hace {0} días",

        // Revisão de divergências e conflitos
        "Arquivo" to "Archivo",
        "1 edição simultânea para revisar" to "1 edición simultánea por revisar",
        "1 alteração aguardando envio" to "1 cambio esperando envío",
        "Os dados da nuvem foram apagados ou trocados. Desconecte e conecte-se de novo." to
            "Los datos de la nube fueron borrados o reemplazados. Desconéctese y vuelva a conectarse.",
        "(registro desconhecido)" to "(registro desconocido)",
        "(sem nome)" to "(sin nombre)",
        "(vazio)" to "(vacío)",
        "Acomodador 1" to "Acomodador 1",
        "Acomodador 2" to "Acomodador 2",
        "Ano" to "Año",
        "Ativo" to "Activo",
        "Conselheiro" to "Consejero",
        "Dados da congregação" to "Datos de la congregación",
        "Designado 1" to "Asignado 1",
        "Designado 2" to "Asignado 2",
        "Designado 3" to "Asignado 3",
        "Designado 4" to "Asignado 4",
        "Dia da semana" to "Día de la semana",
        "Dois aparelhos mudaram o mesmo registro. Escolha a versão que deve ficar em todos." to
            "Dos dispositivos cambiaron el mismo registro. Elija la versión que debe quedar en todos.",
        "Edições simultâneas" to "Ediciones simultáneas",
        "Este dispositivo" to "Este dispositivo",
        "Este dispositivo ({0})" to "Este dispositivo ({0})",
        "Estudo bíblico: dirigente" to "Estudio bíblico: conductor",
        "Estudo bíblico: leitor" to "Estudio bíblico: lector",
        "Excluído" to "Eliminado",
        "Grupo" to "Grupo",
        "Grupo de limpeza {0}" to "Grupo de limpieza {0}",
        "Joias espirituais" to "Perlas escondidas",
        "Leitura da Bíblia" to "Lectura de la Biblia",
        "Leitura da semana" to "Lectura de la semana",
        "Limpeza · semana {0} de {1}" to "Limpieza · semana {0} de {1}",
        "Local" to "Lugar",
        "Ministério 1: ajudante" to "Ministerio 1: ayudante",
        "Ministério 1: estudante" to "Ministerio 1: estudiante",
        "Ministério 1: minutos" to "Ministerio 1: minutos",
        "Ministério 1: tema" to "Ministerio 1: tema",
        "Ministério 2: ajudante" to "Ministerio 2: ayudante",
        "Ministério 2: estudante" to "Ministerio 2: estudiante",
        "Ministério 2: minutos" to "Ministerio 2: minutos",
        "Ministério 2: tema" to "Ministerio 2: tema",
        "Ministério 3: ajudante" to "Ministerio 3: ayudante",
        "Ministério 3: estudante" to "Ministerio 3: estudiante",
        "Ministério 3: minutos" to "Ministerio 3: minutos",
        "Ministério 3: tema" to "Ministerio 3: tema",
        "Ministério 4: ajudante" to "Ministerio 4: ayudante",
        "Ministério 4: estudante" to "Ministerio 4: estudiante",
        "Ministério 4: minutos" to "Ministerio 4: minutos",
        "Ministério 4: tema" to "Ministerio 4: tema",
        "Mês" to "Mes",
        "Nada a revisar: as versões já coincidem." to "Nada que revisar: las versiones ya coinciden.",
        "Não" to "No",
        "Observação" to "Observación",
        "Orador visitante" to "Orador visitante",
        "Ordem" to "Orden",
        "Outro dispositivo" to "Otro dispositivo",
        "Programa de fim de semana · {0}" to "Programa de fin de semana · {0}",
        "Programa de meio de semana · {0}" to "Programa de entre semana · {0}",
        "Reunião" to "Reunión",
        "Semana" to "Semana",
        "Sim" to "Sí",
        "Situação" to "Estado",
        "Tema do discurso" to "Tema del discurso",
        "Tesouros: orador" to "Tesoros: orador",
        "Tesouros: tema" to "Tesoros: tema",
        "Texto" to "Texto",
        "Todas deste dispositivo" to "Todas de este dispositivo",
        "Todas do outro" to "Todas del otro",
        "Vida cristã 1: designado" to "Vida cristiana 1: asignado",
        "Vida cristã 1: minutos" to "Vida cristiana 1: minutos",
        "Vida cristã 1: tema" to "Vida cristiana 1: tema",
        "Vida cristã 2: designado" to "Vida cristiana 2: asignado",
        "Vida cristã 2: minutos" to "Vida cristiana 2: minutos",
        "Vida cristã 2: tema" to "Vida cristiana 2: tema",
        "semana {0}" to "semana {0}",
        "{0} edições simultâneas para revisar" to "{0} ediciones simultáneas por revisar",
        "{0} · em uso" to "{0} · en uso",
        "Áudio/vídeo · {0}" to "Audio/video · {0}",

        // Comuns de reunião
        "Selecione uma reunião para editar (meio de semana)" to "Seleccione una reunión (entre semana)",
    )
}
