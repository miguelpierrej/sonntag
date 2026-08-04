package com.example.sonntag.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.example.sonntag.App
import com.example.sonntag.di.appModule
import com.example.sonntag.i18n.LocalT
import com.example.sonntag.i18n.LocaleController
import com.example.sonntag.i18n.Translator
import com.example.sonntag.ui.layout.LocalWindowSize
import com.example.sonntag.ui.layout.WindowSize
import com.example.sonntag.ui.screens.midweek.MidweekProgramsScreenContent
import com.example.sonntag.ui.screens.weekend.WeekendProgramsScreenContent
import com.example.sonntag.ui.theme.AppTheme
import org.jetbrains.skia.EncodedImageFormat
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import java.io.File

/**
 * Renderiza a UI fora da tela em larguras de celular e tablet. Permite conferir o
 * layout responsivo sem emulador — o mesmo codigo que roda no Android.
 *
 * Uso: renderScreens <diretorio> [painel|fim-de-semana|meio-de-semana]
 */
fun main(args: Array<String>) {
    startKoin { modules(appModule) }
    val outDir = File(args[0]).apply { mkdirs() }
    val tela = args.getOrElse(1) { "painel" }

    val alvos = listOf(
        Triple("celular-s23", 411, 891),
        Triple("tablet-retrato", 720, 1150),
        Triple("tablet-paisagem", 1150, 720),
    )

    alvos.forEach { (nome, larguraDp, alturaDp) ->
        val scene = ImageComposeScene(
            width = (larguraDp * 2f).toInt(),
            height = (alturaDp * 2f).toInt(),
            density = Density(2f),
        ) { Conteudo(tela, larguraDp) }
        repeat(30) { scene.render(it * 100_000_000L) }
        // Toque opcional (em dp) para chegar a telas de segundo nivel, como o editor.
        System.getenv("TAP")?.split(",")?.let { (x, y) ->
            val ponto = Offset(x.trim().toFloat() * 2f, y.trim().toFloat() * 2f)
            scene.sendPointerEvent(PointerEventType.Press, ponto)
            scene.sendPointerEvent(PointerEventType.Release, ponto)
            repeat(20) { scene.render(3_000_000_000L + it * 100_000_000L) }
        }
        val image = scene.render(6_000_000_000L)
        File(outDir, "$tela-$nome${if (System.getenv("TAP") != null) "-detalhe" else ""}.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        println("renderizado: $tela-$nome (${larguraDp}x${alturaDp} dp)")
    }
}

@Composable
private fun Conteudo(tela: String, larguraDp: Int) {
    if (tela == "painel") {
        App()
        return
    }
    // As telas internas sao desenhadas direto, com os CompositionLocals que o
    // AppRoot normalmente fornece.
    val locale = GlobalContext.get().get<LocaleController>()
    CompositionLocalProvider(
        LocalT provides Translator(locale.current),
        LocalWindowSize provides WindowSize.fromWidth(androidx.compose.ui.unit.Dp(larguraDp.toFloat())),
    ) {
        AppTheme {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                when (tela) {
                    "fim-de-semana" -> WeekendProgramsScreenContent()
                    "meio-de-semana" -> MidweekProgramsScreenContent()
                }
            }
        }
    }
}
