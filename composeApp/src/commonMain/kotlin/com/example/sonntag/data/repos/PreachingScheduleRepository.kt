package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.Preaching_notes
import com.example.sonntag.data.sqldelight.Preaching_shifts
import com.example.sonntag.data.sqldelight.Preaching_templates
import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.sync.SyncStamp

/** Os quatro designados de um turno, na ordem em que saem no documento. */
data class ShiftInput(
    val horaInicio: String,
    val horaFim: String? = null,
    val spotId: Long? = null,
    val nota: String? = null,
    val designados: List<Long?> = List(4) { null },
    val ordem: Long = 0L,
) {
    fun designado(i: Int): Long? = designados.getOrNull(i)
}

/** Turnos do calendario, o padrao semanal e a observacao do rodape. */
class PreachingScheduleRepository(
    private val database: SonntagDatabase,
    private val stamp: SyncStamp,
) {
    // ─── Turnos ──────────────────────────────────────────────────────────────

    fun shiftsBetween(tipo: PreachingKind, inicio: String, fim: String): List<Preaching_shifts> =
        database.schemaQueries.getPreachingShiftsBetween(tipo.name, inicio, fim).executeAsList()

    fun insertShift(tipo: PreachingKind, data: String, input: ShiftInput) {
        database.schemaQueries.insertPreachingShift(
            tipo.name, data, input.horaInicio, input.horaFim, input.spotId, input.nota,
            input.designado(0), input.designado(1), input.designado(2), input.designado(3),
            input.ordem, stamp.newRowUuid(), stamp.now(), stamp.deviceId,
        )
    }

    fun updateShift(id: Long, input: ShiftInput) {
        database.schemaQueries.updatePreachingShift(
            input.horaInicio, input.horaFim, input.spotId, input.nota,
            input.designado(0), input.designado(1), input.designado(2), input.designado(3),
            input.ordem, stamp.now(), stamp.deviceId, id,
        )
    }

    fun deleteShift(id: Long) {
        database.schemaQueries.deletePreachingShift(stamp.now(), stamp.deviceId, id)
    }

    // ─── Padrao semanal ──────────────────────────────────────────────────────

    fun templates(tipo: PreachingKind): List<Preaching_templates> =
        database.schemaQueries.getAllPreachingTemplates(tipo.name).executeAsList()

    fun insertTemplate(
        tipo: PreachingKind,
        diaSemana: Long,
        horaInicio: String,
        horaFim: String?,
        spotId: Long?,
        ordem: Long,
    ) {
        database.schemaQueries.insertPreachingTemplate(
            tipo.name, diaSemana, horaInicio, horaFim, spotId, ordem,
            stamp.newRowUuid(), stamp.now(), stamp.deviceId,
        )
    }

    fun updateTemplate(
        id: Long,
        diaSemana: Long,
        horaInicio: String,
        horaFim: String?,
        spotId: Long?,
        ordem: Long,
    ) {
        database.schemaQueries.updatePreachingTemplate(
            diaSemana, horaInicio, horaFim, spotId, ordem, stamp.now(), stamp.deviceId, id,
        )
    }

    fun deleteTemplate(id: Long) {
        database.schemaQueries.deletePreachingTemplate(stamp.now(), stamp.deviceId, id)
    }

    // ─── Observacao do rodape ────────────────────────────────────────────────

    fun note(tipo: PreachingKind, ano: Int, mes: Int): Preaching_notes? =
        database.schemaQueries.getPreachingNote(tipo.name, ano.toLong(), mes.toLong())
            .executeAsOneOrNull()

    fun saveNote(tipo: PreachingKind, ano: Int, mes: Int, texto: String) {
        // UNIQUE(tipo, ano, mes): se a observacao daquele mes foi apagada, reviva-a —
        // inserir outra esbarraria na constraint.
        val existente = database.schemaQueries
            .getPreachingNoteAny(tipo.name, ano.toLong(), mes.toLong())
            .executeAsOneOrNull()
        if (existente != null) {
            database.schemaQueries.updatePreachingNote(texto, stamp.now(), stamp.deviceId, existente.id)
        } else {
            database.schemaQueries.insertPreachingNote(
                tipo.name, ano.toLong(), mes.toLong(), texto,
                stamp.newRowUuid(), stamp.now(), stamp.deviceId,
            )
        }
    }
}
