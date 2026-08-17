package com.example.sonntag.domain.usecases

import com.example.sonntag.data.repos.ShiftInput
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Uma linha do padrao semanal, sem depender das classes geradas pelo banco. */
data class WeeklySlot(
    val diaSemana: Int, // 1 = segunda ... 7 = domingo, como em meeting_days
    val horaInicio: String,
    val horaFim: String? = null,
    val spotId: Long? = null,
    val ordem: Long = 0L,
)

/**
 * Traduz o padrao semanal nos turnos de um mes.
 *
 * Fica separado da tela e do banco porque e aqui que moram os casos chatos do
 * calendario — mes que comeca no sabado, mes com cinco domingos, fevereiro — e essa
 * conta precisa ser verificavel sem abrir o app.
 */
object PreachingMonthGenerator {

    /**
     * Os turnos que faltam no mes para cumprir [padrao].
     *
     * [existentes] sao os pares data|hora que ja estao gravados: repetir a geracao
     * nao duplica nada nem desfaz um ajuste feito a mao.
     */
    fun turnosDoMes(
        ano: Int,
        mes: Int,
        padrao: List<WeeklySlot>,
        existentes: Set<Pair<String, String>> = emptySet(),
    ): List<Pair<LocalDate, ShiftInput>> {
        if (padrao.isEmpty()) return emptyList()
        val primeiro = LocalDate(ano, mes, 1)
        val ultimo = primeiro.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))

        val saida = mutableListOf<Pair<LocalDate, ShiftInput>>()
        var dia = primeiro
        while (dia <= ultimo) {
            val diaIso = dia.dayOfWeek.ordinal + 1
            padrao.filter { it.diaSemana == diaIso }
                .sortedWith(compareBy({ it.horaInicio }, { it.ordem }))
                .forEach { slot ->
                    if ((dia.toString() to slot.horaInicio) !in existentes) {
                        saida += dia to ShiftInput(
                            horaInicio = slot.horaInicio,
                            horaFim = slot.horaFim,
                            spotId = slot.spotId,
                            ordem = slot.ordem,
                        )
                    }
                }
            dia = dia.plus(DatePeriod(days = 1))
        }
        return saida
    }

    /**
     * As semanas que cobrem o mes, de domingo a sabado — a mesma grade do documento
     * impresso, para a tela e a folha mostrarem os dias nas mesmas posicoes.
     *
     * Sempre pelo menos cinco linhas, no maximo seis, como no modelo.
     */
    fun gradeDoMes(ano: Int, mes: Int): List<List<LocalDate>> {
        val primeiro = LocalDate(ano, mes, 1)
        // dayOfWeek.ordinal: segunda = 0 ... domingo = 6; a grade comeca no domingo.
        val recuo = (primeiro.dayOfWeek.ordinal + 1) % 7
        val ultimo = primeiro.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))

        val semanas = mutableListOf<List<LocalDate>>()
        var cursor = primeiro.minus(DatePeriod(days = recuo))
        while (semanas.size < 6) {
            semanas += (0..6).map { cursor.plus(DatePeriod(days = it)) }
            cursor = cursor.plus(DatePeriod(days = 7))
            if (cursor > ultimo && semanas.size >= 5) break
        }
        return semanas
    }
}
