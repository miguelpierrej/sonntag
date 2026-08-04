package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.sync.SyncStamp
import com.example.sonntag.data.sqldelight.Members
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class MembersRepository(
    private val database: SonntagDatabase,
    private val stamp: SyncStamp,
) {
    fun getAll(): Flow<List<Members>> {
        return database.schemaQueries
            .getAllMembers()
            .asFlow()
            .mapToList(Dispatchers.Default)
    }

    fun getById(id: Long): Flow<Members?> {
        return database.schemaQueries
            .getMemberById(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
    }

    fun insert(nome: String, sobrenome: String) {
        database.schemaQueries.insertMember(nome, sobrenome, stamp.newRowUuid(), stamp.now(), stamp.deviceId)
    }

    fun update(id: Long, nome: String, sobrenome: String) {
        database.schemaQueries.updateMember(nome, sobrenome, stamp.now(), stamp.deviceId, id)
    }

    fun delete(id: Long) {
        database.schemaQueries.deleteMember(stamp.now(), stamp.deviceId, id)
    }

    fun getAllOnce(): List<Members> {
        return database.schemaQueries.getAllMembers().executeAsList()
    }
}


