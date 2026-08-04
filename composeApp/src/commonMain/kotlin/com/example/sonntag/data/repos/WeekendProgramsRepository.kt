package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.sync.SyncStamp
import com.example.sonntag.data.sqldelight.Weekend_programs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class WeekendProgramsRepository(
    private val database: SonntagDatabase,
    private val stamp: SyncStamp,
) {
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
            meetingId, tituloDiscurso, oradorId, oradorNome, presidenteId, dirigenteId, leitorId,
            stamp.newRowUuid(), stamp.now(), stamp.deviceId
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
            tituloDiscurso, oradorId, oradorNome, presidenteId, dirigenteId, leitorId,
            stamp.now(), stamp.deviceId, meetingId
        )
    }

    fun delete(meetingId: Long) {
        database.schemaQueries.deleteWeekendProgram(stamp.now(), stamp.deviceId, meetingId)
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
        // Inclui excluidos: o UNIQUE(meeting_id) impede inserir por cima de uma
        // linha apagada — atualizar a revive.
        val current = database.schemaQueries.getWeekendProgramByMeetingIdAny(meetingId).executeAsOneOrNull()
        if (current == null) {
            insert(meetingId, tituloDiscurso, oradorId, oradorNome, presidenteId, dirigenteId, leitorId)
        } else {
            update(meetingId, tituloDiscurso, oradorId, oradorNome, presidenteId, dirigenteId, leitorId)
        }
    }
}


