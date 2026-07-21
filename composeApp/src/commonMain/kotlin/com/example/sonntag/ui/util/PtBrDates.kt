package com.example.sonntag.ui.util

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

fun monthNamePt(month: Int): String = when (month) {
    1 -> "janeiro"
    2 -> "fevereiro"
    3 -> "março"
    4 -> "abril"
    5 -> "maio"
    6 -> "junho"
    7 -> "julho"
    8 -> "agosto"
    9 -> "setembro"
    10 -> "outubro"
    11 -> "novembro"
    12 -> "dezembro"
    else -> ""
}

fun monthNamePtCapitalized(month: Int): String {
    val name = monthNamePt(month)
    return name.replaceFirstChar { it.titlecase() }
}

fun dayNamePt(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Segunda-feira"
    DayOfWeek.TUESDAY -> "Terça-feira"
    DayOfWeek.WEDNESDAY -> "Quarta-feira"
    DayOfWeek.THURSDAY -> "Quinta-feira"
    DayOfWeek.FRIDAY -> "Sexta-feira"
    DayOfWeek.SATURDAY -> "Sábado"
    DayOfWeek.SUNDAY -> "Domingo"
}

/** "Domingo, 17 de maio" */
fun longDateLabel(date: LocalDate): String =
    "${dayNamePt(date.dayOfWeek)}, ${date.dayOfMonth} de ${monthNamePt(date.monthNumber)}"

/** "Domingo, 17 de maio de 2026" */
fun longDateLabelWithYear(date: LocalDate): String =
    "${longDateLabel(date)} de ${date.year}"
