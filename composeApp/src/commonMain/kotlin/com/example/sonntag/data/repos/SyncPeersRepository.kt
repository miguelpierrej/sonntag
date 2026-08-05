package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.data.sqldelight.Sync_peers

/** Ate quando ja trocamos dados com cada aparelho — a base do envio incremental. */
class SyncPeersRepository(private val database: SonntagDatabase) {

    fun getAllOnce(): List<Sync_peers> = database.schemaQueries.getAllPeers().executeAsList()

    /** Instante da ultima troca bem-sucedida com [deviceId], ou null se nunca houve. */
    fun lastSyncAt(deviceId: String): String? =
        database.schemaQueries.getPeer(deviceId).executeAsOneOrNull()?.last_sync_at

    fun remember(deviceId: String, nome: String?, lastSyncAt: String) {
        database.schemaQueries.upsertPeer(deviceId, nome, lastSyncAt)
    }
}
