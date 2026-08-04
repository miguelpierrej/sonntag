package com.example.sonntag.data.sqldelight

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.example.sonntag.platform.AndroidApp

/**
 * No Android nao existe banco legado: toda instalacao nasce com o schema atual, que
 * ja inclui as colunas de sincronizacao. Por isso aqui nao ha as migracoes manuais
 * que o desktop precisa carregar.
 */
actual fun createDatabaseDriver(): SqlDriver = AndroidSqliteDriver(
    schema = SonntagDatabase.Schema,
    context = AndroidApp.context,
    name = "sonntag.db",
)
