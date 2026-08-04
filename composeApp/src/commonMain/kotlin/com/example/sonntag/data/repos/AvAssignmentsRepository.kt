package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.Av_assignments
import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.sync.SyncStamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class AvAssignmentsRepository(
    private val database: SonntagDatabase,
    private val stamp: SyncStamp,
) {
    fun getAll(): Flow<List<Av_assignments>> {
        return flowOf(database.schemaQueries.getAllAvAssignments().executeAsList())
    }

    fun getAllOnce(): List<Av_assignments> {
        return database.schemaQueries.getAllAvAssignments().executeAsList()
    }

    fun getByMeetingIdOnce(meetingId: Long): Av_assignments? {
        return database.schemaQueries.getAvAssignmentByMeetingId(meetingId).executeAsOneOrNull()
    }

    fun delete(meetingId: Long) {
        database.schemaQueries.deleteAvAssignment(stamp.now(), stamp.deviceId, meetingId)
    }

    fun upsert(
        meetingId: Long,
        audioId: Long?,
        videoId: Long?,
        plataforma1Id: Long?,
        plataforma2Id: Long?,
        microfone1Id: Long?,
        microfone2Id: Long?,
        acomodador1Id: Long?,
        acomodador2Id: Long?,
    ) {
        // Inclui excluidos: o UNIQUE(meeting_id) impede inserir por cima de uma
        // linha apagada — atualizar a revive.
        val current = database.schemaQueries.getAvAssignmentByMeetingIdAny(meetingId).executeAsOneOrNull()
        if (current == null) {
            database.schemaQueries.insertAvAssignment(
                meetingId,
                audioId, videoId,
                plataforma1Id, plataforma2Id,
                microfone1Id, microfone2Id,
                acomodador1Id, acomodador2Id,
                stamp.newRowUuid(), stamp.now(), stamp.deviceId,
            )
        } else {
            database.schemaQueries.updateAvAssignment(
                audioId, videoId,
                plataforma1Id, plataforma2Id,
                microfone1Id, microfone2Id,
                acomodador1Id, acomodador2Id,
                stamp.now(), stamp.deviceId,
                meetingId,
            )
        }
    }
}
