package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.data.sqldelight.Members
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class MembersRepository(private val database: SonntagDatabase) {
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
        database.schemaQueries.insertMember(nome, sobrenome)
    }

    fun update(id: Long, nome: String, sobrenome: String) {
        database.schemaQueries.updateMember(nome, sobrenome, id)
    }

    fun delete(id: Long) {
        database.schemaQueries.deleteMember(id)
    }

    fun getAllOnce(): List<Members> {
        return database.schemaQueries.getAllMembers().executeAsList()
    }
}


