package com.example.sonntag

import android.app.Application
import com.example.sonntag.di.appModule
import com.example.sonntag.platform.AndroidApp
import org.koin.core.context.startKoin

class SonntagApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // O contexto e necessario para abrir o banco e os seletores de arquivo,
        // e as funcoes `actual` sao top-level (sem injecao).
        AndroidApp.context = this
        startKoin { modules(appModule) }
    }
}
