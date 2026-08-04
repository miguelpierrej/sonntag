package com.example.sonntag.sync

import com.example.sonntag.data.repos.PreferencesRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Identificador desta instalacao, guardado em app_prefs (nunca sincronizado). */
const val PREF_DEVICE_ID = "device_id"

/** Gera um identificador global unico para uma linha. */
expect fun newUuid(): String

/**
 * Carimbo aplicado a toda escrita: quem alterou (este dispositivo) e quando.
 *
 * Sem isso nao ha como decidir, na fusao, qual das versoes de uma linha e a mais
 * recente — nem distinguir uma linha apagada de uma que nunca chegou.
 */
class SyncStamp(private val prefs: PreferencesRepository) {

    /** Estavel enquanto o banco existir; criado na primeira escrita. */
    val deviceId: String by lazy {
        prefs.get(PREF_DEVICE_ID) ?: newUuid().also { prefs.set(PREF_DEVICE_ID, it) }
    }

    /**
     * Instante atual em UTC, com precisao de segundos: "2026-08-04T12:34:56Z".
     *
     * O formato e fixo de proposito. Com fracao de segundo opcional, a comparacao
     * textual erra — "…:56.7Z" fica menor que "…:56Z" — e a fusao escolheria a
     * versao errada. Empate no mesmo segundo se desempata pelo dispositivo.
     */
    fun now(): String = Instant.fromEpochSeconds(Clock.System.now().epochSeconds).toString()

    /** Identidade global de uma linha nova. */
    fun newRowUuid(): String = newUuid()
}
