package com.example.sonntag.cloud

import com.example.sonntag.sync.IV_SIZE
import com.example.sonntag.sync.SALT_SIZE
import com.example.sonntag.sync.SyncCrypto
import com.example.sonntag.sync.base64Decode
import com.example.sonntag.sync.base64Encode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

const val CONNECTION_EXTENSION = "sonntagnuvem"

private const val TIPO = "sonntag-nuvem"
private const val VERSAO = 1

/**
 * Arquivo com os dados de conexao da nuvem, para levar a outro aparelho sem digitar
 * a URL. Vai inteiro cifrado com a senha da congregacao: quem acha o arquivo sem a
 * senha nao ve nem o servidor; quem tem a senha precisaria dela para conectar mesmo.
 */
object CloudConnectionFile {

    fun encode(config: CloudConfig, passphrase: String, crypto: SyncCrypto): ByteArray {
        val plain = buildJsonObject {
            put("servidor", config.host)
            put("porta", config.port)
            put("banco", config.database)
            put("usuario", config.user)
            put("senha", config.password)
        }.toString().encodeToByteArray()
        val salt = crypto.randomBytes(SALT_SIZE)
        val iv = crypto.randomBytes(IV_SIZE)
        return buildJsonObject {
            put("tipo", TIPO)
            put("versao", VERSAO)
            put("sal", base64Encode(salt))
            put("iv", base64Encode(iv))
            put("dados", base64Encode(crypto.encrypt(plain, passphrase, salt, iv)))
        }.toString().encodeToByteArray()
    }

    fun isConnectionFile(bytes: ByteArray): Boolean = envelope(bytes) != null

    /**
     * Devolve a conexao, ou null se a senha nao abre o arquivo. Lanca
     * [IllegalArgumentException] quando o arquivo nao e de conexao.
     */
    fun decode(bytes: ByteArray, passphrase: String, crypto: SyncCrypto): CloudConfig? {
        val env = envelope(bytes) ?: throw IllegalArgumentException("not a connection file")
        val plain = crypto.decrypt(
            base64Decode(env.getValue("dados")),
            passphrase,
            base64Decode(env.getValue("sal")),
            base64Decode(env.getValue("iv")),
        ) ?: return null
        val obj = Json.parseToJsonElement(plain.decodeToString()).jsonObject
        fun texto(chave: String) = obj[chave]?.jsonPrimitive?.contentOrNull.orEmpty()
        return CloudConfig(
            host = texto("servidor"),
            port = obj["porta"]?.jsonPrimitive?.int ?: CloudConfig.DEFAULT_PORT,
            database = texto("banco").ifEmpty { CloudConfig.DEFAULT_DATABASE },
            user = texto("usuario"),
            password = texto("senha"),
        )
    }

    /** Null quando o arquivo nao e de conexao (ou e de uma versao mais nova). */
    private fun envelope(bytes: ByteArray): Map<String, String>? = runCatching {
        val obj = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        if (obj["tipo"]?.jsonPrimitive?.contentOrNull != TIPO) return null
        if ((obj["versao"]?.jsonPrimitive?.intOrNull ?: return null) > VERSAO) return null
        listOf("sal", "iv", "dados").associateWith { obj.getValue(it).jsonPrimitive.content }
    }.getOrNull()
}
