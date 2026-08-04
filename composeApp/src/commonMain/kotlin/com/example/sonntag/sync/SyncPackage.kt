package com.example.sonntag.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Assinatura do arquivo: identifica o formato antes de tentar decifrar. */
const val PACKAGE_MAGIC = "SNTG"
const val PACKAGE_VERSION = 1
const val PACKAGE_EXTENSION = "sonntag"

/**
 * Cabecalho, em texto claro. Precisa ser legivel sem a senha para a tela de
 * importacao dizer o que ha no arquivo e se vai pedir senha.
 */
data class PackageHeader(
    val version: Int,
    val protected: Boolean,
    val exportedAt: String,
    val exportedBy: String,
    val congregacao: String?,
    val sections: List<String>,
    val salt: ByteArray,
    val iv: ByteArray,
) {
    val sectionsResolved: List<SyncSection> get() = sections.mapNotNull { SyncSection.fromId(it) }
}

/** Uma tabela dentro do pacote. */
data class PackageTable(
    val name: String,
    val columns: List<String>,
    val rows: List<RowValues>,
)

private val json = Json { prettyPrint = false }

fun encodePayload(tables: List<PackageTable>): String = buildJsonObject {
    put("tables", buildJsonArray {
        tables.forEach { table ->
            add(buildJsonObject {
                put("name", JsonPrimitive(table.name))
                put("columns", JsonArray(table.columns.map { JsonPrimitive(it) }))
                put("rows", buildJsonArray {
                    table.rows.forEach { row ->
                        add(JsonArray(table.columns.map { column ->
                            row[column]?.let { JsonPrimitive(it) } ?: JsonNull
                        }))
                    }
                })
            })
        }
    })
}.toString()

fun decodePayload(text: String): List<PackageTable> =
    json.parseToJsonElement(text).jsonObject["tables"]!!.jsonArray.map { element ->
        val table = element.jsonObject
        val columns = table["columns"]!!.jsonArray.map { it.jsonPrimitive.content }
        PackageTable(
            name = table["name"]!!.jsonPrimitive.content,
            columns = columns,
            rows = table["rows"]!!.jsonArray.map { rowElement ->
                val values = rowElement.jsonArray
                columns.mapIndexed { index, column ->
                    column to values[index].jsonPrimitive.contentOrNull
                }.toMap()
            },
        )
    }

fun encodeHeader(header: PackageHeader): String = buildJsonObject {
    put("version", JsonPrimitive(header.version))
    put("protected", JsonPrimitive(header.protected))
    put("exportedAt", JsonPrimitive(header.exportedAt))
    put("exportedBy", JsonPrimitive(header.exportedBy))
    put("congregacao", header.congregacao?.let { JsonPrimitive(it) } ?: JsonNull)
    put("sections", JsonArray(header.sections.map { JsonPrimitive(it) }))
    put("salt", JsonPrimitive(base64Encode(header.salt)))
    put("iv", JsonPrimitive(base64Encode(header.iv)))
}.toString()

fun decodeHeader(text: String): PackageHeader {
    val obj = json.parseToJsonElement(text).jsonObject
    return PackageHeader(
        version = obj["version"]!!.jsonPrimitive.int,
        protected = obj["protected"]!!.jsonPrimitive.content.toBoolean(),
        exportedAt = obj["exportedAt"]!!.jsonPrimitive.content,
        exportedBy = obj["exportedBy"]!!.jsonPrimitive.content,
        congregacao = obj["congregacao"]?.jsonPrimitive?.contentOrNull,
        sections = obj["sections"]!!.jsonArray.map { it.jsonPrimitive.content },
        salt = base64Decode(obj["salt"]!!.jsonPrimitive.content),
        iv = base64Decode(obj["iv"]!!.jsonPrimitive.content),
    )
}

expect fun base64Encode(bytes: ByteArray): String
expect fun base64Decode(text: String): ByteArray

// ─── Enquadramento: MAGIC | tamanho do cabecalho | cabecalho | conteudo cifrado ───

fun frame(header: String, cipher: ByteArray): ByteArray {
    val magic = PACKAGE_MAGIC.encodeToByteArray()
    val headerBytes = header.encodeToByteArray()
    val out = ByteArray(magic.size + 4 + headerBytes.size + cipher.size)
    magic.copyInto(out)
    writeInt(out, magic.size, headerBytes.size)
    headerBytes.copyInto(out, magic.size + 4)
    cipher.copyInto(out, magic.size + 4 + headerBytes.size)
    return out
}

fun unframeHeader(bytes: ByteArray): String {
    val magic = PACKAGE_MAGIC.encodeToByteArray()
    require(bytes.size > magic.size + 4) { "Arquivo curto demais para ser um pacote." }
    require(bytes.copyOfRange(0, magic.size).contentEquals(magic)) { "Assinatura desconhecida." }
    val length = readInt(bytes, magic.size)
    require(length in 1..(bytes.size - magic.size - 4)) { "Cabecalho invalido." }
    return bytes.decodeToString(magic.size + 4, magic.size + 4 + length)
}

fun unframePayload(bytes: ByteArray): ByteArray {
    val offset = PACKAGE_MAGIC.length + 4 + readInt(bytes, PACKAGE_MAGIC.length)
    return bytes.copyOfRange(offset, bytes.size)
}

private fun writeInt(target: ByteArray, offset: Int, value: Int) {
    target[offset] = (value ushr 24).toByte()
    target[offset + 1] = (value ushr 16).toByte()
    target[offset + 2] = (value ushr 8).toByte()
    target[offset + 3] = value.toByte()
}

private fun readInt(source: ByteArray, offset: Int): Int =
    ((source[offset].toInt() and 0xFF) shl 24) or
        ((source[offset + 1].toInt() and 0xFF) shl 16) or
        ((source[offset + 2].toInt() and 0xFF) shl 8) or
        (source[offset + 3].toInt() and 0xFF)
