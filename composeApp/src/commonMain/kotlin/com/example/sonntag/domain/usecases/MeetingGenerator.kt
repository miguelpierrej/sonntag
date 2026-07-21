package com.example.sonntag.domain.usecases

import com.example.sonntag.data.repos.MeetingDaysRepository
import com.example.sonntag.data.repos.MeetingsRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class MeetingGenerator(
    private val meetingDaysRepository: MeetingDaysRepository,
    private val meetingsRepository: MeetingsRepository,
) {

    fun generateNext12Months() {
        val days = meetingDaysRepository.getAllOnce()
        if (days.isEmpty()) return

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val startDate = today.plus(DatePeriod(days = 1))
        val endDate = today.plus(DatePeriod(months = 12))

        val existing = meetingsRepository
            .getByDateRangeOnce(startDate.toString(), endDate.toString())
            .map { "${it.data_}|${it.hora}|${it.tipo}" }
            .toMutableSet()

        var cursor = startDate
        while (cursor <= endDate) {
            val isoDay = cursor.dayOfWeek.isoDayNumber.toLong()
            val dayDefs = days.filter { it.dia_semana == isoDay }
            val tipo = if (isoDay >= 6) "WEEKEND" else "WEEKDAY"

            dayDefs.forEach { def ->
                val key = "${cursor}|${def.hora}|$tipo"
                if (!existing.contains(key)) {
                    meetingsRepository.insert(cursor.toString(), def.hora, tipo)
                    existing.add(key)
                }
            }

            cursor = cursor.plus(DatePeriod(days = 1))
        }
    }
}

