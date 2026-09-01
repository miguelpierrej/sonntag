package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.Preaching_group_members
import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.sync.SyncStamp

/**
 * Quem compoe cada grupo de pregacao.
 *
 * Uma pessoa pertence a um grupo so: [assign] tira o vinculo anterior antes de criar
 * o novo, para o mesmo nome nao sair em duas colunas da folha.
 */
class PreachingGroupMembersRepository(
    private val database: SonntagDatabase,
    private val stamp: SyncStamp,
) {
    fun getAllOnce(): List<Preaching_group_members> =
        database.schemaQueries.getAllPreachingGroupMembers().executeAsList()

    /** id do membro -> id do grupo a que ele pertence hoje. */
    fun groupByMemberOnce(): Map<Long, Long> =
        getAllOnce().associate { it.member_id to it.preaching_group_id }

    /** Poe o membro no grupo, saindo de qualquer outro em que estivesse. */
    fun assign(groupId: Long, memberId: Long) {
        database.schemaQueries.getPreachingGroupMembershipsOfMember(memberId)
            .executeAsList()
            .filter { it.preaching_group_id != groupId }
            .forEach { database.schemaQueries.deletePreachingGroupMember(stamp.now(), stamp.deviceId, it.id) }

        // A linha excluida e revivida em vez de recriada: o uuid e a identidade do
        // vinculo entre aparelhos, e trocar de uuid faria o outro lado ver duas.
        //
        // Lista, e nao linha unica: nada no banco impede duas linhas com o mesmo par
        // (a chave natural da sincronizacao e que as junta), e uma consulta que
        // exigisse uma so quebraria a tela se um dia chegassem duas.
        val existente = paresDe(groupId, memberId).firstOrNull()
        if (existente == null) {
            database.schemaQueries.insertPreachingGroupMember(
                groupId, memberId, stamp.newRowUuid(), stamp.now(), stamp.deviceId,
            )
        } else if (existente.deleted != 0L) {
            database.schemaQueries.revivePreachingGroupMember(stamp.now(), stamp.deviceId, existente.id)
        }
    }

    fun remove(groupId: Long, memberId: Long) {
        paresDe(groupId, memberId)
            .filter { it.deleted == 0L }
            .forEach { database.schemaQueries.deletePreachingGroupMember(stamp.now(), stamp.deviceId, it.id) }
    }

    /** O vinculo deste par, vivo primeiro: e ele que interessa reviver ou apagar. */
    private fun paresDe(groupId: Long, memberId: Long): List<Preaching_group_members> =
        database.schemaQueries.getPreachingGroupMemberAny(groupId, memberId)
            .executeAsList()
            .sortedBy { it.deleted }
}
