package com.example.sonntag.tools

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.example.sonntag.data.repos.MeetingDaysRepository
import com.example.sonntag.data.repos.MeetingsRepository
import com.example.sonntag.data.repos.PreferencesRepository
import com.example.sonntag.data.sqldelight.SchemaUpgrade
import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.domain.usecases.MeetingGenerator
import com.example.sonntag.sync.SyncStamp
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Verifica se o gerador de agenda duplica uma reuniao que chegou de outro aparelho
 * com horario diferente — o caso "terca as 19:30 la, 19:00 aqui".
 *
 *     DB=/copia/data.db ./gradlew :composeApp:checkGenerator
 */
fun main(args: Array<String>) {
    val fonte = HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:sqlite:${args[0]}"
        maximumPoolSize = 1
    })
    val driver = fonte.asJdbcDriver()
    SchemaUpgrade.run(driver)
    val db = SonntagDatabase(driver)
    val stamp = SyncStamp(PreferencesRepository(db))
    val meetings = MeetingsRepository(db, stamp)
    val days = MeetingDaysRepository(db, stamp)

    val dia = days.getAllOnce().firstOrNull { it.dia_semana in 1..5 } ?: error("sem dia de semana")
    println("dia de reuniao configurado: dia ${dia.dia_semana} as ${dia.hora}")

    // Uma reuniao futura no mesmo dia da semana, com horario diferente — como se
    // tivesse chegado do outro aparelho.
    val hoje = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    var alvo = hoje.plus(DatePeriod(days = 1))
    while (alvo.dayOfWeek.isoDayNumber.toLong() != dia.dia_semana) {
        alvo = alvo.plus(DatePeriod(days = 1))
    }

    val outroHorario = if (dia.hora == "19:00") "19:30" else "19:00"
    meetings.getByDateRangeOnce(alvo.toString(), alvo.toString()).forEach { meetings.delete(it.id) }
    meetings.insert(alvo.toString(), outroHorario, "WEEKDAY")
    println("inseri $alvo as $outroHorario (como se tivesse vindo do outro aparelho)")

    MeetingGenerator(days, meetings).generateNext12Months()

    val doDia = meetings.getByDateRangeOnce(alvo.toString(), alvo.toString())
        .filter { it.deleted == 0L }
    println("depois de gerar, reunioes em $alvo: ${doDia.size}")
    doDia.forEach { println("   ${it.data_} ${it.hora} ${it.tipo}") }
    println(if (doDia.size == 1) "OK — nao duplicou" else "FALHOU — duplicou")
    driver.close()
}
