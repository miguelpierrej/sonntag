package com.example.sonntag.di

import com.example.sonntag.data.repos.AvAssignmentsRepository
import com.example.sonntag.data.repos.CleaningAssignmentsRepository
import com.example.sonntag.data.repos.CleaningGroupsRepository
import com.example.sonntag.data.repos.MeetingDaysRepository
import com.example.sonntag.data.repos.MeetingsRepository
import com.example.sonntag.data.repos.MembersRepository
import com.example.sonntag.data.repos.MidweekProgramsRepository
import com.example.sonntag.data.repos.PreferencesRepository
import com.example.sonntag.data.repos.SettingsRepository
import com.example.sonntag.data.repos.SyncPeersRepository
import com.example.sonntag.data.repos.TalkOutlinesRepository
import com.example.sonntag.i18n.LocaleController
import com.example.sonntag.data.repos.WeekendProgramsRepository
import com.example.sonntag.data.sqldelight.DatabaseFactory
import com.example.sonntag.domain.usecases.MeetingGenerator
import com.example.sonntag.imports.MwbImportService
import com.example.sonntag.imports.createMwbImportService
import com.example.sonntag.imports.S34ImportService
import com.example.sonntag.imports.createS34ImportService
import com.example.sonntag.sync.SyncCrypto
import com.example.sonntag.sync.SyncFileService
import com.example.sonntag.sync.createSyncFileService
import com.example.sonntag.sync.SyncService
import com.example.sonntag.sync.SyncStamp
import com.example.sonntag.sync.SyncStore
import com.example.sonntag.sync.createSyncCrypto
import com.example.sonntag.pdf.PdfExportService
import com.example.sonntag.pdf.createPdfExportService
import com.example.sonntag.ui.screens.av.AvAssignmentsViewModel
import com.example.sonntag.ui.screens.cleaning.CleaningViewModel
import com.example.sonntag.ui.screens.cleaninggroups.CleaningGroupsViewModel
import com.example.sonntag.ui.screens.dashboard.DashboardViewModel
import com.example.sonntag.ui.screens.datatransfer.DataTransferViewModel
import com.example.sonntag.ui.screens.members.MembersViewModel
import com.example.sonntag.ui.screens.midweek.MidweekProgramsViewModel
import com.example.sonntag.ui.screens.settings.SettingsViewModel
import com.example.sonntag.ui.screens.setup.InitialSetupViewModel
import com.example.sonntag.ui.screens.weekend.WeekendProgramsViewModel
import org.koin.dsl.module

val appModule = module {
    // Database
    single { DatabaseFactory.createDriver() }
    single { DatabaseFactory.createDatabase(get()) }

    // Sincronizacao (carimbo de quem/quando em toda escrita)
    single { SyncStamp(get()) }
    single { SyncStore(get()) }
    single<SyncCrypto> { createSyncCrypto() }
    single<SyncFileService> { createSyncFileService() }
    single { SyncService(get(), get(), get(), get()) }

    // Repositories
    single { SettingsRepository(get(), get()) }
    single { MeetingDaysRepository(get(), get()) }
    single { MembersRepository(get(), get()) }
    single { CleaningGroupsRepository(get(), get()) }
    single { MeetingsRepository(get(), get()) }
    single { WeekendProgramsRepository(get(), get()) }
    single { MidweekProgramsRepository(get(), get()) }
    single { CleaningAssignmentsRepository(get(), get()) }
    single { AvAssignmentsRepository(get(), get()) }
    single { PreferencesRepository(get()) }
    single { TalkOutlinesRepository(get()) }
    single { SyncPeersRepository(get()) }

    // i18n
    single { LocaleController(get()) }

    // Services
    single { MeetingGenerator(get(), get()) }
    single<PdfExportService> { createPdfExportService() }
    single<MwbImportService> { createMwbImportService() }
    single<S34ImportService> { createS34ImportService() }

    // ViewModels
    single { InitialSetupViewModel(get(), get(), get()) }
    single { MembersViewModel(get()) }
    single { CleaningGroupsViewModel(get()) }
    single { DataTransferViewModel(get(), get(), get(), get(), get(), get()) }
    single { DashboardViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    single { SettingsViewModel(get(), get(), get(), get()) }
    single { WeekendProgramsViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { MidweekProgramsViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    single { CleaningViewModel(get(), get(), get(), get(), get(), get(), get()) }
    single { AvAssignmentsViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}

