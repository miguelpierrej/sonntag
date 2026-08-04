package com.example.sonntag.sync

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private const val PBKDF2_ITERATIONS = 210_000
private const val KEY_BITS = 256
private const val GCM_TAG_BITS = 128

class SyncCryptoJvm : SyncCrypto {

    private val random = SecureRandom()

    override fun encrypt(plain: ByteArray, passphrase: String, salt: ByteArray, iv: ByteArray): ByteArray =
        cipher(Cipher.ENCRYPT_MODE, passphrase, salt, iv).doFinal(plain)

    override fun decrypt(cipher: ByteArray, passphrase: String, salt: ByteArray, iv: ByteArray): ByteArray? =
        try {
            cipher(Cipher.DECRYPT_MODE, passphrase, salt, iv).doFinal(cipher)
        } catch (e: AEADBadTagException) {
            // Senha errada e arquivo corrompido chegam pelo mesmo caminho: a tag GCM
            // so fecha quando a chave e o conteudo estao corretos.
            null
        }

    override fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    private fun cipher(mode: Int, passphrase: String, salt: ByteArray, iv: ByteArray): Cipher {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key.encoded, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
    }
}

actual fun createSyncCrypto(): SyncCrypto = SyncCryptoJvm()
