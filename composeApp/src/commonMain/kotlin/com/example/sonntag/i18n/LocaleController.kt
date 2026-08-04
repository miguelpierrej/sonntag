package com.example.sonntag.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.sonntag.data.repos.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREF_IDIOMA = "idioma"

/**
 * Fonte de verdade do idioma atual. Usado tanto pela UI (via [LocalT]) quanto
 * pelos ViewModels (que nao tem contexto de composicao, entao leem [translator]).
 */
class LocaleController(private val prefs: PreferencesRepository) {
    // Sem escolha salva, comeca no idioma do computador; a partir dai vale o que
    // o usuario selecionou em Configuracoes.
    private val _language = MutableStateFlow(
        prefs.get(PREF_IDIOMA)?.let { AppLanguage.fromCode(it) } ?: AppLanguage.fromSystem(),
    )
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    val current: AppLanguage get() = _language.value
    val translator: Translator get() = Translator(current)

    fun setLanguage(lang: AppLanguage) {
        prefs.set(PREF_IDIOMA, lang.code)
        _language.value = lang
    }
}

/** Tradutor do idioma atual, disponivel para qualquer Composable. */
val LocalT = staticCompositionLocalOf { Translator(AppLanguage.DEFAULT) }
