package com.example.sonntag.cloud

import android.os.Build

actual fun deviceLabel(): String {
    val fabricante = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
    val modelo = Build.MODEL.orEmpty()
    // Alguns fabricantes ja repetem a marca no modelo ("Samsung SM-…" nao, "Pixel" sim).
    return if (modelo.startsWith(fabricante, ignoreCase = true)) modelo else "$fabricante $modelo".trim()
}
