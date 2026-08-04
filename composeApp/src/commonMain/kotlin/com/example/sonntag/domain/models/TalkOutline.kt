package com.example.sonntag.domain.models

/** Um bosquejo de discurso publico do S-34. */
data class TalkOutline(val numero: Int, val titulo: String) {
    /** "12. Titulo do discurso" — formato mostrado na lista e salvo na programacao. */
    val display: String get() = "$numero. $titulo"
}
