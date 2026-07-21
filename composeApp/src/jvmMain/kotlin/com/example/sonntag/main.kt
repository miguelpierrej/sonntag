package com.example.sonntag

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.sonntag.data.repos.SettingsRepository
import com.example.sonntag.di.appModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

private const val DEFAULT_WINDOW_TITLE = "Programação do Salão"

fun main() {
    startKoin {
        modules(appModule)
    }

    application {
        val settingsRepo = remember { GlobalContext.get().get<SettingsRepository>() }
        var title by remember { mutableStateOf(DEFAULT_WINDOW_TITLE) }

        LaunchedEffect(Unit) {
            while (true) {
                val settings = withContext(Dispatchers.IO) { settingsRepo.getSettingsOnce() }
                val resolved = settings?.nome?.takeIf { it.isNotBlank() } ?: DEFAULT_WINDOW_TITLE
                if (resolved != title) title = resolved
                delay(2000)
            }
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = title,
        ) {
            App()
        }
    }
}
