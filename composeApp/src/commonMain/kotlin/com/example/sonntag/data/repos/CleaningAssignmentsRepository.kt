package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.data.sqldelight.Cleaning_assignments
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class CleaningAssignmentsRepository(private val database: SonntagDatabase) {
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
        database.schemaQueries.insertCleaningAssignment(semanaIso, ano, groupId)
    }

    fun update(semanaIso: Long, ano: Long, groupId: Long) {
        database.schemaQueries.updateCleaningAssignment(groupId, semanaIso, ano)
    }

    fun deleteByWeek(semanaIso: Long, ano: Long) {
        database.schemaQueries.deleteCleaningAssignmentByWeek(semanaIso, ano)
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

        val current = getByWeekOnce(semanaIso, ano)
        if (current == null) {
            insert(semanaIso, ano, groupId)
        } else {
            update(semanaIso, ano, groupId)
        }
    }
}


