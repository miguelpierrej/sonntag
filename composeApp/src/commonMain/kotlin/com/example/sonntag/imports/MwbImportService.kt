package com.example.sonntag.imports

/** Seleciona uma apostila (mwb) em PDF e devolve seu texto puro. */
interface MwbImportService {
    /**
     * Abre um seletor de arquivo (com titulo/filtro localizados) e devolve o texto
     * extraido do PDF escolhido, ou null se o usuario cancelar.
     */
    suspend fun pickPdfText(dialogTitle: String, pdfFilterLabel: String): String?
}

expect fun createMwbImportService(): MwbImportService
