package com.example.sonntag.tools

import com.example.sonntag.i18n.AppLanguage
import com.example.sonntag.pdf.AvScheduleLine
import com.example.sonntag.pdf.AvSchedulePdfData
import com.example.sonntag.pdf.CleaningSchedulePdfData
import com.example.sonntag.pdf.CleaningScheduleLine
import com.example.sonntag.pdf.MeetingProgramPdfData
import com.example.sonntag.pdf.MidweekPartPdf
import com.example.sonntag.pdf.MonthlyProgramPdfData
import com.example.sonntag.pdf.PreachingGroupMemberPdf
import com.example.sonntag.pdf.PreachingGroupSheetPdf
import com.example.sonntag.pdf.PreachingGroupsPdfData
import com.example.sonntag.pdf.PdfMeetingLine
import com.example.sonntag.pdf.MidweekProgramPdfData
import com.example.sonntag.pdf.MidweekWeekPdf
import com.example.sonntag.pdf.avPdfStrings
import com.example.sonntag.pdf.cleaningPdfStrings
import com.example.sonntag.pdf.midweekPdfStrings
import com.example.sonntag.pdf.preachingPdfStrings
import com.example.sonntag.pdf.weekendPdfStrings
import com.example.sonntag.pdf.render.AvScheduleLayout
import com.example.sonntag.pdf.render.CleaningLayout
import com.example.sonntag.pdf.render.MidweekProgramLayout
import com.example.sonntag.pdf.render.PreachingGroupsLayout
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
            // A quarta reuniao cai numa semana de congresso: o pior caso do bloco.
            eventoLabel = if (data == "12/04/2026") "Sin reunión · Congreso: Congreso Regional" else null,
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
            eventoLabel = if (i == 2) "Sin reunión · Congreso: Congreso Regional" else null,
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
                    if (it == 3) CleaningScheduleLine(
                        "$it–${it + 6} de abril", "Congreso", null,
                        eventoLabel = "Sin reunión · Congreso Regional",
                    ) else CleaningScheduleLine("$it–${it + 6} de abril", "Martes y Domingo", "Grupo $it")
                },
                labels = cleaningPdfStrings(lang),
            ),
            geradoEm = "15/08/2026 20:30",
        ).draw(canvas)
    }

    // ── Grupos de pregacao ──
    // O exemplo tem quatro grupos (quebra de pagina nas colunas) e um deles com
    // publicadores demais para uma coluna so (quebra dentro do grupo).
    val publicadores = listOf(
        "Nombre Apellido" to "AN | SM | PR", "María Victoria Morales" to "PR",
        "Juan Carlos Pérez" to null, "Ana Lucía Rodríguez" to "PR",
        "Pedro Antonio Gómez" to "SM", "Lucía Fernández" to null,
        "Roberto Silva" to "AN", "Carmen Delgado" to "PR",
        "Miguel Ángel Torres" to "SM | PR", "Rosa María Castillo" to null,
        "Andrés Villalba" to null, "Teresa Ibarra" to "PR",
    ).map { (nome, siglas) -> PreachingGroupMemberPdf(nome, siglas) }
    pdf("grupos-de-pregacao") { canvas ->
        PreachingGroupsLayout(
            PreachingGroupsPdfData(
                congregacao = "Espanhola (Penha de França)",
                subtitulo = "Domingo, 5 de abril de 2026",
                fileSlug = "grupos",
                grupos = (1..4).map { n ->
                    PreachingGroupSheetPdf(
                        nome = "Grupo $n",
                        dirigente = "Marcos Pinheiro",
                        auxiliar = if (n == 2) null else "Vlanilton Amirate",
                        // Nome comprido de proposito: e o caso que saia cortado.
                        ponto = if (n == 3) null else "Salón Del Reino De Los Testigos De Jehová - Arere",
                        membros = when (n) {
                            1 -> (1..5).flatMap { r -> publicadores.map { it.copy(nome = "${it.nome} ($r)") } }
                            4 -> emptyList()
                            else -> publicadores.take(n * 3)
                        },
                    )
                },
                labels = preachingPdfStrings(lang),
            ),
            geradoEm = "15/08/2026 20:30",
        ).draw(canvas)
    }

    // A folha de dois grupos: colunas largas e centralizadas, o caso comum de uma
    // congregacao pequena.
    pdf("grupos-de-pregacao-dois") { canvas ->
        PreachingGroupsLayout(
            PreachingGroupsPdfData(
                congregacao = "Espanhola (Penha de França)",
                subtitulo = "Domingo, 5 de abril de 2026",
                fileSlug = "grupos2",
                grupos = (1..2).map { n ->
                    PreachingGroupSheetPdf(
                        nome = "Grupo $n",
                        dirigente = "Marcos Pinheiro",
                        auxiliar = "Vlanilton Amirate",
                        ponto = "Salón Del Reino De Los Testigos De Jehová - Arere",
                        membros = publicadores,
                    )
                },
                labels = preachingPdfStrings(lang),
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
                semanas = listOf(
                    semana,
                    semana.copy(
                        periodo = "12-18 de enero",
                        leitura = "",
                        eventoLabel = "Sin reunión · Congreso: Congreso Regional",
                    ),
                ),
                labels = midweekPdfStrings(lang),
            ),
            iconTesouros = icone("secao-tesouros"),
            iconMinisterio = icone("secao-ministerio"),
            iconVida = icone("secao-vida"),
        ).draw(canvas)
    }
    println("ok -> ${out.absolutePath}")
}
