package com.example.sonntag.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.sonntag.data.repos.SettingsRepository
import com.example.sonntag.domain.usecases.MeetingGenerator
import com.example.sonntag.i18n.LocalT
import com.example.sonntag.i18n.LocaleController
import com.example.sonntag.i18n.Translator
import com.example.sonntag.ui.screens.main.MainNavigationShell
import com.example.sonntag.ui.screens.setup.InitialSetupScreen
import com.example.sonntag.ui.theme.AppTheme
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun AppRoot() {
    val settingsRepo = koinInject<SettingsRepository>()
    val meetingGenerator = koinInject<MeetingGenerator>()
    val localeController = koinInject<LocaleController>()
    val language by localeController.language.collectAsState()
    val needsSetup = remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            settingsRepo.getSettings().collect { settings ->
                needsSetup.value = settings == null
                if (settings != null) {
                    meetingGenerator.generateNext12Months()
                }
            }
        }
    }

    CompositionLocalProvider(LocalT provides Translator(language)) {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (needsSetup.value) {
                true -> InitialSetupScreen {
                    needsSetup.value = false
                }

                false -> MainNavigationShell()

                null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
    }
}
