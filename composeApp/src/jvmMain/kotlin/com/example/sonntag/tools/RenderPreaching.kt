package com.example.sonntag.tools

import com.example.sonntag.data.repos.PreachingKind
import com.example.sonntag.data.sqldelight.SonntagDatabase
import com.example.sonntag.data.sqldelight.SchemaUpgrade
import com.example.sonntag.domain.usecases.PreachingMonthGenerator
import com.example.sonntag.i18n.AppLanguage
import com.example.sonntag.i18n.Translator
import com.example.sonntag.pdf.PreachingDayPdf
import com.example.sonntag.pdf.PreachingGroupPdf
import com.example.sonntag.pdf.PreachingProgramPdfData
import com.example.sonntag.pdf.PreachingShiftPdf
import com.example.sonntag.pdf.nomeCurtoDeCalendario
import com.example.sonntag.pdf.preachingPdfStrings
import com.example.sonntag.pdf.render.PdfBoxCanvas
import com.example.sonntag.pdf.render.PreachingCalendarLayout
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.io.File

/**
 * Gera os dois programas de pregacao a partir de um banco existente, sem abrir o app.
 *
 *     DB=/caminho/data.db ANO=2026 MES=8 OUT=/tmp/docs ./gradlew :composeApp:renderPreaching
 */
fun main(args: Array<String>) {
    val dbPath = args[0]
    val ano = args[1].toInt()
    val mes = args[2].toInt()
    val out = File(args[3]).apply { mkdirs() }

    val fonte = HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:sqlite:$dbPath"
        maximumPoolSize = 1
    })
    val driver = fonte.asJdbcDriver()
    SchemaUpgrade.run(driver)
    val db = SonntagDatabase(driver)
    val q = db.schemaQueries

    val t = Translator(AppLanguage.ES)
    val labels = preachingPdfStrings(AppLanguage.ES)
    val nomes = q.getAllMembers().executeAsList()
        .associate { it.id to nomeCurtoDeCalendario(it.nome, it.sobrenome) }
    val pontos = q.getAllPreachingSpots().executeAsList().associateBy { it.id }
    val congregacao = q.getSettings().executeAsOneOrNull()?.nome ?: "Congregación"

    PreachingKind.entries.forEach { tipo ->
        val grade = PreachingMonthGenerator.gradeDoMes(ano, mes)
        val turnos = q.getPreachingShiftsBetween(
            tipo.name,
            grade.first().first().toString(),
            grade.last().last().toString(),
        ).executeAsList().groupBy { it.data_ }

        val grupos = if (tipo == PreachingKind.PREDICACION) {
            q.getAllPreachingGroups().executeAsList().map { g ->
                PreachingGroupPdf(
                    nome = g.nome,
                    dirigente = g.dirigente_id?.let { nomes[it] },
                    local = pontos[g.spot_id]?.let { p ->
                        listOfNotNull(p.nome, p.endereco?.takeIf { it.isNotBlank() }).joinToString(" – ")
                    },
                )
            }
        } else {
            emptyList()
        }

        val data = PreachingProgramPdfData(
            congregacao = congregacao,
            titulo = if (tipo == PreachingKind.CARRITO) labels.tituloCarritos else labels.tituloPredicacion,
            mesLabel = t.monthYearLabel(mes, ano),
            fileSlug = "pregacao-${tipo.name.lowercase()}",
            semanas = grade.map { semana ->
                semana.map { dia ->
                    PreachingDayPdf(
                        dia = dia.dayOfMonth,
                        doMes = dia.monthNumber == mes && dia.year == ano,
                        turnos = turnos[dia.toString()].orEmpty().map { s ->
                            PreachingShiftPdf(
                                hora = listOfNotNull(s.hora_inicio, s.hora_fim).joinToString(" a "),
                                ponto = pontos[s.spot_id]?.nome,
                                nomes = listOfNotNull(
                                    s.designado1_id, s.designado2_id, s.designado3_id, s.designado4_id,
                                ).mapNotNull { nomes[it] },
                                nota = s.nota,
                            )
                        },
                    )
                }
            },
            grupos = grupos,
            observacao = q.getPreachingNote(tipo.name, ano.toLong(), mes.toLong())
                .executeAsOneOrNull()?.texto,
            labels = labels,
        )

        val doc = PDDocument()
        PdfBoxCanvas(doc, PDRectangle.A4).use { PreachingCalendarLayout(data, "17/08/2026 09:00").draw(it) }
        doc.save(File(out, "${data.fileSlug}.pdf"))
        doc.close()
        println("gerado: ${data.fileSlug}.pdf  (${data.semanas.flatten().sumOf { it.turnos.size }} turnos)")
    }
}
