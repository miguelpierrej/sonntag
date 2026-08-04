package com.example.sonntag.sync

import java.util.UUID

actual fun newUuid(): String = UUID.randomUUID().toString()
