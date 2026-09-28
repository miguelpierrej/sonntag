package com.example.sonntag.cloud

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.sonntag.sync.base64Decode
import com.example.sonntag.sync.base64Encode
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ALIAS = "sonntag-nuvem"
private const val IV_BYTES = 12

/**
 * No Android a chave fica no Keystore do sistema: nao sai do aparelho e nem o proprio
 * app consegue le-la, so pedir que cifre e decifre.
 */
private class SecretBoxAndroid : SecretBox {

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun seal(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        // O Keystore escolhe o IV; ele vai junto na frente do texto cifrado.
        return base64Encode(cipher.iv + cipher.doFinal(plain.encodeToByteArray()))
    }

    override fun open(sealed: String): String? = runCatching {
        val bytes = base64Decode(sealed)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, IV_BYTES)))
        }
        cipher.doFinal(bytes.copyOfRange(IV_BYTES, bytes.size)).decodeToString()
    }.getOrNull()
}

actual fun createSecretBox(): SecretBox = SecretBoxAndroid()
