package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.data.sqldelight.Weekend_programs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class WeekendProgramsRepository(private val database: SonntagDatabase) {
    fun getAll(): Flow<List<Weekend_programs>> {
        return flowOf(database.schemaQueries.getAllWeekendPrograms().executeAsList())
    }

    fun getByMeetingId(meetingId: Long): Flow<Weekend_programs?> {
        return flowOf(database.schemaQueries.getWeekendProgramByMeetingId(meetingId).executeAsOneOrNull())
    }

    fun getByMeetingIdOnce(meetingId: Long): Weekend_programs? {
        return database.schemaQueries.getWeekendProgramByMeetingId(meetingId).executeAsOneOrNull()
    }

    fun insert(
        meetingId: Long,
        tituloDiscurso: String?,
        oradorId: Long?,
        oradorNome: String?,
        presidenteId: Long?,
        dirigenteId: Long?,
        leitorId: Long?,
    ) {
        database.schemaQueries.insertWeekendProgram(
            meetingId, tituloDiscurso, oradorId, oradorNome, presidenteId, dirigenteId, leitorId
        )
    }

    fun update(
        meetingId: Long,
        tituloDiscurso: String?,
        oradorId: Long?,
        oradorNome: String?,
        presidenteId: Long?,
        dirigenteId: Long?,
        leitorId: Long?,
    ) {
        database.schemaQueries.updateWeekendProgram(
            tituloDiscurso, oradorId, oradorNome, presidenteId, dirigenteId, leitorId, meetingId
        )
    }

    fun delete(meetingId: Long) {
        database.schemaQueries.deleteWeekendProgram(meetingId)
    }

    fun upsert(
        meetingId: Long,
        tituloDiscurso: String?,
        oradorId: Long?,
        oradorNome: String?,
        presidenteId: Long?,
        dirigenteId: Long?,
        leitorId: Long?,
    ) {
        val current = getByMeetingIdOnce(meetingId)
        if (current == null) {
            insert(meetingId, tituloDiscurso, oradorId, oradorNome, presidenteId, dirigenteId, leitorId)
        } else {
            update(meetingId, tituloDiscurso, oradorId, oradorNome, presidenteId, dirigenteId, leitorId)
        }
    }
}


