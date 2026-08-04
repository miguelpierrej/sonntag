package com.example.sonntag.i18n

/** Idiomas suportados pelo app. */
enum class AppLanguage(val code: String, val label: String) {
    ES("es", "Español"),
    PT_BR("pt", "Português (BR)");

    companion object {
        val DEFAULT = ES
        fun fromCode(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: DEFAULT

        /**
         * Idioma sugerido pelo sistema operacional. Aceita variantes regionais
         * ("pt-BR", "es_AR"); idiomas nao suportados caem em [DEFAULT].
         */
        fun fromSystem(code: String? = systemLanguageCode()): AppLanguage {
            val language = code?.lowercase()?.substringBefore('-')?.substringBefore('_')
            return entries.firstOrNull { it.code == language } ?: DEFAULT
        }
    }
}
