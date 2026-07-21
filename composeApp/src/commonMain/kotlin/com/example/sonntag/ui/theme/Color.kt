package com.example.sonntag.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val NavyPrimary = Color(0xFF1E3A5F)
private val NavyPrimaryDark = Color(0xFF0F1F33)
private val NavyPrimaryContainer = Color(0xFFDCE7F5)
private val OnNavyPrimaryContainer = Color(0xFF0B1A2E)

private val SteelSecondary = Color(0xFF4A6FA5)
private val SteelSecondaryContainer = Color(0xFFE1EAF7)
private val OnSteelSecondaryContainer = Color(0xFF122340)

private val SlateTertiary = Color(0xFF5A6F8C)
private val SlateTertiaryContainer = Color(0xFFE3E8F0)
private val OnSlateTertiaryContainer = Color(0xFF1A2230)

private val NeutralBackground = Color(0xFFF5F6F8)
private val NeutralSurface = Color(0xFFFFFFFF)
private val NeutralSurfaceVariant = Color(0xFFE6E8EE)
private val NeutralOutline = Color(0xFFC2C6D0)
private val NeutralOutlineVariant = Color(0xFFD8DBE2)

private val TextPrimary = Color(0xFF1A1D29)
private val TextSecondary = Color(0xFF5C6275)

private val ErrorRed = Color(0xFFB3261E)
private val ErrorContainer = Color(0xFFF9DEDC)
private val OnErrorContainer = Color(0xFF410E0B)

val AppColorScheme: ColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = Color.White,
    primaryContainer = NavyPrimaryContainer,
    onPrimaryContainer = OnNavyPrimaryContainer,
    inversePrimary = Color(0xFFA9C2E6),

    secondary = SteelSecondary,
    onSecondary = Color.White,
    secondaryContainer = SteelSecondaryContainer,
    onSecondaryContainer = OnSteelSecondaryContainer,

    tertiary = SlateTertiary,
    onTertiary = Color.White,
    tertiaryContainer = SlateTertiaryContainer,
    onTertiaryContainer = OnSlateTertiaryContainer,

    background = NeutralBackground,
    onBackground = TextPrimary,

    surface = NeutralSurface,
    onSurface = TextPrimary,
    surfaceVariant = NeutralSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceTint = NavyPrimary,
    inverseSurface = NavyPrimaryDark,
    inverseOnSurface = Color.White,

    outline = NeutralOutline,
    outlineVariant = NeutralOutlineVariant,

    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,

    scrim = Color(0x99000000),
)
