package com.example.sonntag.tools

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.example.sonntag.data.sqldelight.DataRepair
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Roda o reparo de agenda contra um arquivo de banco escolhido, sem abrir o app.
 * Serve para conferir o resultado numa copia antes de mexer no banco de verdade.
 *
 * Uso: repairDb <caminho-do-data.db>
 */
fun main(args: Array<String>) {
    val caminho = args[0]
    val fonte = HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:sqlite:$caminho"
        maximumPoolSize = 1
    })
    val driver = fonte.asJdbcDriver()
    val hoje = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    DataRepair.run(driver, hoje)
    driver.close()
    println("reparo concluido em $caminho (hoje = $hoje)")
}
