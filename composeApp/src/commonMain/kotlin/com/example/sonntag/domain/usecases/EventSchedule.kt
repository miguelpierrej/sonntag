package com.example.sonntag.domain.usecases

import com.example.sonntag.data.sqldelight.Events
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * Tipos de evento. O `id` e o texto gravado na coluna `tipo`; o `label` e a chave de
 * traducao mostrada na interface.
 */
enum class EventType(val id: String, val label: String) {
    ASSEMBLEIA("ASSEMBLEIA", "Assembleia"),
    CONGRESSO("CONGRESSO", "Congresso"),
    COMEMORACAO("COMEMORACAO", "Comemoração"),
    OUTRO("OUTRO", "Outro");

    companion object {
        fun fromId(id: String?): EventType = entries.firstOrNull { it.id == id } ?: OUTRO
    }
}

data class CongregationEvent(
    val id: Long,
    val nome: String,
    val date: LocalDate,
    val tipo: EventType,
)

fun Events.toDomain(): CongregationEvent = CongregationEvent(
    id = id,
    nome = nome,
    date = LocalDate.parse(data_),
    tipo = EventType.fromId(tipo),
)

/**
 * Onde os eventos tomam o lugar das reunioes.
 *
 * Assembleia e congresso ocupam a semana inteira: nem a reuniao de meio de semana nem
 * a de fim de semana acontecem. A comemoracao depende de onde cai — no fim de semana
 * substitui so a reuniao daquele mesmo dia (a de meio de semana e normal); no meio da
 * semana derruba a reuniao de meio de semana daquela semana. "Outro" nunca cancela
 * nada: existe apenas para ser anunciado.
 *
 * Uma reuniao substituida continua existindo no banco — o que muda e que ninguem e
 * designado nela e, no lugar do formulario, aparece o anuncio do evento.
 */
class EventSchedule(events: List<CongregationEvent>) {

    private val events = events.sortedBy { it.date }

    /** O evento que substitui esta reuniao, ou null se ela acontece normalmente. */
    fun replacing(meetingDate: LocalDate, meetingTipo: String): CongregationEvent? =
        events.firstOrNull { it.replaces(meetingDate, meetingTipo) }

    /** Eventos da semana que comeca em [monday], cancelem ou nao reunioes. */
    fun inWeek(monday: LocalDate): List<CongregationEvent> =
        events.filter { weekStart(it.date) == monday }

    /** O evento que deixa a semana inteira sem reuniao (assembleia ou congresso). */
    fun replacingWeek(monday: LocalDate): CongregationEvent? =
        inWeek(monday).firstOrNull {
            it.tipo == EventType.ASSEMBLEIA || it.tipo == EventType.CONGRESSO
        }
}

private fun CongregationEvent.replaces(meetingDate: LocalDate, meetingTipo: String): Boolean {
    val mesmaSemana = weekStart(date) == weekStart(meetingDate)
    return when (tipo) {
        EventType.ASSEMBLEIA, EventType.CONGRESSO -> mesmaSemana
        EventType.COMEMORACAO ->
            if (date.dayOfWeek.isoDayNumber >= 6) date == meetingDate
            else mesmaSemana && meetingTipo == "WEEKDAY"
        EventType.OUTRO -> false
    }
}
