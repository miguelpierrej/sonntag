package com.example.sonntag.domain.usecases

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/** Segunda-feira da semana a que [date] pertence. */
fun weekStart(date: LocalDate): LocalDate {
    val shift = date.dayOfWeek.isoDayNumber - 1
    return date.plus(DatePeriod(days = -shift))
}

/** Par (ano ISO, semana ISO) de [date], conforme a norma ISO-8601. */
fun isoYearWeek(date: LocalDate): Pair<Int, Int> {
    val day = date.dayOfWeek.isoDayNumber
    val thursday = date.plus(DatePeriod(days = 4 - day))
    val weekYear = thursday.year

    val jan4 = LocalDate(weekYear, 1, 4)
    val jan4Day = jan4.dayOfWeek.isoDayNumber
    var firstThursday = jan4.plus(DatePeriod(days = 4 - jan4Day))

    var week = 1
    while (firstThursday < thursday) {
        firstThursday = firstThursday.plus(DatePeriod(days = 7))
        week++
    }

    return weekYear to week
}
