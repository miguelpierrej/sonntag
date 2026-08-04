package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.domain.models.TalkOutline

class TalkOutlinesRepository(private val database: SonntagDatabase) {

    fun getAllOnce(): List<TalkOutline> =
        database.schemaQueries.getAllTalkOutlines().executeAsList()
            .map { TalkOutline(it.numero.toInt(), it.titulo) }

    /** Substitui a lista inteira — cada importacao do S-34 vale pelo arquivo todo. */
    fun replaceAll(outlines: List<TalkOutline>) {
        database.transaction {
            database.schemaQueries.deleteAllTalkOutlines()
            outlines.forEach {
                database.schemaQueries.insertTalkOutline(it.numero.toLong(), it.titulo)
            }
        }
    }
}
