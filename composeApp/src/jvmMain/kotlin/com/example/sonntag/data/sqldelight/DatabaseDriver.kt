package com.example.sonntag.data.sqldelight

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.example.sonntag.sync.PREF_DEVICE_ID
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.File
import java.sql.Connection
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Tabelas que viajam entre instalacoes e por isso carregam as colunas de sincronizacao. */
private val SYNC_TABLES = listOf(
    "settings", "meeting_days", "members", "cleaning_groups", "meetings",
    "weekend_programs", "midweek_programs", "cleaning_assignments", "av_assignments",
)

/** (filha, coluna, tabela pai) das referencias obrigatorias, para faxina de orfas. */
private val ORPHAN_CHECKS = listOf(
    Triple("weekend_programs", "meeting_id", "meetings"),
    Triple("midweek_programs", "meeting_id", "meetings"),
    Triple("av_assignments", "meeting_id", "meetings"),
    Triple("cleaning_assignments", "group_id", "cleaning_groups"),
)

/** UUID gerado no proprio SQLite: evita trazer todas as linhas para o Kotlin so para preencher. */
private const val SQL_RANDOM_UUID =
    "lower(hex(randomblob(4))||'-'||hex(randomblob(2))||'-'||hex(randomblob(2))||'-'||" +
        "hex(randomblob(2))||'-'||hex(randomblob(6)))"

/** Mesmo formato de SyncStamp.now(): UTC com precisao de segundos. */
private fun utcNowSeconds(): String = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()

private fun addColumnIfMissing(connection: Connection, table: String, column: String, definition: String) {
    val exists = connection.prepareStatement(
        "SELECT COUNT(*) FROM pragma_table_info('$table') WHERE name = ?"
    ).use { stmt ->
        stmt.setString(1, column)
        stmt.executeQuery().use { rs -> rs.next() && rs.getInt(1) > 0 }
    }
    if (!exists) {
        connection.createStatement().use { it.execute("ALTER TABLE $table ADD COLUMN $column $definition") }
    }
}

/** Id desta instalacao. Criado uma vez e reusado por SyncStamp. */
private fun ensureDeviceId(connection: Connection): String {
    connection.prepareStatement("SELECT valor FROM app_prefs WHERE chave = ?").use { stmt ->
        stmt.setString(1, PREF_DEVICE_ID)
        stmt.executeQuery().use { rs -> if (rs.next()) return rs.getString(1) }
    }
    val id = UUID.randomUUID().toString()
    connection.prepareStatement("INSERT OR REPLACE INTO app_prefs (chave, valor) VALUES (?, ?)").use { stmt ->
        stmt.setString(1, PREF_DEVICE_ID)
        stmt.setString(2, id)
        stmt.executeUpdate()
    }
    return id
}

actual fun createDatabaseDriver(): SqlDriver {
    val dbDir = File(System.getProperty("user.home"), ".salao-app")
    dbDir.mkdirs()

    val dbFile = File(dbDir, "data.db")
    val dbUrl = "jdbc:sqlite:${dbFile.absolutePath}"

    val config = HikariConfig().apply {
        jdbcUrl = dbUrl
        maximumPoolSize = 1
    }
    val dataSource = HikariDataSource(config)
    val driver = dataSource.asJdbcDriver()

    val shouldCreateSchema = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='settings'"
        ).use { statement ->
            statement.executeQuery().use { resultSet ->
                !resultSet.next()
            }
        }
    }

    if (shouldCreateSchema) {
        SonntagDatabase.Schema.create(driver)
    } else {
        // Runtime migrations for existing databases
        dataSource.connection.use { connection ->
            // v2: add orador_nome column to weekend_programs
            val hasOradorNome = connection.prepareStatement(
                "SELECT COUNT(*) FROM pragma_table_info('weekend_programs') WHERE name='orador_nome'"
            ).use { stmt ->
                stmt.executeQuery().use { rs -> rs.next() && rs.getInt(1) > 0 }
            }
            if (!hasOradorNome) {
                connection.createStatement().use { it.execute("ALTER TABLE weekend_programs ADD COLUMN orador_nome TEXT") }
            }

            // v3: add midweek_programs table (formulario S-140)
            connection.createStatement().use {
                it.execute(
                    """
                    CREATE TABLE IF NOT EXISTS midweek_programs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        meeting_id INTEGER NOT NULL UNIQUE,
                        leitura_semanal TEXT,
                        presidente_id INTEGER,
                        conselheiro_id INTEGER,
                        cantico_inicial TEXT,
                        oracao_inicial_id INTEGER,
                        tesouros_titulo TEXT,
                        tesouros_orador_id INTEGER,
                        joias_id INTEGER,
                        leitura_biblia_id INTEGER,
                        min1_titulo TEXT,
                        min1_minutos TEXT,
                        min1_estudante_id INTEGER,
                        min1_ajudante_id INTEGER,
                        min2_titulo TEXT,
                        min2_minutos TEXT,
                        min2_estudante_id INTEGER,
                        min2_ajudante_id INTEGER,
                        min3_titulo TEXT,
                        min3_minutos TEXT,
                        min3_estudante_id INTEGER,
                        min3_ajudante_id INTEGER,
                        min4_titulo TEXT,
                        min4_minutos TEXT,
                        min4_estudante_id INTEGER,
                        min4_ajudante_id INTEGER,
                        cantico_meio TEXT,
                        vida1_titulo TEXT,
                        vida1_minutos TEXT,
                        vida1_id INTEGER,
                        vida2_titulo TEXT,
                        vida2_minutos TEXT,
                        vida2_id INTEGER,
                        estudo_dirigente_id INTEGER,
                        estudo_leitor_id INTEGER,
                        cantico_final TEXT,
                        oracao_final_id INTEGER,
                        FOREIGN KEY (meeting_id) REFERENCES meetings(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }

            // v5: tabela de preferencias (idioma etc.)
            connection.createStatement().use {
                it.execute("CREATE TABLE IF NOT EXISTS app_prefs (chave TEXT PRIMARY KEY, valor TEXT NOT NULL)")
            }

            // v6: tabela de designacoes de audio/video e acomodadores
            connection.createStatement().use {
                it.execute(
                    """
                    CREATE TABLE IF NOT EXISTS av_assignments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        meeting_id INTEGER NOT NULL UNIQUE,
                        audio_id INTEGER,
                        video_id INTEGER,
                        plataforma1_id INTEGER,
                        plataforma2_id INTEGER,
                        microfone1_id INTEGER,
                        microfone2_id INTEGER,
                        acomodador1_id INTEGER,
                        acomodador2_id INTEGER,
                        FOREIGN KEY (meeting_id) REFERENCES meetings(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }

            // v7: bosquejos de discursos publicos importados do S-34 (.jwpub)
            connection.createStatement().use {
                it.execute(
                    """
                    CREATE TABLE IF NOT EXISTS talk_outlines (
                        numero INTEGER PRIMARY KEY,
                        titulo TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }

            // v8: colunas de sincronizacao nas tabelas compartilhaveis + fila de conflitos.
            // Nao entram app_prefs (idioma e id do dispositivo sao locais) nem
            // talk_outlines (cada instalacao importa o proprio S-34).
            val deviceId = ensureDeviceId(connection)
            val migratedAt = utcNowSeconds()
            SYNC_TABLES.forEach { table ->
                // ALTER TABLE nao aceita NOT NULL sem default; por isso o default vazio
                // seguido do preenchimento abaixo.
                addColumnIfMissing(connection, table, "uuid", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(connection, table, "updated_at", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(connection, table, "updated_by", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(connection, table, "deleted", "INTEGER NOT NULL DEFAULT 0")

                // Linhas que ja existiam ganham identidade global e o carimbo da migracao.
                connection.createStatement().use {
                    it.execute("UPDATE $table SET uuid = $SQL_RANDOM_UUID WHERE uuid = ''")
                }
                connection.prepareStatement(
                    "UPDATE $table SET updated_at = ?, updated_by = ? WHERE updated_at = ''"
                ).use { stmt ->
                    stmt.setString(1, migratedAt)
                    stmt.setString(2, deviceId)
                    stmt.executeUpdate()
                }
                connection.createStatement().use {
                    it.execute("CREATE UNIQUE INDEX IF NOT EXISTS ${table}_uuid ON $table(uuid)")
                }
            }

            connection.createStatement().use {
                it.execute(
                    """
                    CREATE TABLE IF NOT EXISTS sync_conflicts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        tabela TEXT NOT NULL,
                        row_uuid TEXT NOT NULL,
                        local_json TEXT NOT NULL,
                        remoto_json TEXT NOT NULL,
                        remoto_device TEXT NOT NULL,
                        detectado_em TEXT NOT NULL,
                        resolvido INTEGER NOT NULL DEFAULT 0,
                        UNIQUE(tabela, row_uuid)
                    )
                    """.trimIndent()
                )
            }

            // v9: remove linhas filhas cujo pai nao existe mais. O SQLite nao impoe
            // chave estrangeira por padrao, entao exclusoes antigas deixaram programas
            // apontando para reunioes inexistentes. Elas nunca aparecem em tela (a UI
            // parte da reuniao) e travariam a importacao, que nao tem como resolver a
            // referencia obrigatoria.
            ORPHAN_CHECKS.forEach { (child, column, parent) ->
                connection.createStatement().use {
                    it.execute(
                        """
                        DELETE FROM $child
                        WHERE $column IS NOT NULL
                          AND $column NOT IN (SELECT id FROM $parent)
                        """.trimIndent()
                    )
                }
            }

            // v4: add 4th ministry slot to midweek_programs (apostilas com 4 partes)
            listOf("min4_titulo TEXT", "min4_minutos TEXT", "min4_estudante_id INTEGER", "min4_ajudante_id INTEGER")
                .forEach { columnDef ->
                    val name = columnDef.substringBefore(' ')
                    val hasColumn = connection.prepareStatement(
                        "SELECT COUNT(*) FROM pragma_table_info('midweek_programs') WHERE name=?"
                    ).use { stmt ->
                        stmt.setString(1, name)
                        stmt.executeQuery().use { rs -> rs.next() && rs.getInt(1) > 0 }
                    }
                    if (!hasColumn) {
                        connection.createStatement().use { it.execute("ALTER TABLE midweek_programs ADD COLUMN $columnDef") }
                    }
                }
        }
    }

    return driver
}

