package com.example.sonntag.sync

/**
 * Cifra o conteudo do pacote com AES-GCM. A chave sai sempre de uma senha via
 * derivacao lenta (PBKDF2): quando o usuario nao define uma, usamos [BUILT_IN_PASSPHRASE].
 *
 * Sem senha do usuario **nao ha protecao real** — a frase esta no codigo, que e
 * publico. Serve para o arquivo ser nosso e nao ser aberto por acidente, nao para
 * guardar segredo. A tela precisa deixar isso claro.
 */
interface SyncCrypto {
    /** Deriva a chave da senha + [salt] e devolve o texto cifrado (com a tag GCM). */
    fun encrypt(plain: ByteArray, passphrase: String, salt: ByteArray, iv: ByteArray): ByteArray

    /** Devolve null quando a senha esta errada ou o arquivo foi adulterado. */
    fun decrypt(cipher: ByteArray, passphrase: String, salt: ByteArray, iv: ByteArray): ByteArray?

    fun randomBytes(size: Int): ByteArray

    /**
     * Chave AES derivada da senha, para quem cifra muitas coisas com a mesma senha.
     *
     * A nuvem cifra linha a linha; derivar a chave a cada linha (PBKDF2 lento de
     * proposito) levaria minutos numa sincronizacao completa.
     */
    fun deriveKey(passphrase: String, salt: ByteArray): ByteArray

    fun encryptWithKey(plain: ByteArray, key: ByteArray, iv: ByteArray): ByteArray

    /** Null quando a chave nao confere ou o conteudo foi adulterado. */
    fun decryptWithKey(cipher: ByteArray, key: ByteArray, iv: ByteArray): ByteArray?
}

/** Usada quando o usuario opta por nao definir senha. */
const val BUILT_IN_PASSPHRASE = "sonntag/pacote-de-dados/v1"

const val SALT_SIZE = 16
const val IV_SIZE = 12

expect fun createSyncCrypto(): SyncCrypto
