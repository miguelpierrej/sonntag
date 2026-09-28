package com.example.sonntag.cloud

/** Onde fica o banco PostgreSQL compartilhado. */
data class CloudConfig(
    val host: String,
    val port: Int,
    val database: String,
    val user: String,
    val password: String,
) {
    companion object {
        const val DEFAULT_PORT = 5432
        const val DEFAULT_DATABASE = "postgres"

        /**
         * Le uma URL `postgresql://usuario:senha@servidor:porta/banco`, como a que o
         * Supabase e o Neon mostram para copiar. Null se o texto nao for uma URL.
         */
        fun fromUri(text: String): CloudConfig? {
            val match = URI_REGEX.matchEntire(text.trim()) ?: return null
            val (user, password, host, port, database) = match.destructured
            return CloudConfig(
                host = host.removePrefix("[").removeSuffix("]"),
                port = port.toIntOrNull() ?: DEFAULT_PORT,
                database = percentDecode(database).ifBlank { DEFAULT_DATABASE },
                user = percentDecode(user),
                password = percentDecode(password),
            )
        }

        private val URI_REGEX = Regex(
            """postgres(?:ql)?://([^:@/]*)(?::([^@/]*))?@(\[[^\]]+]|[^:/?]+)(?::(\d+))?(?:/([^?]*))?(?:\?.*)?""",
        )

        private fun percentDecode(text: String): String {
            if ('%' !in text) return text
            val bytes = mutableListOf<Byte>()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '%' && i + 2 <= text.lastIndex) {
                    val hex = text.substring(i + 1, i + 3).toIntOrNull(16)
                    if (hex != null) {
                        bytes += hex.toByte()
                        i += 3
                        continue
                    }
                }
                c.toString().encodeToByteArray().forEach { bytes += it }
                i++
            }
            return bytes.toByteArray().decodeToString()
        }
    }
}

/**
 * Uma linha como fica na nuvem. So o necessario para a fusao vai em claro; o
 * conteudo ([dados]) vai cifrado com a senha da congregacao.
 */
class CloudRow(
    val tabela: String,
    val uuid: String,
    val updatedAt: String,
    val updatedBy: String,
    val deleted: Boolean,
    val dados: ByteArray,
    /** Ordem de chegada no servidor; 0 numa linha que ainda vai ser enviada. */
    val seq: Long = 0,
)

/** Por que a conversa com a nuvem nao aconteceu. */
enum class CloudFailure {
    SEM_CONEXAO,
    CREDENCIAIS,
    BANCO_INEXISTENTE,
    SEM_PERMISSAO,
    /** A nuvem foi criada por uma versao mais nova do app. */
    VERSAO_NOVA,
    SENHA_CONGREGACAO,
    NAO_CONFIGURADA,
    /** A nuvem salva foi apagada ou trocada por outra: o ponto em que paramos nao vale mais. */
    NUVEM_TROCADA,
    ERRO,
}

class CloudException(val failure: CloudFailure, cause: Throwable? = null) :
    Exception(cause?.message ?: failure.name, cause)

/**
 * Uma conexao aberta com a nuvem. Cada sincronizacao abre a sua e fecha no fim: uma
 * conexao longa nao sobrevive a troca de rede do celular.
 */
interface CloudSession : AutoCloseable {
    /** Cria as tabelas que faltarem. Seguro de chamar sempre. */
    fun ensureSchema()

    fun meta(): Map<String, String>

    /** Grava so as chaves que ainda nao existem; quem chegar primeiro define. */
    fun putMetaIfAbsent(values: Map<String, String>)

    /** Grava sobrescrevendo (nome de cada aparelho, que pode mudar). */
    fun putMeta(values: Map<String, String>)

    /** Linhas gravadas depois de [afterSeq], na ordem em que chegaram. */
    fun pull(afterSeq: Long): List<CloudRow>

    /**
     * Envia [rows]. Uma linha so substitui a da nuvem se for mais recente; devolve
     * quantas foram de fato gravadas. Se alguma foi, avisa quem escuta (ver
     * [CloudListener]) com [origin] — para o proprio aparelho ignorar o aviso.
     */
    fun push(rows: List<CloudRow>, origin: String): Int

    fun countRows(): Long
}

/** Lanca [CloudException] se nao conseguir conectar. */
expect fun openCloudSession(config: CloudConfig): CloudSession

/**
 * Conexao que so espera avisos de que alguem gravou na nuvem. Fica separada da de
 * sincronizar porque esperar prende a conexao.
 *
 * Num pooler em modo transacao (Supabase, porta 6543) os avisos nao chegam; a
 * sincronizacao periodica cobre esse caso.
 */
interface CloudListener : AutoCloseable {
    /** Espera ate [timeoutMs]; devolve a origem de cada aviso recebido (vazio se nenhum). */
    fun await(timeoutMs: Int): List<String>
}

expect fun openCloudListener(config: CloudConfig): CloudListener
