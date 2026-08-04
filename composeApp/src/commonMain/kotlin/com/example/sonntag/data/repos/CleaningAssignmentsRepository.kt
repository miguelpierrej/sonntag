package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.sync.SyncStamp
import com.example.sonntag.data.sqldelight.Cleaning_assignments
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class CleaningAssignmentsRepository(
    private val database: SonntagDatabase,
    private val stamp: SyncStamp,
) {
    fun getAll(): Flow<List<Cleaning_assignments>> {
        return flowOf(database.schemaQueries.getAllCleaningAssignments().executeAsList())
    }

    fun getByWeek(semanaIso: Long, ano: Long): Flow<Cleaning_assignments?> {
        return flowOf(database.schemaQueries.getCleaningAssignmentByWeek(semanaIso, ano).executeAsOneOrNull())
    }

    fun getByYear(ano: Long): Flow<List<Cleaning_assignments>> {
        return flowOf(database.schemaQueries.getCleaningAssignmentsByYear(ano).executeAsList())
    }

    fun insert(semanaIso: Long, ano: Long, groupId: Long) {
        database.schemaQueries.insertCleaningAssignment(
            semanaIso, ano, groupId, stamp.newRowUuid(), stamp.now(), stamp.deviceId,
        )
    }

    fun update(semanaIso: Long, ano: Long, groupId: Long) {
        database.schemaQueries.updateCleaningAssignment(groupId, stamp.now(), stamp.deviceId, semanaIso, ano)
    }

    fun deleteByWeek(semanaIso: Long, ano: Long) {
        database.schemaQueries.deleteCleaningAssignmentByWeek(stamp.now(), stamp.deviceId, semanaIso, ano)
    }

    fun getAllOnce(): List<Cleaning_assignments> {
        return database.schemaQueries.getAllCleaningAssignments().executeAsList()
    }

    fun getByWeekOnce(semanaIso: Long, ano: Long): Cleaning_assignments? {
        return database.schemaQueries.getCleaningAssignmentByWeek(semanaIso, ano).executeAsOneOrNull()
    }

    fun upsertByWeek(semanaIso: Long, ano: Long, groupId: Long?) {
        if (groupId == null) {
            deleteByWeek(semanaIso, ano)
            return
        }

        // Inclui excluidos: o UNIQUE(semana_iso, ano) impede inserir por cima de uma
        // linha apagada — atualizar a revive.
        val current = database.schemaQueries.getCleaningAssignmentByWeekAny(semanaIso, ano)
            .executeAsOneOrNull()
        if (current == null) {
            insert(semanaIso, ano, groupId)
        } else {
            update(semanaIso, ano, groupId)
        }
    }
}


