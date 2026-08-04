package com.example.sonntag.ui.util

import com.example.sonntag.i18n.Translator

private val ACCENTS = mapOf(
    'á' to 'a', 'à' to 'a', 'â' to 'a', 'ã' to 'a',
    'é' to 'e', 'ê' to 'e',
    'í' to 'i',
    'ó' to 'o', 'ô' to 'o', 'õ' to 'o',
    'ú' to 'u',
    'ç' to 'c',
    'ñ' to 'n',
)

/**
 * Nome do mes no idioma atual, em minusculas e sem acentos, para compor nomes
 * de arquivo exportado ("marco", "julho", "julio").
 */
fun slugMonth(translator: Translator, month: Int): String =
    translator.monthName(month).lowercase().map { ACCENTS[it] ?: it }.joinToString("")
