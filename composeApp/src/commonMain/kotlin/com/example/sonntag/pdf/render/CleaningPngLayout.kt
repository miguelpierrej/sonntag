package com.example.sonntag.pdf.render

import com.example.sonntag.pdf.CleaningSchedulePdfData

/** Cores exclusivas das imagens (o PDF nao pinta fundo nem cartoes). */
object PngColors {
    val PageBg = DocColor.hex(0xF5F6F8)
    val Card = DocColor.White
    val Navy = DocColor.hex(0x1E3A5F)
}

/** Largura fixa das imagens exportadas. */
const val PNG_WIDTH = 1080

/** Raio dos cantos dos cartoes. O Java2D recebe o arco, que e o dobro disso. */
const val CARD_RADIUS = 12f

/** Lado do icone no cabecalho das imagens. */
const val PNG_HEADER_ICON = 140

/**
 * Escala de limpeza em imagem: cartoes empilhados numa figura alta, pensada para
 * compartilhar em conversa. E um desenho diferente do PDF, nao a mesma folha.
 *
 * Escrito de cima para baixo sobre [TopDownCanvas].
 */
class CleaningPngLayout(
    private val data: CleaningSchedulePdfData,
    private val geradoEm: String,
    private val iconBytes: ByteArray?,
) {

    private val padding = 64
    private val titleStyle = TextStyle(52f, DocColors.Title, FontStyle.BOLD)
    private val subtitleStyle = TextStyle(38f, PngColors.Navy, FontStyle.BOLD)
    private val monthStyle = TextStyle(28f, DocColors.Muted)
    private val cardLabelStyle = TextStyle(24f, DocColors.Muted, FontStyle.BOLD)
    private val cardValueStyle = TextStyle(30f, DocColors.Title)
    private val cardValueItalic = TextStyle(30f, DocColors.Muted, FontStyle.ITALIC)
    private val periodStyle = TextStyle(34f, PngColors.Navy, FontStyle.BOLD)
    private val footerStyle = TextStyle(20f, DocColors.Muted, FontStyle.ITALIC)

    private val cardInnerPad = 32
    private val cardGap = 20
    private val cardLineGap = 12

    private val approxCardHeight = cardInnerPad * 2 +
        periodStyle.size.toInt() + cardLineGap +
        cardLabelStyle.size.toInt() + 6 + cardValueStyle.size.toInt() + cardLineGap +
        cardLabelStyle.size.toInt() + 6 + cardValueStyle.size.toInt()

    /** Altura necessaria para a figura, calculada antes de criar a superficie. */
    fun measureHeight(): Int {
        val cards = data.semanas
        val cardsHeight =
            if (cards.isEmpty()) 180 else cards.size * approxCardHeight + (cards.size - 1) * cardGap
        val headerHeight = padding + titleStyle.size.toInt() + 36 + subtitleStyle.size.toInt() +
            12 + monthStyle.size.toInt() + 36
        val footerHeight = padding + footerStyle.size.toInt()
        return (headerHeight + cardsHeight + footerHeight).coerceAtLeast(1350)
    }

    fun draw(canvas: TopDownCanvas) {
        val width = canvas.width.toInt()
        val height = canvas.height

        var y = padding + titleStyle.size.toInt()
        iconBytes?.let {
            canvas.image(
                it,
                (width - padding - PNG_HEADER_ICON).toFloat(),
                padding.toFloat(),
                PNG_HEADER_ICON.toFloat(),
                PNG_HEADER_ICON.toFloat(),
            )
        }
        canvas.text(data.congregacao, padding.toFloat(), y.toFloat(), titleStyle)

        y += 36
        y += subtitleStyle.size.toInt()
        canvas.text(data.labels.title, padding.toFloat(), y.toFloat(), subtitleStyle)

        y += 12 + monthStyle.size.toInt()
        canvas.text(data.mesLabel, padding.toFloat(), y.toFloat(), monthStyle)
        y += 28

        val cardX = padding.toFloat()
        val cardWidth = (width - padding * 2).toFloat()

        if (data.semanas.isEmpty()) {
            canvas.fillRect(cardX, y.toFloat(), cardWidth, 140f, PngColors.Card, CARD_RADIUS)
            canvas.text(data.labels.vazio, cardX + 32f, y + 80f, cardValueItalic)
            y += 140
        } else {
            data.semanas.forEach { row ->
                val cardTop = y
                canvas.fillRect(cardX, cardTop.toFloat(), cardWidth, approxCardHeight.toFloat(), PngColors.Card, CARD_RADIUS)
                canvas.strokeRect(
                    cardX, cardTop.toFloat(), cardWidth, approxCardHeight.toFloat(),
                    DocColors.Border, 1f, CARD_RADIUS,
                )

                var ty = cardTop + cardInnerPad + periodStyle.size.toInt()
                canvas.text(row.periodo, cardX + cardInnerPad, ty.toFloat(), periodStyle)

                ty += cardLineGap + cardLabelStyle.size.toInt()
                canvas.text(data.labels.diasReuniao, cardX + cardInnerPad, ty.toFloat(), cardLabelStyle)
                ty += 6 + cardValueStyle.size.toInt()
                canvas.text(row.diasReuniao, cardX + cardInnerPad, ty.toFloat(), cardValueStyle)

                ty += cardLineGap + cardLabelStyle.size.toInt()
                canvas.text(data.labels.grupoResponsavel, cardX + cardInnerPad, ty.toFloat(), cardLabelStyle)
                ty += 6 + cardValueStyle.size.toInt()
                val isPlaceholder = row.grupoResponsavel.isNullOrBlank()
                canvas.text(
                    row.grupoResponsavel?.takeIf { it.isNotBlank() } ?: data.labels.common.aDefinir,
                    cardX + cardInnerPad,
                    ty.toFloat(),
                    if (isPlaceholder) cardValueItalic else cardValueStyle,
                )

                y = cardTop + approxCardHeight + cardGap
            }
        }

        val footerText = data.labels.common.geradoEm(geradoEm)
        val fw = canvas.measure(footerText, footerStyle)
        canvas.text(footerText, width - padding - fw, height - padding, footerStyle)
    }
}
