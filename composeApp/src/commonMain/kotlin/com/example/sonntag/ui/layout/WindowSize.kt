package com.example.sonntag.ui.layout

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Faixa de largura disponivel, no espirito das window size classes do Material 3.
 * Medimos com BoxWithConstraints em vez de perguntar ao sistema: assim a janela
 * redimensionada no desktop se comporta como o celular, o que torna o layout
 * verificavel sem emulador.
 */
enum class WindowSize {
    /** Celular em pe (S23 ~ 411dp). Uma coisa de cada vez na tela. */
    COMPACT,

    /** Tablet em pe ou janela estreita. Cabe navegacao lateral enxuta. */
    MEDIUM,

    /** Tablet deitado e desktop. Cabe menu + lista + editor lado a lado. */
    EXPANDED;

    val isCompact: Boolean get() = this == COMPACT

    /** Duas colunas lado a lado so fazem sentido a partir daqui. */
    val supportsSideBySide: Boolean get() = this == EXPANDED

    companion object {
        fun fromWidth(width: Dp): WindowSize = when {
            width < 600.dp -> COMPACT
            width < 900.dp -> MEDIUM
            else -> EXPANDED
        }
    }
}

val LocalWindowSize = staticCompositionLocalOf { WindowSize.EXPANDED }
