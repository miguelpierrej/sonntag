package com.example.sonntag.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/** Traduz [pt] usando o idioma atual da composicao. Uso: Text(tr("Membros")). */
@Composable
@ReadOnlyComposable
fun tr(pt: String): String = LocalT.current(pt)

@Composable
@ReadOnlyComposable
fun tr(pt: String, vararg args: Any?): String = LocalT.current(pt, *args)
