package com.example.sonntag.data.sqldelight

object DatabaseFactory {
    fun createDatabase(): SonntagDatabase {
        val driver = createDatabaseDriver()
        return SonntagDatabase(driver)
    }
}

