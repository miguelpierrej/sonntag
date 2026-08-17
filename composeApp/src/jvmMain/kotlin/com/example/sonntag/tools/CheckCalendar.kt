package com.example.sonntag.tools

import com.example.sonntag.domain.usecases.PreachingMonthGenerator
import com.example.sonntag.domain.usecases.WeeklySlot
import kotlinx.datetime.LocalDate

/** Confere a grade e o gerador do mes nos casos que costumam quebrar calendario. */
fun main() {
    val casos = listOf(
        Triple(2026, 8, "agosto/26 — começa no sábado"),
        Triple(2026, 2, "fevereiro/26 — 28 dias"),
        Triple(2021, 2, "fevereiro/21 — 28 dias começando no domingo"),
        Triple(2026, 11, "novembro/26 — começa no domingo"),
        Triple(2025, 6, "junho/25 — o mês do modelo impresso"),
        Triple(2024, 2, "fevereiro/24 — bissexto"),
    )

    casos.forEach { (ano, mes, nome) ->
        val grade = PreachingMonthGenerator.gradeDoMes(ano, mes)
        val doMes = grade.flatten().filter { it.monthNumber == mes && it.year == ano }
        val ultimo = LocalDate(ano, mes, 1).let { p ->
            (1..31).map { d -> runCatching { LocalDate(ano, mes, d) }.getOrNull() }.filterNotNull().max()
        }
        val ok = doMes.size == ultimo.dayOfMonth &&
            grade.all { it.size == 7 } &&
            grade.first().first().dayOfWeek.ordinal == 6 && // domingo
            grade.size in 5..6
        println("$nome: ${grade.size} semanas, ${doMes.size}/${ultimo.dayOfMonth} dias — ${if (ok) "ok" else "FALHOU"}")
    }

    // Padrao do modelo de junho: terça 12:00-14:00, sábado 7:00-9:00, domingo 12:00-14:00
    val padrao = listOf(
        WeeklySlot(2, "12:00", "14:00", spotId = 3),
        WeeklySlot(6, "07:00", "09:00", spotId = 5),
        WeeklySlot(7, "12:00", "14:00", spotId = 1),
    )
    val gerado = PreachingMonthGenerator.turnosDoMes(2025, 6, padrao)
    println()
    println("junho/25 com o padrão do modelo: ${gerado.size} turnos")
    gerado.groupBy { it.first.dayOfWeek }.forEach { (dia, lista) ->
        println("   $dia: ${lista.size}  (${lista.joinToString { it.first.dayOfMonth.toString() }})")
    }

    val jaExiste = setOf("2025-06-03" to "12:00", "2025-06-07" to "07:00")
    val segundaVez = PreachingMonthGenerator.turnosDoMes(2025, 6, padrao, jaExiste)
    println("gerando de novo com 2 turnos já gravados: ${segundaVez.size} novos " +
        "(esperado ${gerado.size - 2})")
}
