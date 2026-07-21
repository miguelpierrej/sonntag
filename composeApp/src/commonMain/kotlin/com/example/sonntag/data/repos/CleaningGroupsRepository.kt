package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.data.sqldelight.Cleaning_groups
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class CleaningGroupsRepository(private val database: SonntagDatabase) {
    fun getAll(): Flow<List<Cleaning_groups>> {
        return database.schemaQueries
            .getAllCleaningGroups()
            .asFlow()
            .mapToList(Dispatchers.Default)
    }

    fun getById(id: Long): Flow<Cleaning_groups?> {
        return database.schemaQueries
            .getCleaningGroupById(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
    }

    fun insert(nome: String) {
        database.schemaQueries.insertCleaningGroup(nome)
    }

    fun update(id: Long, nome: String) {
        database.schemaQueries.updateCleaningGroup(nome, id)
    }

    fun delete(id: Long) {
        database.schemaQueries.deleteCleaningGroup(id)
    }

    fun getAllOnce(): List<Cleaning_groups> {
        return database.schemaQueries.getAllCleaningGroups().executeAsList()
    }
}


