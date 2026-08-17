package com.example.sonntag.tools

import com.example.sonntag.i18n.AppLanguage
import com.example.sonntag.pdf.AvScheduleLine
import com.example.sonntag.pdf.AvSchedulePdfData
import com.example.sonntag.pdf.CleaningSchedulePdfData
import com.example.sonntag.pdf.CleaningScheduleLine
import com.example.sonntag.pdf.MeetingProgramPdfData
import com.example.sonntag.pdf.MidweekPartPdf
import com.example.sonntag.pdf.MonthlyProgramPdfData
import com.example.sonntag.pdf.PdfMeetingLine
import com.example.sonntag.pdf.MidweekProgramPdfData
import com.example.sonntag.pdf.MidweekWeekPdf
import com.example.sonntag.pdf.avPdfStrings
import com.example.sonntag.pdf.cleaningPdfStrings
import com.example.sonntag.pdf.midweekPdfStrings
import com.example.sonntag.pdf.weekendPdfStrings
import com.example.sonntag.pdf.render.AvScheduleLayout
import com.example.sonntag.pdf.render.CleaningLayout
import com.example.sonntag.pdf.render.MidweekProgramLayout
import com.example.sonntag.pdf.render.WeekendMeetingLayout
import com.example.sonntag.pdf.render.WeekendMonthlyLayout
import com.example.sonntag.pdf.render.PdfBoxCanvas
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.io.File

/**
 * Gera os documentos exportaveis com dados de exemplo, para conferir o layout sem
 * precisar de banco nem de interface:
 *
 *     OUT=/tmp/docs ./gradlew :composeApp:renderDocs
 *
 * Os exemplos usam o pior caso de cada folha (mes de cinco semanas no audio/video,
 * designacoes faltando), que e onde o layout costuma quebrar.
 */
fun main(args: Array<String>) {
    val out = File(args[0]).apply { mkdirs() }
    val lang = AppLanguage.ES

    // Cada documento usa a folha do seu servico: Letter no geral, A4 no S-140.
    fun pdf(nome: String, folha: PDRectangle = PDRectangle.LETTER, bloco: (PdfBoxCanvas) -> Unit) {
        val doc = PDDocument()
        PdfBoxCanvas(doc, folha).use { bloco(it) }
        doc.save(File(out, "$nome.pdf"))
        doc.close()
    }

    // ── Audio/video ──
    val avLines = listOf(
        "31/03/2026" to true, "05/04/2026" to false, "07/04/2026" to true, "12/04/2026" to false,
        "14/04/2026" to true, "19/04/2026" to false, "21/04/2026" to true, "26/04/2026" to false,
        "28/04/2026" to true, "03/05/2026" to false,
    ).map { (data, meio) ->
        AvScheduleLine(
            dataLabel = data,
            tipoLabel = if (meio) avPdfStrings(lang).reuniaoMeioSemana else avPdfStrings(lang).reuniaoFimSemana,
            audio = "Marcos Pinheiro", video = "Vlanilton Amirate",
            plataforma = listOf("Nombre Apellido", "Nombre Apellido"),
            microfones = listOf("Nombre Apellido", "Nombre Apellido"),
            acomodadores = listOf("Nombre Apellido", "Nombre Apellido"),
        )
    }
    pdf("audio-video") { canvas ->
        AvScheduleLayout(
            AvSchedulePdfData(
                congregacao = "Espanhola (Penha de França)", endereco = "Rua Arere 66",
                mesLabel = "Abril 2026", fileSlug = "av", reunioes = avLines, labels = avPdfStrings(lang),
            ),
            geradoEm = "15/08/2026 20:30",
        ).draw(canvas)
    }

    // ── Fim de semana (mensal) ──
    val reunioes = listOf(
        "Domingo, 5 de abril", "Domingo, 12 de abril", "Domingo, 19 de abril",
        "Domingo, 26 de abril", "Domingo, 3 de mayo",
    ).mapIndexed { i, dia ->
        PdfMeetingLine(
            dateLabel = dia, hora = "10:00",
            tituloDiscurso = if (i < 4) "¿Cómo podemos tener paz mental en un mundo lleno de problemas?" else null,
            orador = if (i < 4) "Marcos Pinheiro" else null,
            presidente = "Vlanilton Amirate",
            dirigenteEstudo = if (i < 3) "Nombre Apellido" else null,
            leitor = if (i < 3) "Nombre Apellido" else null,
        )
    }
    pdf("fim-de-semana") { canvas ->
        WeekendMonthlyLayout(
            MonthlyProgramPdfData(
                congregacao = "Española Penha de França",
                mesLabel = "Abril 2026", fileSlug = "fds",
                reunioes = reunioes, labels = weekendPdfStrings(lang),
            ),
            geradoEm = "15/08/2026 20:30",
        ).draw(canvas)
    }

    // ── Fim de semana (uma reuniao) ──
    pdf("fim-de-semana-reuniao") { canvas ->
        WeekendMeetingLayout(
            MeetingProgramPdfData(
                congregacao = "Española Penha de França",
                dateLabel = "Domingo, 5 de abril", hora = "10:00", fileSlug = "fds1",
                tituloDiscurso = "¿Cómo podemos tener paz mental en un mundo lleno de problemas?",
                orador = "Marcos Pinheiro", presidente = "Vlanilton Amirate",
                dirigenteEstudo = "Nombre Apellido", leitor = null,
                labels = weekendPdfStrings(lang),
            ),
            geradoEm = "15/08/2026 20:30",
        ).draw(canvas)
    }

    // ── Limpeza ──
    pdf("limpeza") { canvas ->
        CleaningLayout(
            CleaningSchedulePdfData(
                congregacao = "Espanhola (Penha de França)", endereco = "Rua Arere 66",
                mesLabel = "Abril 2026", fileSlug = "limpeza",
                semanas = (1..5).map {
                    CleaningScheduleLine("$it–${it + 6} de abril", "Martes y Domingo", "Grupo $it")
                },
                labels = cleaningPdfStrings(lang),
            ),
            geradoEm = "15/08/2026 20:30",
        ).draw(canvas)
    }

    // ── Meio de semana ──
    fun parte(n: Int, titulo: String, mins: String, nome: String? = "Nombre Apellido", nome2: String? = null) =
        MidweekPartPdf(n, titulo, mins, nome, nome2)
    val semana = MidweekWeekPdf(
        periodo = "5-11 de enero", leitura = "Isaías 17-20",
        presidente = "Nombre Apellido", oracaoInicial = "Nombre Apellido", canticoInicial = "153",
        tesouros = parte(1, "“Lo que van a recibir quienes nos despojan”", "10"),
        joias = parte(2, "Busquemos perlas escondidas", "10"),
        leituraBiblia = parte(3, "Lectura de la Biblia", "4"),
        ministerio = listOf(
            parte(4, "Empiece conversaciones", "3", "Nombre Apellido", "Nombre Apellido"),
            parte(5, "Haga revisitas", "4", "Nombre Apellido", "Nombre Apellido"),
            parte(6, "Discurso", "5"),
        ),
        canticoMeio = "148",
        vida = listOf(parte(7, "Acuérdate de “la Roca de tu fortaleza”", "10")),
        estudo = MidweekPartPdf(9, "Estudio bíblico de la congregación", "30", "Nombre Apellido", "Nombre Apellido"),
        canticoFinal = "73", oracaoFinal = "Nombre Apellido",
    )
    fun icone(nome: String): ByteArray? =
        object {}.javaClass.classLoader.getResourceAsStream("icons/$nome.png")?.use { it.readBytes() }

    pdf("meio-semana", PDRectangle.A4) { canvas ->
        MidweekProgramLayout(
            data = MidweekProgramPdfData(
                congregacao = "Espanhola (Penha de França)", subtitulo = "Centro Sur",
                mesLabel = "Enero 2026", fileSlug = "s140",
                semanas = listOf(semana, semana.copy(periodo = "12-18 de enero", leitura = "Isaías 21-23")),
                labels = midweekPdfStrings(lang),
            ),
            iconTesouros = icone("secao-tesouros"),
            iconMinisterio = icone("secao-ministerio"),
            iconVida = icone("secao-vida"),
        ).draw(canvas)
    }
    println("ok -> ${out.absolutePath}")
}
