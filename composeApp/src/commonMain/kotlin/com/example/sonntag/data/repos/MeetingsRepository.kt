package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.data.sqldelight.Meetings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class MeetingsRepository(private val database: SonntagDatabase) {
    fun getAll(): Flow<List<Meetings>> {
        return flowOf(database.schemaQueries.getAllMeetings().executeAsList())
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
        database.schemaQueries.insertMeeting(data, hora, tipo)
        return database.schemaQueries.getAllMeetings().executeAsList().last().id
    }

    fun update(id: Long, data: String, hora: String, tipo: String) {
        database.schemaQueries.updateMeeting(data, hora, tipo, id)
    }

    fun delete(id: Long) {
        database.schemaQueries.deleteMeeting(id)
    }

    fun getFutureMeetings(fromDateExclusive: String): List<Meetings> {
        return database.schemaQueries.getFutureMeetings(fromDateExclusive).executeAsList()
    }

    fun getFutureMeetingsWithProgram(fromDateExclusive: String): List<Meetings> {
        return database.schemaQueries.getFutureMeetingsWithProgram(fromDateExclusive).executeAsList()
    }

    fun deleteFutureMeetingsWithoutProgram(fromDateExclusive: String) {
        database.schemaQueries.deleteFutureMeetingsWithoutProgram(fromDateExclusive)
    }
}


