package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.sync.SyncStamp
import com.example.sonntag.data.sqldelight.Meetings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class MeetingsRepository(
    private val database: SonntagDatabase,
    private val stamp: SyncStamp,
) {
    fun getAll(): Flow<List<Meetings>> {
        return flowOf(database.schemaQueries.getAllMeetings().executeAsList())
    }

    fun getAllOnce(): List<Meetings> {
        return database.schemaQueries.getAllMeetings().executeAsList()
    }

    fun getById(id: Long): Flow<Meetings?> {
        return flowOf(database.schemaQueries.getMeetingById(id).executeAsOneOrNull())
    }

    fun getByIdOnce(id: Long): Meetings? {
        return database.schemaQueries.getMeetingById(id).executeAsOneOrNull()
    }

    fun getByType(tipo: String): Flow<List<Meetings>> {
        return flowOf(database.schemaQueries.getMeetingsByType(tipo).executeAsList())
    }

    fun getByDateRange(dataStart: String, dataEnd: String): Flow<List<Meetings>> {
        return flowOf(database.schemaQueries.getMeetingsByDateRange(dataStart, dataEnd).executeAsList())
    }

    fun getByDateRangeOnce(dataStart: String, dataEnd: String): List<Meetings> {
        return database.schemaQueries.getMeetingsByDateRange(dataStart, dataEnd).executeAsList()
    }

    fun getByTypeOnce(tipo: String): List<Meetings> {
        return database.schemaQueries.getMeetingsByType(tipo).executeAsList()
    }

    fun insert(data: String, hora: String, tipo: String): Long {
        database.schemaQueries.insertMeeting(data, hora, tipo, stamp.newRowUuid(), stamp.now(), stamp.deviceId)
        return database.schemaQueries.getAllMeetings().executeAsList().last().id
    }

    fun update(id: Long, data: String, hora: String, tipo: String) {
        database.schemaQueries.updateMeeting(data, hora, tipo, stamp.now(), stamp.deviceId, id)
    }

    fun delete(id: Long) {
        database.schemaQueries.deleteMeeting(stamp.now(), stamp.deviceId, id)
    }

    fun getFutureMeetings(fromDateExclusive: String): List<Meetings> {
        return database.schemaQueries.getFutureMeetings(fromDateExclusive).executeAsList()
    }

    fun getFutureMeetingsWithProgram(fromDateExclusive: String): List<Meetings> {
        return database.schemaQueries.getFutureMeetingsWithProgram(fromDateExclusive).executeAsList()
    }

    fun deleteFutureMeetingsWithoutProgram(fromDateExclusive: String) {
        database.schemaQueries.deleteFutureMeetingsWithoutProgram(stamp.now(), stamp.deviceId, fromDateExclusive)
    }
}


