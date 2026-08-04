package com.example.sonntag.data.repos

import com.example.sonntag.data.sqldelight.Midweek_programs
import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.sync.SyncStamp

/**
 * Conteudo editavel de uma programacao de meio de semana (formulario S-140).
 * Os campos seguem a ordem das colunas em [SonntagDatabase] para facilitar o upsert.
 */
data class MidweekProgramInput(
    val leituraSemanal: String? = null,
    val presidenteId: Long? = null,
    val conselheiroId: Long? = null,
    val canticoInicial: String? = null,
    val oracaoInicialId: Long? = null,
    val tesourosTitulo: String? = null,
    val tesourosOradorId: Long? = null,
    val joiasId: Long? = null,
    val leituraBibliaId: Long? = null,
    val min1Titulo: String? = null,
    val min1Minutos: String? = null,
    val min1EstudanteId: Long? = null,
    val min1AjudanteId: Long? = null,
    val min2Titulo: String? = null,
    val min2Minutos: String? = null,
    val min2EstudanteId: Long? = null,
    val min2AjudanteId: Long? = null,
    val min3Titulo: String? = null,
    val min3Minutos: String? = null,
    val min3EstudanteId: Long? = null,
    val min3AjudanteId: Long? = null,
    val min4Titulo: String? = null,
    val min4Minutos: String? = null,
    val min4EstudanteId: Long? = null,
    val min4AjudanteId: Long? = null,
    val canticoMeio: String? = null,
    val vida1Titulo: String? = null,
    val vida1Minutos: String? = null,
    val vida1Id: Long? = null,
    val vida2Titulo: String? = null,
    val vida2Minutos: String? = null,
    val vida2Id: Long? = null,
    val estudoDirigenteId: Long? = null,
    val estudoLeitorId: Long? = null,
    val canticoFinal: String? = null,
    val oracaoFinalId: Long? = null,
)

class MidweekProgramsRepository(
    private val database: SonntagDatabase,
    private val stamp: SyncStamp,
) {
    fun getByMeetingIdOnce(meetingId: Long): Midweek_programs? {
        return database.schemaQueries.getMidweekProgramByMeetingId(meetingId).executeAsOneOrNull()
    }

    fun upsert(meetingId: Long, input: MidweekProgramInput) {
        // Inclui excluidos: o UNIQUE(meeting_id) impede inserir por cima de uma
        // linha apagada — atualizar a revive.
        val exists = database.schemaQueries.getMidweekProgramByMeetingIdAny(meetingId)
            .executeAsOneOrNull() != null
        with(input) {
            if (!exists) {
                database.schemaQueries.insertMidweekProgram(
                    meetingId,
                    leituraSemanal, presidenteId, conselheiroId, canticoInicial, oracaoInicialId,
                    tesourosTitulo, tesourosOradorId, joiasId, leituraBibliaId,
                    min1Titulo, min1Minutos, min1EstudanteId, min1AjudanteId,
                    min2Titulo, min2Minutos, min2EstudanteId, min2AjudanteId,
                    min3Titulo, min3Minutos, min3EstudanteId, min3AjudanteId,
                    min4Titulo, min4Minutos, min4EstudanteId, min4AjudanteId,
                    canticoMeio,
                    vida1Titulo, vida1Minutos, vida1Id,
                    vida2Titulo, vida2Minutos, vida2Id,
                    estudoDirigenteId, estudoLeitorId, canticoFinal, oracaoFinalId,
                    stamp.newRowUuid(), stamp.now(), stamp.deviceId,
                )
            } else {
                database.schemaQueries.updateMidweekProgram(
                    leituraSemanal, presidenteId, conselheiroId, canticoInicial, oracaoInicialId,
                    tesourosTitulo, tesourosOradorId, joiasId, leituraBibliaId,
                    min1Titulo, min1Minutos, min1EstudanteId, min1AjudanteId,
                    min2Titulo, min2Minutos, min2EstudanteId, min2AjudanteId,
                    min3Titulo, min3Minutos, min3EstudanteId, min3AjudanteId,
                    min4Titulo, min4Minutos, min4EstudanteId, min4AjudanteId,
                    canticoMeio,
                    vida1Titulo, vida1Minutos, vida1Id,
                    vida2Titulo, vida2Minutos, vida2Id,
                    estudoDirigenteId, estudoLeitorId, canticoFinal, oracaoFinalId,
                    stamp.now(), stamp.deviceId,
                    meetingId,
                )
            }
        }
    }

    fun delete(meetingId: Long) {
        database.schemaQueries.deleteMidweekProgram(stamp.now(), stamp.deviceId, meetingId)
    }
}
