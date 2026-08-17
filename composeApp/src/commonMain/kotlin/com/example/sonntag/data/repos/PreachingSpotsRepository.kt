package com.example.sonntag.data.repos

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.sonntag.data.sqldelight.Preaching_spots
import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.sync.SyncStamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/** Para que serve o ponto: carrinho, saida de pregacao, ou os dois. */
enum class SpotKind {
    CARRITO,
    PREDICACION,
    AMBOS;

    /** O ponto entra na lista de [tipo] quando serve a ele ou serve aos dois. */
    fun serve(tipo: PreachingKind): Boolean = this == AMBOS || name == tipo.name
}

/** Os dois programas de pregacao, que dividem a mesma tela e o mesmo calendario. */
enum class PreachingKind { CARRITO, PREDICACION }

class PreachingSpotsRepository(
    private val database: SonntagDatabase,
    private val stamp: SyncStamp,
) {
    fun getAll(): Flow<List<Preaching_spots>> =
        database.schemaQueries.getAllPreachingSpots().asFlow().mapToList(Dispatchers.Default)

    fun getAllOnce(): List<Preaching_spots> =
        database.schemaQueries.getAllPreachingSpots().executeAsList()

    fun insert(nome: String, endereco: String?, tipo: SpotKind) {
        database.schemaQueries.insertPreachingSpot(
            nome, endereco, tipo.name, stamp.newRowUuid(), stamp.now(), stamp.deviceId,
        )
    }

    fun update(id: Long, nome: String, endereco: String?, tipo: SpotKind) {
        database.schemaQueries.updatePreachingSpot(
            nome, endereco, tipo.name, stamp.now(), stamp.deviceId, id,
        )
    }

    fun delete(id: Long) {
        database.schemaQueries.deletePreachingSpot(stamp.now(), stamp.deviceId, id)
    }
}
