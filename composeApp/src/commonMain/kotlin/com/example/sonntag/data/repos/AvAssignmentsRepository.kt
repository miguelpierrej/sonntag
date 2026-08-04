package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.Av_assignments
import com.example.sonntag.data.sqldelight.SonntagDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class AvAssignmentsRepository(private val database: SonntagDatabase) {
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
        database.schemaQueries.deleteAvAssignment(meetingId)
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
        val current = getByMeetingIdOnce(meetingId)
        if (current == null) {
            database.schemaQueries.insertAvAssignment(
                meetingId,
                audioId, videoId,
                plataforma1Id, plataforma2Id,
                microfone1Id, microfone2Id,
                acomodador1Id, acomodador2Id,
            )
        } else {
            database.schemaQueries.updateAvAssignment(
                audioId, videoId,
                plataforma1Id, plataforma2Id,
                microfone1Id, microfone2Id,
                acomodador1Id, acomodador2Id,
                meetingId,
            )
        }
    }
}
