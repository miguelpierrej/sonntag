package com.example.sonntag.cloud

import com.example.sonntag.sync.base64Decode
import com.example.sonntag.sync.base64Encode
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val IV_BYTES = 12

/**
 * No desktop a chave fica num arquivo proprio ao lado do banco, legivel so pelo
 * usuario. Nao e um cofre do sistema: protege contra quem copia o banco (um backup),
 * nao contra quem tem acesso a conta do usuario no computador.
 */
private class SecretBoxJvm(private val file: File) : SecretBox {

    private val random = SecureRandom()

    private val key: ByteArray by lazy {
        if (file.exists() && file.length() == 32L) return@lazy file.readBytes()
        val novo = ByteArray(32).also { random.nextBytes(it) }
        file.parentFile?.mkdirs()
        file.writeBytes(novo)
        runCatching {
            Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"))
        }
        novo
    }

    override fun seal(plain: String): String {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        }
        return base64Encode(iv + cipher.doFinal(plain.encodeToByteArray()))
    }

    override fun open(sealed: String): String? = runCatching {
        val bytes = base64Decode(sealed)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, bytes.copyOfRange(0, IV_BYTES)))
        }
        cipher.doFinal(bytes.copyOfRange(IV_BYTES, bytes.size)).decodeToString()
    }.getOrNull()
}

/** Mesma pasta do banco (ver DatabaseDriver do desktop). */
actual fun createSecretBox(): SecretBox =
    SecretBoxJvm(File(File(System.getProperty("user.home"), ".salao-app"), "nuvem.key"))

/** Para as ferramentas de teste, que simulam varios aparelhos na mesma maquina. */
fun createSecretBox(file: File): SecretBox = SecretBoxJvm(file)
