package com.example.sonntag.data.sqldelight

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.File

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

