package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.sync.SyncStamp
import com.example.sonntag.data.sqldelight.Meeting_days
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class MeetingDaysRepository(
    private val database: SonntagDatabase,
    private val stamp: SyncStamp,
) {
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
        database.schemaQueries.insertMeetingDay(diaSemana, hora, stamp.newRowUuid(), stamp.now(), stamp.deviceId)
    }

    fun update(id: Long, diaSemana: Long, hora: String) {
        database.schemaQueries.updateMeetingDay(diaSemana, hora, stamp.now(), stamp.deviceId, id)
    }

    fun delete(id: Long) {
        database.schemaQueries.deleteMeetingDay(stamp.now(), stamp.deviceId, id)
    }

    fun getAllOnce(): List<Meeting_days> {
        return database.schemaQueries.getAllMeetingDays().executeAsList()
    }

    fun replaceAll(days: List<Pair<Long, String>>) {
        database.transaction {
            database.schemaQueries.deleteAllMeetingDays(stamp.now(), stamp.deviceId)
            days.forEach { (diaSemana, hora) ->
                database.schemaQueries.insertMeetingDay(
                    diaSemana, hora, stamp.newRowUuid(), stamp.now(), stamp.deviceId,
                )
            }
        }
    }
}


