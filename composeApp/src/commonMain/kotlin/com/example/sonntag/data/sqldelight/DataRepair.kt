package com.example.sonntag.data.sqldelight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver

/**
 * Conserta duplicacoes de agenda deixadas por sincronizacoes antigas.
 *
 * Ate agora a chave natural de reuniao incluia a hora, e a de dia de reuniao tambem.
 * Como a hora e justamente o que o usuario edita, mudar o horario num aparelho fazia
 * o outro criar uma segunda agenda inteira, em paralelo: uma semana preenchida e uma
 * vazia, lado a lado. A chave ja foi corrigida; isto limpa o que ficou para tras.
 *
 * Roda em toda abertura, nos dois sistemas, e nao faz nada quando o banco esta limpo.
 */
object DataRepair {

    private val TABELAS_FILHAS = listOf("weekend_programs", "midweek_programs", "av_assignments")

    /** Dia da semana da coluna `data` no padrao ISO (segunda = 1, domingo = 7). */
    private const val DIA_ISO =
        "(CASE strftime('%w', data) WHEN '0' THEN 7 ELSE CAST(strftime('%w', data) AS INTEGER) END)"

    fun run(driver: SqlDriver, hoje: String) {
        fundeReunioesDuplicadas(driver)
        fundeDiasDuplicados(driver, hoje)
    }

    /**
     * Uma reuniao por data e tipo. Fica a que carrega programa — os registros da outra
     * sao trazidos junto, e so entao ela e apagada.
     */
    private fun fundeReunioesDuplicadas(driver: SqlDriver) {
        val grupos = driver.lista(
            """
            SELECT data, tipo FROM meetings WHERE COALESCE(deleted, 0) = 0
            GROUP BY data, tipo HAVING COUNT(*) > 1
            """
        ) { it.getString(0)!! to it.getString(1)!! }

        grupos.forEach { (data, tipo) ->
            val ids = driver.lista(
                "SELECT id FROM meetings WHERE data = '${data.esc()}' AND tipo = '${tipo.esc()}' " +
                    "AND COALESCE(deleted, 0) = 0 ORDER BY id"
            ) { it.getLong(0)!! }
            if (ids.size < 2) return@forEach

            fun filhos(id: Long): Long = TABELAS_FILHAS.sumOf { tabela ->
                driver.lista("SELECT COUNT(*) FROM $tabela WHERE meeting_id = $id") { it.getLong(0)!! }
                    .firstOrNull() ?: 0L
            }

            // Empate resolve pelo menor id, que e a reuniao que ja existia aqui.
            val fica = ids.maxByOrNull { filhos(it) * 1_000_000L - it } ?: return@forEach

            (ids - fica).forEach { sai ->
                TABELAS_FILHAS.forEach { tabela ->
                    val jaTem = (driver.lista(
                        "SELECT COUNT(*) FROM $tabela WHERE meeting_id = $fica"
                    ) { it.getLong(0)!! }.firstOrNull() ?: 0L) > 0L
                    // UNIQUE(meeting_id) nao deixa as duas conviverem: a vazia perde.
                    if (jaTem) {
                        driver.roda("DELETE FROM $tabela WHERE meeting_id = $sai")
                    } else {
                        driver.roda("UPDATE $tabela SET meeting_id = $fica WHERE meeting_id = $sai")
                    }
                }
                driver.roda("DELETE FROM meetings WHERE id = $sai")
            }
        }
    }

    /**
     * Um dia de reuniao por dia da semana, o mais recente — a mesma regra de ultima
     * alteracao que vale no resto da sincronizacao. As reunioes de hoje em diante
     * passam a valer pela hora que sobrou; as passadas ficam como aconteceram.
     */
    private fun fundeDiasDuplicados(driver: SqlDriver, hoje: String) {
        val dias = driver.lista(
            """
            SELECT dia_semana FROM meeting_days WHERE COALESCE(deleted, 0) = 0
            GROUP BY dia_semana HAVING COUNT(*) > 1
            """
        ) { it.getLong(0)!! }

        dias.forEach { dia ->
            val linhas = driver.lista(
                "SELECT id, hora, COALESCE(updated_at, '') FROM meeting_days " +
                    "WHERE dia_semana = $dia AND COALESCE(deleted, 0) = 0"
            ) { Triple(it.getLong(0)!!, it.getString(1)!!, it.getString(2)!!) }
            if (linhas.size < 2) return@forEach

            // Mais recente primeiro; empate pelo maior id, que tambem e o mais novo.
            val fica = linhas.sortedWith(compareBy({ it.third }, { it.first })).last()
            (linhas - fica).forEach { (id, _, _) ->
                driver.roda("DELETE FROM meeting_days WHERE id = $id")
            }
            // So as reunioes deste mesmo dia da semana: DIA_ISO converte o 0..6 do
            // SQLite (domingo = 0) para o 1..7 que meeting_days usa.
            driver.roda(
                "UPDATE meetings SET hora = '${fica.second.esc()}' " +
                    "WHERE data >= '${hoje.esc()}' AND hora <> '${fica.second.esc()}' " +
                    "AND COALESCE(deleted, 0) = 0 AND $DIA_ISO = $dia"
            )
        }
    }

    private fun String.esc() = replace("'", "''")

    private fun <T : Any> SqlDriver.lista(sql: String, row: (SqlCursor) -> T): List<T> =
        executeQuery(
            identifier = null,
            sql = sql.trimIndent(),
            parameters = 0,
            binders = null,
            mapper = { cursor ->
                val out = mutableListOf<T>()
                while (cursor.next().value) out += row(cursor)
                QueryResult.Value(out.toList())
            },
        ).value

    private fun SqlDriver.roda(sql: String) {
        execute(null, sql, 0).value
    }
}
