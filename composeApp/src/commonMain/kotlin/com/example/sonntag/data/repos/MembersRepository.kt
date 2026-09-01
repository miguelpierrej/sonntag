package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.sync.SyncStamp
import com.example.sonntag.data.sqldelight.Members
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/** O que o publicador e na congregacao. Nenhuma delas e obrigatoria. */
data class Responsabilidades(
    val anciao: Boolean = false,
    val servoMinisterial: Boolean = false,
    val pioneiro: Boolean = false,
) {
    companion object {
        fun de(membro: Members): Responsabilidades = Responsabilidades(
            anciao = membro.anciao != 0L,
            servoMinisterial = membro.servo_ministerial != 0L,
            pioneiro = membro.pioneiro != 0L,
        )
    }
}

private fun Boolean.comoInteiro(): Long = if (this) 1L else 0L

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

    fun insert(nome: String, sobrenome: String, responsabilidades: Responsabilidades = Responsabilidades()) {
        database.schemaQueries.insertMember(
            nome, sobrenome,
            responsabilidades.anciao.comoInteiro(),
            responsabilidades.servoMinisterial.comoInteiro(),
            responsabilidades.pioneiro.comoInteiro(),
            stamp.newRowUuid(), stamp.now(), stamp.deviceId,
        )
    }

    fun update(id: Long, nome: String, sobrenome: String, responsabilidades: Responsabilidades = Responsabilidades()) {
        database.schemaQueries.updateMember(
            nome, sobrenome,
            responsabilidades.anciao.comoInteiro(),
            responsabilidades.servoMinisterial.comoInteiro(),
            responsabilidades.pioneiro.comoInteiro(),
            stamp.now(), stamp.deviceId, id,
        )
    }

    fun delete(id: Long) {
        database.schemaQueries.deleteMember(stamp.now(), stamp.deviceId, id)
    }

    fun getAllOnce(): List<Members> {
        return database.schemaQueries.getAllMembers().executeAsList()
    }
}


