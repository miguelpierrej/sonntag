package com.example.sonntag.data.repos

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.sonntag.data.sqldelight.Events
import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.sync.SyncStamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class EventsRepository(
    private val database: SonntagDatabase,
    private val stamp: SyncStamp,
) {
    fun getAll(): Flow<List<Events>> {
        return database.schemaQueries
            .getAllEvents()
            .asFlow()
            .mapToList(Dispatchers.Default)
    }

    fun getAllOnce(): List<Events> {
        return database.schemaQueries.getAllEvents().executeAsList()
    }

    fun getBetweenOnce(inicio: String, fim: String): List<Events> {
        return database.schemaQueries.getEventsBetween(inicio, fim).executeAsList()
    }

    fun insert(nome: String, data: String, tipo: String) {
        database.schemaQueries.insertEvent(
            nome, data, tipo, stamp.newRowUuid(), stamp.now(), stamp.deviceId,
        )
    }

    fun update(id: Long, nome: String, data: String, tipo: String) {
        database.schemaQueries.updateEvent(nome, data, tipo, stamp.now(), stamp.deviceId, id)
    }

    fun delete(id: Long) {
        database.schemaQueries.deleteEvent(stamp.now(), stamp.deviceId, id)
    }
}
