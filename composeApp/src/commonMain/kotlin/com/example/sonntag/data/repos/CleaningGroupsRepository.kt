package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.sync.SyncStamp
import com.example.sonntag.data.sqldelight.Cleaning_groups
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class CleaningGroupsRepository(
    private val database: SonntagDatabase,
    private val stamp: SyncStamp,
) {
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
        // UNIQUE(nome): se um grupo com esse nome foi excluido, reviva-o em vez de
        // inserir outro — a insercao esbarraria na constraint.
        val existing = database.schemaQueries.getCleaningGroupByNameAny(nome).executeAsOneOrNull()
        if (existing != null) {
            database.schemaQueries.updateCleaningGroup(nome, stamp.now(), stamp.deviceId, existing.id)
        } else {
            database.schemaQueries.insertCleaningGroup(nome, stamp.newRowUuid(), stamp.now(), stamp.deviceId)
        }
    }

    fun update(id: Long, nome: String) {
        database.schemaQueries.updateCleaningGroup(nome, stamp.now(), stamp.deviceId, id)
    }

    fun delete(id: Long) {
        database.schemaQueries.deleteCleaningGroup(stamp.now(), stamp.deviceId, id)
    }

    fun getAllOnce(): List<Cleaning_groups> {
        return database.schemaQueries.getAllCleaningGroups().executeAsList()
    }
}


