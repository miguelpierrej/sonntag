package com.example.sonntag.i18n

import java.util.Locale

actual fun systemLanguageCode(): String? =
    Locale.getDefault().language?.takeIf { it.isNotBlank() }?.lowercase()
