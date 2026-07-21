package com.example.sonntag.ui.screens.main

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import com.example.sonntag.ui.screens.cleaning.CleaningScreenContent
import com.example.sonntag.ui.screens.dashboard.DashboardScreenContent
import com.example.sonntag.ui.screens.members.MembersScreenContent
import com.example.sonntag.ui.screens.midweek.MidweekProgramsScreenContent
import com.example.sonntag.ui.screens.settings.SettingsScreenContent
import com.example.sonntag.ui.screens.weekend.WeekendProgramsScreenContent

data class DashboardScreen(val name: String = "Dashboard") : Screen {
    @Composable
    override fun Content() {
        DashboardScreenContent()
    }
}

data class WeekendProgramsScreen(val name: String = "Programações - Fim de Semana") : Screen {
    @Composable
    override fun Content() {
        WeekendProgramsScreenContent()
    }
}

data class MidweekProgramsScreen(val name: String = "Programações - Meio de Semana") : Screen {
    @Composable
    override fun Content() {
        MidweekProgramsScreenContent()
    }
}

data class MembersScreen(val name: String = "Membros") : Screen {
    @Composable
    override fun Content() {
        MembersScreenContent()
    }
}

data class CleaningScreen(val name: String = "Limpeza") : Screen {
    @Composable
    override fun Content() {
        CleaningScreenContent()
    }
}

data class SettingsScreen(val name: String = "Configurações") : Screen {
    @Composable
    override fun Content() {
        SettingsScreenContent()
    }
}
