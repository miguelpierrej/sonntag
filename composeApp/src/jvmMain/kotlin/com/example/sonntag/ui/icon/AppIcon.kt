package com.example.sonntag.ui.icon

import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/**
 * Dias usados quando ainda nao ha reunioes configuradas — tambem e o que vai no
 * icone do instalador, que por ser um arquivo estatico nao conhece a configuracao.
 */
val DEFAULT_MEETING_DAYS: Set<Int> = setOf(4, 7)

private val NavyTop = Color(0x2A, 0x4D, 0x7C)
private val NavyBottom = Color(0x12, 0x24, 0x3E)
private val Steel = Color(0x4A, 0x6F, 0xA5)
private val Navy = Color(0x1E, 0x3A, 0x5F)
private val Muted = Color(0xDC, 0xE0, 0xE7)

private const val BAR_COUNT = 7
private const val BARS_LEFT = 0.255
private const val BAR_PITCH = 0.073
private const val BARS_MIDDLE = 0.618

/** Dia de reuniao: barra alta e escura. Demais dias: marca curta e apagada. */
private const val ACTIVE_WIDTH = 0.058
private const val ACTIVE_HEIGHT = 0.17
private const val IDLE_WIDTH = 0.040
private const val IDLE_HEIGHT = 0.085

/**
 * Icone do app: calendario com a faixa dos sete dias da semana, onde os dias em
 * [meetingDays] (1=segunda ... 7=domingo) aparecem destacados.
 */
fun renderAppIcon(size: Int, meetingDays: Set<Int> = DEFAULT_MEETING_DAYS): BufferedImage {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    val s = size.toFloat()
    fun p(v: Double): Float = (v * s).toFloat()

    // Fundo: quadrado arredondado com gradiente vertical.
    g.paint = GradientPaint(0f, 0f, NavyTop, 0f, s, NavyBottom)
    g.fill(RoundRectangle2D.Float(0f, 0f, s, s, p(0.44), p(0.44)))

    // Corpo do calendario.
    val body = RoundRectangle2D.Float(p(0.21), p(0.29), p(0.58), p(0.51), p(0.11), p(0.11))
    g.paint = Color.WHITE
    g.fill(body)

    // Faixa do cabecalho, recortada pelo corpo para herdar os cantos arredondados.
    val previousClip = g.clip
    g.clip(body)
    g.paint = Steel
    g.fill(Rectangle2D.Float(p(0.21), p(0.29), p(0.58), p(0.13)))
    g.clip = previousClip

    // Argolas.
    val ringWidth = p(0.048)
    listOf(0.36, 0.64).forEach { cx ->
        g.paint = Color.WHITE
        g.fill(
            RoundRectangle2D.Float(
                p(cx) - ringWidth / 2f, p(0.205), ringWidth, p(0.15), ringWidth, ringWidth,
            ),
        )
    }

    // Faixa de dias: os configurados em navy, os demais apagados.
    repeat(BAR_COUNT) { index ->
        val active = (index + 1) in meetingDays
        val width = if (active) ACTIVE_WIDTH else IDLE_WIDTH
        val height = if (active) ACTIVE_HEIGHT else IDLE_HEIGHT
        val centerX = BARS_LEFT + index * BAR_PITCH + BAR_PITCH / 2
        g.paint = if (active) Navy else Muted
        g.fill(
            RoundRectangle2D.Float(
                p(centerX - width / 2), p(BARS_MIDDLE - height / 2),
                p(width), p(height), p(width), p(width),
            ),
        )
    }

    g.dispose()
    return image
}
