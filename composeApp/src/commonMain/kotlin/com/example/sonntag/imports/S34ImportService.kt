package com.example.sonntag.imports

import com.example.sonntag.domain.models.TalkOutline

/** Le a lista de bosquejos de discursos publicos de um arquivo S-34 (.jwpub). */
interface S34ImportService {
    /**
     * Abre um seletor de arquivo (com titulo/filtro localizados) e devolve os
     * bosquejos encontrados. Devolve null se o usuario cancelar e lista vazia se
     * o arquivo nao for um S-34 valido.
     */
    suspend fun pickTalkOutlines(dialogTitle: String, filterLabel: String): List<TalkOutline>?
}

expect fun createS34ImportService(): S34ImportService
