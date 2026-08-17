package com.example.sonntag.data.repos

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.sonntag.data.sqldelight.Preaching_groups
import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.sync.SyncStamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/** Grupos de pregacao: nome, quem dirige e de onde o grupo sai. */
class PreachingGroupsRepository(
    private val database: SonntagDatabase,
    private val stamp: SyncStamp,
) {
    fun getAll(): Flow<List<Preaching_groups>> =
        database.schemaQueries.getAllPreachingGroups().asFlow().mapToList(Dispatchers.Default)

    fun getAllOnce(): List<Preaching_groups> =
        database.schemaQueries.getAllPreachingGroups().executeAsList()

    fun insert(nome: String, dirigenteId: Long?, auxiliarId: Long?, spotId: Long?, ordem: Long) {
        database.schemaQueries.insertPreachingGroup(
            nome, dirigenteId, auxiliarId, spotId, ordem,
            stamp.newRowUuid(), stamp.now(), stamp.deviceId,
        )
    }

    fun update(id: Long, nome: String, dirigenteId: Long?, auxiliarId: Long?, spotId: Long?, ordem: Long) {
        database.schemaQueries.updatePreachingGroup(
            nome, dirigenteId, auxiliarId, spotId, ordem,
            stamp.now(), stamp.deviceId, id,
        )
    }

    fun delete(id: Long) {
        database.schemaQueries.deletePreachingGroup(stamp.now(), stamp.deviceId, id)
    }

    /** Proxima posicao livre na lista, para o grupo novo entrar no fim. */
    fun nextOrdem(): Long = (getAllOnce().maxOfOrNull { it.ordem } ?: -1L) + 1L
}
