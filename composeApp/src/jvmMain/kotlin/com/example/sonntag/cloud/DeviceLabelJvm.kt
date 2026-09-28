package com.example.sonntag.cloud

import java.net.InetAddress

actual fun deviceLabel(): String =
    System.getenv("COMPUTERNAME")
        ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
        ?: System.getProperty("os.name").orEmpty()
