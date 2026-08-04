package com.example.sonntag.pdf.render

import com.example.sonntag.pdf.AssignmentPdfStrings
import com.example.sonntag.pdf.MidweekAssignmentPdf
import com.example.sonntag.pdf.MidweekAssignmentsPdfData

/** Comprovantes de designacao (S-89): oito por folha A4, em duas colunas. */
class AssignmentSlipsLayout(private val data: MidweekAssignmentsPdfData) {

    private val marginLeft = 36f
    private val marginTop = 36f
    private val gap = 18f
    private val cols = 2
    private val rows = 4

    fun draw(canvas: DocumentCanvas) {
        val slipWidth = (canvas.pageWidth - 2 * marginLeft - gap) / cols
        val slipHeight = (canvas.pageHeight - 2 * marginTop - gap) / rows
        val perPage = cols * rows

        if (data.designacoes.isEmpty()) {
            canvas.text(
                data.labels.vazio,
                marginLeft,
                canvas.pageHeight - 80f,
                TextStyle(12f, DocColors.Muted, FontStyle.ITALIC),
            )
            return
        }

        data.designacoes.chunked(perPage).forEachIndexed { pageIndex, pageSlips ->
            if (pageIndex > 0) canvas.newPage()
            pageSlips.forEachIndexed { i, slip ->
                val col = i % cols
                val row = i / cols
                val x = marginLeft + col * (slipWidth + gap)
                val yTop = canvas.pageHeight - marginTop - row * (slipHeight + gap)
                drawSlip(canvas, slip, data.labels, x, yTop, slipWidth, slipHeight)
            }
        }
    }

    private fun drawSlip(
        canvas: DocumentCanvas,
        slip: MidweekAssignmentPdf,
        labels: AssignmentPdfStrings,
        x: Float,
        yTop: Float,
        w: Float,
        h: Float,
    ) {
        canvas.strokeRect(x, yTop - h, w, h, DocColors.Border, 0.8f)

        val pad = 12f
        var y = yTop - 18f
        val titleStyle = TextStyle(9.5f, DocColors.Maroon, FontStyle.BOLD)
        canvas.textCentered(labels.title1, x, w, y, titleStyle)
        y -= 11f
        canvas.textCentered(labels.title2, x, w, y, titleStyle)
        y -= 20f

        val labelStyle = TextStyle(9f, DocColors.Ink, FontStyle.BOLD)
        val valueStyle = TextStyle(9f, DocColors.Ink)

        fun field(label: String, value: String?) {
            canvas.text(label, x + pad, y, labelStyle)
            val lw = canvas.measure(label, labelStyle)
            val disponivel = w - 2 * pad - lw - 4f
            canvas.text(canvas.fitText(value.orEmpty(), valueStyle, disponivel), x + pad + lw + 4f, y, valueStyle)
            canvas.line(x + pad + lw + 4f, y - 2f, x + w - pad, y - 2f, DocColors.Border, 0.5f)
            y -= 17f
        }
        field(labels.nombre, slip.nome)
        field(labels.ayudante, slip.ajudante)
        field(labels.fecha, slip.data)
        field(labels.intervencion, slip.numeroParte)

        canvas.text(labels.presentaraEn, x + pad, y, labelStyle)
        y -= 14f
        drawCheck(canvas, labels.salaPrincipal, !slip.salaAuxiliar, x + pad + 4f, y)
        y -= 13f
        drawCheck(canvas, labels.salaAuxiliar, slip.salaAuxiliar, x + pad + 4f, y)
        y -= 16f

        val notaStyle = TextStyle(6.8f, DocColors.Muted, FontStyle.ITALIC)
        canvas.wrapText(labels.nota, notaStyle, w - 2 * pad).take(3).forEach {
            canvas.text(it, x + pad, y, notaStyle)
            y -= 8.5f
        }
        canvas.text("S-89-S", x + pad, yTop - h + 8f, TextStyle(6.5f, DocColors.Muted))
    }

    private fun drawCheck(canvas: DocumentCanvas, label: String, checked: Boolean, x: Float, y: Float) {
        val box = 8f
        canvas.strokeRect(x, y - box + 1f, box, box, DocColors.Ink, 0.8f)
        if (checked) {
            canvas.fillRect(x + 1.6f, y - box + 2.6f, box - 3.2f, box - 3.2f, DocColors.Maroon)
        }
        canvas.text(label, x + box + 5f, y - box + 2f, TextStyle(8.5f, DocColors.Ink))
    }
}
