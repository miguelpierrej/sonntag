package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.data.sqldelight.Meeting_days
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class MeetingDaysRepository(private val database: SonntagDatabase) {
    fun getAll(): Flow<List<Meeting_days>> {
        return database.schemaQueries
            .getAllMeetingDays()
            .asFlow()
            .mapToList(Dispatchers.Default)
    }

    fun getById(id: Long): Flow<Meeting_days?> {
        return database.schemaQueries
            .getMeetingDayById(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
    }

    fun insert(diaSemana: Long, hora: String) {
        database.schemaQueries.insertMeetingDay(diaSemana, hora)
    }

    fun update(id: Long, diaSemana: Long, hora: String) {
        database.schemaQueries.updateMeetingDay(diaSemana, hora, id)
    }

    fun delete(id: Long) {
        database.schemaQueries.deleteMeetingDay(id)
    }

    fun getAllOnce(): List<Meeting_days> {
        return database.schemaQueries.getAllMeetingDays().executeAsList()
    }

    fun replaceAll(days: List<Pair<Long, String>>) {
        database.schemaQueries.deleteAllMeetingDays()
        days.forEach { (diaSemana, hora) ->
            database.schemaQueries.insertMeetingDay(diaSemana, hora)
        }
    }
}


