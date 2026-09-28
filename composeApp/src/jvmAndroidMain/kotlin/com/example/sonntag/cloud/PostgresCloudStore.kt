package com.example.sonntag.cloud

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
import java.util.Properties
import org.postgresql.PGConnection

/**
 * Chave do trava-escrita no servidor ("SNTG"). Dois envios ao mesmo tempo pegariam
 * numeros de sequencia intercalados e poderiam confirmar fora de ordem: quem lesse
 * entre um e outro pularia de vez as linhas do mais lento.
 */
private const val PUSH_LOCK = 0x534E5447L

private const val BATCH = 500

/** Canal do LISTEN/NOTIFY. */
private const val CANAL = "sonntag"

/**
 * As tabelas da nuvem. Uma so para os dados, generica, em vez de uma por tabela do
 * app: coluna nova no app nao exige migrar a nuvem, e versoes diferentes do app
 * convivem no mesmo banco.
 *
 * `COLLATE "C"` nas colunas comparadas: a ordem textual precisa ser a mesma do
 * Kotlin, senao o servidor e o app discordam sobre qual versao e a mais nova.
 */
private val DDL = listOf(
    """
    CREATE TABLE IF NOT EXISTS sonntag_meta (
        chave TEXT PRIMARY KEY,
        valor TEXT NOT NULL
    )
    """,
    "CREATE SEQUENCE IF NOT EXISTS sonntag_seq",
    """
    CREATE TABLE IF NOT EXISTS sonntag_rows (
        tabela TEXT NOT NULL,
        uuid TEXT NOT NULL,
        updated_at TEXT COLLATE "C" NOT NULL,
        updated_by TEXT COLLATE "C" NOT NULL,
        deleted BOOLEAN NOT NULL DEFAULT FALSE,
        dados BYTEA NOT NULL,
        seq BIGINT NOT NULL DEFAULT nextval('sonntag_seq'),
        PRIMARY KEY (tabela, uuid)
    )
    """,
    "CREATE INDEX IF NOT EXISTS sonntag_rows_seq ON sonntag_rows (seq)",
)

/**
 * No Supabase, tabela do esquema public sem RLS fica aberta a qualquer um com a
 * chave publica da API REST. Ligar o RLS sem politica fecha essa porta; o dono das
 * tabelas (o usuario com que o app conecta) nao e afetado.
 */
private val RLS = listOf(
    "ALTER TABLE sonntag_meta ENABLE ROW LEVEL SECURITY",
    "ALTER TABLE sonntag_rows ENABLE ROW LEVEL SECURITY",
)

private val UPSERT = """
    INSERT INTO sonntag_rows (tabela, uuid, updated_at, updated_by, deleted, dados)
    VALUES (?, ?, ?, ?, ?, ?)
    ON CONFLICT (tabela, uuid) DO UPDATE SET
        updated_at = EXCLUDED.updated_at,
        updated_by = EXCLUDED.updated_by,
        deleted = EXCLUDED.deleted,
        dados = EXCLUDED.dados,
        seq = nextval('sonntag_seq')
    WHERE (EXCLUDED.updated_at, EXCLUDED.updated_by) > (sonntag_rows.updated_at, sonntag_rows.updated_by)
""".trimIndent()

private class PostgresCloudSession(private val conn: Connection) : CloudSession {

    override fun ensureSchema() = guard {
        conn.createStatement().use { st -> DDL.forEach { st.execute(it.trimIndent()) } }
        RLS.forEach { sql ->
            // So o dono pode ligar; se as tabelas sao de outro usuario, fica como esta.
            runCatching { conn.createStatement().use { it.execute(sql) } }
        }
    }

    override fun meta(): Map<String, String> = guard {
        conn.createStatement().use { st ->
            st.executeQuery("SELECT chave, valor FROM sonntag_meta").use { rs ->
                buildMap { while (rs.next()) put(rs.getString(1), rs.getString(2)) }
            }
        }
    }

    override fun putMetaIfAbsent(values: Map<String, String>) = guard {
        conn.prepareStatement(
            "INSERT INTO sonntag_meta (chave, valor) VALUES (?, ?) ON CONFLICT (chave) DO NOTHING",
        ).use { st ->
            values.forEach { (k, v) ->
                st.setString(1, k)
                st.setString(2, v)
                st.executeUpdate()
            }
        }
    }

    override fun putMeta(values: Map<String, String>) = guard {
        conn.prepareStatement(
            "INSERT INTO sonntag_meta (chave, valor) VALUES (?, ?) " +
                "ON CONFLICT (chave) DO UPDATE SET valor = EXCLUDED.valor",
        ).use { st ->
            values.forEach { (k, v) ->
                st.setString(1, k)
                st.setString(2, v)
                st.executeUpdate()
            }
        }
    }

    override fun pull(afterSeq: Long): List<CloudRow> = guard {
        conn.prepareStatement(
            "SELECT tabela, uuid, updated_at, updated_by, deleted, dados, seq " +
                "FROM sonntag_rows WHERE seq > ? ORDER BY seq",
        ).use { st ->
            st.setLong(1, afterSeq)
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            CloudRow(
                                tabela = rs.getString(1),
                                uuid = rs.getString(2),
                                updatedAt = rs.getString(3),
                                updatedBy = rs.getString(4),
                                deleted = rs.getBoolean(5),
                                dados = rs.getBytes(6),
                                seq = rs.getLong(7),
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun push(rows: List<CloudRow>, origin: String): Int = guard {
        if (rows.isEmpty()) return@guard 0
        conn.autoCommit = false
        try {
            conn.prepareStatement("SELECT pg_advisory_xact_lock(?)").use { st ->
                st.setLong(1, PUSH_LOCK)
                st.executeQuery().close()
            }
            var gravadas = 0
            conn.prepareStatement(UPSERT).use { st ->
                rows.chunked(BATCH).forEach { lote ->
                    lote.forEach { row ->
                        st.setString(1, row.tabela)
                        st.setString(2, row.uuid)
                        st.setString(3, row.updatedAt)
                        st.setString(4, row.updatedBy)
                        st.setBoolean(5, row.deleted)
                        st.setBytes(6, row.dados)
                        st.addBatch()
                    }
                    gravadas += st.executeBatch().sumOf { n ->
                        when {
                            n > 0 -> n
                            n == Statement.SUCCESS_NO_INFO -> 1
                            else -> 0
                        }
                    }
                }
            }
            if (gravadas > 0) {
                // Dentro da transacao: o aviso so sai no commit, com os dados ja visiveis.
                conn.prepareStatement("SELECT pg_notify('$CANAL', ?)").use { st ->
                    st.setString(1, origin)
                    st.executeQuery().close()
                }
            }
            conn.commit()
            gravadas
        } catch (e: Exception) {
            runCatching { conn.rollback() }
            throw e
        } finally {
            runCatching { conn.autoCommit = true }
        }
    }

    override fun countRows(): Long = guard {
        conn.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM sonntag_rows").use { rs -> if (rs.next()) rs.getLong(1) else 0 }
        }
    }

    override fun close() {
        runCatching { conn.close() }
    }
}

private class PostgresCloudListener(private val conn: Connection) : CloudListener {
    init {
        guard { conn.createStatement().use { it.execute("LISTEN $CANAL") } }
    }

    override fun await(timeoutMs: Int): List<String> = guard {
        conn.unwrap(PGConnection::class.java).getNotifications(timeoutMs)
            ?.map { it.parameter.orEmpty() }
            .orEmpty()
    }

    override fun close() {
        runCatching { conn.close() }
    }
}

actual fun openCloudListener(config: CloudConfig): CloudListener {
    val conn = connect(config, socketTimeoutSeconds = 0)
    return try {
        PostgresCloudListener(conn)
    } catch (e: Exception) {
        runCatching { conn.close() }
        throw e
    }
}

actual fun openCloudSession(config: CloudConfig): CloudSession =
    PostgresCloudSession(connect(config, socketTimeoutSeconds = 60))

private inline fun <T> guard(block: () -> T): T = try {
    block()
} catch (e: SQLException) {
    throw translate(e)
}

/**
 * [socketTimeoutSeconds] 0 no ouvinte: ele fica parado esperando por natureza, e o
 * proprio `await` ja tem prazo.
 */
private fun connect(config: CloudConfig, socketTimeoutSeconds: Int): Connection {
    val host = if (':' in config.host) "[${config.host}]" else config.host
    val url = "jdbc:postgresql://$host:${config.port}/${config.database}"
    val props = Properties().apply {
        setProperty("user", config.user)
        setProperty("password", config.password)
        setProperty("ApplicationName", "Sonntag")
        setProperty("connectTimeout", "15")
        setProperty("loginTimeout", "20")
        setProperty("socketTimeout", socketTimeoutSeconds.toString())
        // Cifra quando o servidor oferece (Supabase, Neon), sem exigir: um Postgres
        // na rede de casa costuma nao ter certificado.
        setProperty("sslmode", "prefer")
        // O pooler de transacao do Supabase nao guarda comandos preparados entre
        // transacoes; sem isto o quinto envio quebra.
        setProperty("prepareThreshold", "0")
        setProperty("tcpKeepAlive", "true")
    }
    val conn = try {
        // Instanciado direto, sem DriverManager: no APK o META-INF que registra o
        // driver e descartado (ver packaging no build.gradle.kts).
        org.postgresql.Driver().connect(url, props)
            ?: throw CloudException(CloudFailure.ERRO)
    } catch (e: SQLException) {
        throw translate(e)
    }
    return conn
}

/** Traduz o erro do driver num motivo que a tela sabe explicar. */
private fun translate(e: SQLException): CloudException {
    val state = e.sqlState.orEmpty()
    val rede = generateSequence(e as Throwable) { it.cause }.any {
        it is UnknownHostException || it is ConnectException ||
            it is SocketTimeoutException || it is NoRouteToHostException
    }
    val failure = when {
        rede || state.startsWith("08") -> CloudFailure.SEM_CONEXAO
        state == "28P01" || state == "28000" -> CloudFailure.CREDENCIAIS
        state == "3D000" -> CloudFailure.BANCO_INEXISTENTE
        state == "42501" -> CloudFailure.SEM_PERMISSAO
        else -> CloudFailure.ERRO
    }
    return CloudException(failure, e)
}
