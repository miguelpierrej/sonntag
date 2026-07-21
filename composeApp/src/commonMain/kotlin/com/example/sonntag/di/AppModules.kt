package com.example.sonntag.di

import com.example.sonntag.data.repos.CleaningAssignmentsRepository
import com.example.sonntag.data.repos.CleaningGroupsRepository
import com.example.sonntag.data.repos.MeetingDaysRepository
import com.example.sonntag.data.repos.MeetingsRepository
import com.example.sonntag.data.repos.MembersRepository
import com.example.sonntag.data.repos.MidweekProgramsRepository
import com.example.sonntag.data.repos.SettingsRepository
import com.example.sonntag.data.repos.WeekendProgramsRepository
import com.example.sonntag.data.sqldelight.DatabaseFactory
import com.example.sonntag.domain.usecases.MeetingGenerator
import com.example.sonntag.imports.MwbImportService
import com.example.sonntag.imports.createMwbImportService
import com.example.sonntag.pdf.PdfExportService
import com.example.sonntag.pdf.createPdfExportService
import com.example.sonntag.ui.screens.cleaning.CleaningViewModel
import com.example.sonntag.ui.screens.cleaninggroups.CleaningGroupsViewModel
import com.example.sonntag.ui.screens.members.MembersViewModel
import com.example.sonntag.ui.screens.midweek.MidweekProgramsViewModel
import com.example.sonntag.ui.screens.settings.SettingsViewModel
import com.example.sonntag.ui.screens.setup.InitialSetupViewModel
import com.example.sonntag.ui.screens.weekend.WeekendProgramsViewModel
import org.koin.dsl.module

val appModule = module {
    // Database
    single { DatabaseFactory.createDatabase() }

    // Repositories
    single { SettingsRepository(get()) }
    single { MeetingDaysRepository(get()) }
    single { MembersRepository(get()) }
    single { CleaningGroupsRepository(get()) }
    single { MeetingsRepository(get()) }
    single { WeekendProgramsRepository(get()) }
    single { MidweekProgramsRepository(get()) }
    single { CleaningAssignmentsRepository(get()) }

    // Services
    single { MeetingGenerator(get(), get()) }
    single<PdfExportService> { createPdfExportService() }
    single<MwbImportService> { createMwbImportService() }

    // ViewModels
    single { InitialSetupViewModel(get(), get()) }
    single { MembersViewModel(get()) }
    single { CleaningGroupsViewModel(get()) }
    single { SettingsViewModel(get(), get(), get()) }
    single { WeekendProgramsViewModel(get(), get(), get(), get(), get(), get()) }
    single { MidweekProgramsViewModel(get(), get(), get(), get(), get(), get(), get()) }
    single { CleaningViewModel(get(), get(), get(), get(), get(), get()) }
}

