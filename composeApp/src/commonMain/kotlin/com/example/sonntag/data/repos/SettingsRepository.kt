package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.data.sqldelight.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SettingsRepository(private val database: SonntagDatabase) {
    fun getSettings(): Flow<Settings?> {
        return flowOf(database.schemaQueries.getSettings().executeAsOneOrNull())
    }

    fun insertOrUpdate(nome: String, endereco: String?, telefone: String?) {
        database.schemaQueries.insertSettings(nome, endereco, telefone)
    }

    fun delete() {
        database.schemaQueries.deleteSettings()
    }

    fun getSettingsOnce(): Settings? {
        return database.schemaQueries.getSettings().executeAsOneOrNull()
    }
}



