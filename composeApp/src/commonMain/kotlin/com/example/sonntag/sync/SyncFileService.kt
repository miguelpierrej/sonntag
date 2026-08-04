package com.example.sonntag.sync

/** Escolha e leitura/escrita do arquivo de pacote, com os dialogos do sistema. */
interface SyncFileService {
    /** Devolve o caminho salvo, ou null se o usuario cancelar. */
    suspend fun savePackage(defaultName: String, dialogTitle: String, filterLabel: String, bytes: ByteArray): String?

    /** Devolve o conteudo escolhido, ou null se o usuario cancelar. */
    suspend fun openPackage(dialogTitle: String, filterLabel: String): ByteArray?
}

expect fun createSyncFileService(): SyncFileService
