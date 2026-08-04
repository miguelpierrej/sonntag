package com.example.sonntag.data.sqldelight

import app.cash.sqldelight.db.SqlDriver

object DatabaseFactory {
    /** Driver e banco sao expostos separados: a sincronizacao precisa do driver
     *  para ler e gravar tabelas de forma generica, na mesma conexao. */
    fun createDriver(): SqlDriver = createDatabaseDriver()

    fun createDatabase(driver: SqlDriver): SonntagDatabase = SonntagDatabase(driver)

    fun createDatabase(): SonntagDatabase = createDatabase(createDriver())
}
