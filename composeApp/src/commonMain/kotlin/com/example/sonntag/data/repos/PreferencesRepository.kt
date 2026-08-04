package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.SonntagDatabase

/** Armazenamento simples de preferencias chave/valor (ex.: idioma). */
class PreferencesRepository(private val database: SonntagDatabase) {
    fun get(key: String): String? =
        database.schemaQueries.getPref(key).executeAsOneOrNull()

    fun set(key: String, value: String) {
        database.schemaQueries.setPref(key, value)
    }
}
