package com.example.sonntag.tools

import com.example.sonntag.ui.icon.DEFAULT_MEETING_DAYS
import com.example.sonntag.ui.icon.renderAppIcon
import java.io.File
import javax.imageio.ImageIO

/**
 * Exporta o PNG mestre do icone (mesmo desenho usado na janela em execucao).
 * Rode via `./gradlew :composeApp:exportAppIcon`; o .ico/.icns sao derivados dele.
 *
 * Argumentos: [diretorio] [dias separados por virgula, 1=segunda ... 7=domingo].
 */
fun main(args: Array<String>) {
    val outDir = File(args.firstOrNull() ?: "icons").apply { mkdirs() }
    val days = args.getOrNull(1)
        ?.split(",")
        ?.mapNotNull { it.trim().toIntOrNull() }
        ?.toSet()
        ?.takeIf { it.isNotEmpty() }
        ?: DEFAULT_MEETING_DAYS

    val target = File(outDir, "app-icon.png")
    ImageIO.write(renderAppIcon(1024, days), "png", target)
    println("Icone exportado em ${target.absolutePath} (dias: ${days.sorted()})")
}
