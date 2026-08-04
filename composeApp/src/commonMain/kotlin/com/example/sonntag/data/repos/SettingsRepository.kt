package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.sync.SyncStamp
import com.example.sonntag.data.sqldelight.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SettingsRepository(
    private val database: SonntagDatabase,
    private val stamp: SyncStamp,
) {
    fun getSettings(): Flow<Settings?> {
        return flowOf(database.schemaQueries.getSettings().executeAsOneOrNull())
    }

    fun insertOrUpdate(nome: String, endereco: String?, telefone: String?) {
        // O uuid da linha singleton se mantem entre edicoes: e a mesma congregacao.
        val uuid = getSettingsOnce()?.uuid ?: stamp.newRowUuid()
        database.schemaQueries.insertSettings(nome, endereco, telefone, uuid, stamp.now(), stamp.deviceId)
    }

    fun delete() {
        database.schemaQueries.deleteSettings(stamp.now(), stamp.deviceId)
    }

    fun getSettingsOnce(): Settings? {
        return database.schemaQueries.getSettings().executeAsOneOrNull()
    }
}



