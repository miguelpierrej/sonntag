package com.example.sonntag

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.GraphicsEnvironment
import com.example.sonntag.data.repos.MeetingDaysRepository
import com.example.sonntag.data.repos.SettingsRepository
import com.example.sonntag.di.appModule
import com.example.sonntag.i18n.LocaleController
import com.example.sonntag.ui.icon.DEFAULT_MEETING_DAYS
import com.example.sonntag.ui.icon.renderAppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

private const val DEFAULT_WINDOW_TITLE = "Programação do Salão"

/** Janela grande (85% da área útil da tela), mas sem ocupar a tela inteira. */
private fun defaultWindowSize(): DpSize {
    val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val device = env.defaultScreenDevice.defaultConfiguration
    val bounds = env.maximumWindowBounds
    val scale = device.defaultTransform.scaleX.takeIf { it > 0.0 } ?: 1.0

    val availableWidth = bounds.width / scale
    val availableHeight = bounds.height / scale

    val width = (availableWidth * 0.85).coerceIn(1100.0, 1600.0).coerceAtMost(availableWidth)
    val height = (availableHeight * 0.85).coerceIn(700.0, 1000.0).coerceAtMost(availableHeight)

    return DpSize(width.toFloat().dp, height.toFloat().dp)
}

fun main() {
    startKoin {
        modules(appModule)
    }

    application {
        val settingsRepo = remember { GlobalContext.get().get<SettingsRepository>() }
        val meetingDaysRepo = remember { GlobalContext.get().get<MeetingDaysRepository>() }
        val localeController = remember { GlobalContext.get().get<LocaleController>() }
        var title by remember { mutableStateOf(localeController.translator(DEFAULT_WINDOW_TITLE)) }
        var meetingDays by remember { mutableStateOf(DEFAULT_MEETING_DAYS) }

        LaunchedEffect(Unit) {
            while (true) {
                val settings = withContext(Dispatchers.IO) { settingsRepo.getSettingsOnce() }
                val resolved = settings?.nome?.takeIf { it.isNotBlank() }
                    ?: localeController.translator(DEFAULT_WINDOW_TITLE)
                if (resolved != title) title = resolved

                val days = withContext(Dispatchers.IO) {
                    meetingDaysRepo.getAllOnce().map { it.dia_semana.toInt() }.toSet()
                }
                val resolvedDays = days.ifEmpty { DEFAULT_MEETING_DAYS }
                if (resolvedDays != meetingDays) meetingDays = resolvedDays

                delay(2000)
            }
        }

        // Icone da janela/barra de tarefas destaca os dias de reuniao configurados.
        val icon = remember(meetingDays) {
            BitmapPainter(renderAppIcon(256, meetingDays).toComposeImageBitmap())
        }

        val windowState = rememberWindowState(
            placement = WindowPlacement.Maximized,
            size = remember { defaultWindowSize() },
            position = WindowPosition(Alignment.Center),
        )

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = title,
            icon = icon,
        ) {
            App()
        }
    }
}
