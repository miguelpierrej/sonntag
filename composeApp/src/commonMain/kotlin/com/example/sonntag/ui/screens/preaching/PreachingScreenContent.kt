package com.example.sonntag.ui.screens.preaching

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.sonntag.data.repos.PreachingKind
import com.example.sonntag.i18n.tr
import com.example.sonntag.ui.components.ScreenScaffold

/** As tres visoes da tela de pregacao. */
private enum class PreachingTab { CARRITOS, PREDICACION, CADASTROS }

/**
 * Tela de pregacao: o calendario dos carrinhos, o da pregacao e os cadastros que os
 * dois consomem. Uma tela so, como o programa impresso, que tambem sai de uma vez.
 */
@Composable
fun PreachingScreenContent() {
    var aba by remember { mutableStateOf(PreachingTab.CARRITOS) }

    val subtitle = when (aba) {
        PreachingTab.CARRITOS -> tr("Programação dos carrinhos")
        PreachingTab.PREDICACION -> tr("Programação da pregação")
        PreachingTab.CADASTROS -> tr("Pontos e grupos de pregação")
    }

    ScreenScaffold(
        title = tr("Pregação"),
        subtitle = subtitle,
        leadingIcon = Icons.Outlined.Campaign,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TabButton(tr("Carrinhos"), aba == PreachingTab.CARRITOS) { aba = PreachingTab.CARRITOS }
            TabButton(tr("Pregação"), aba == PreachingTab.PREDICACION) { aba = PreachingTab.PREDICACION }
            TabButton(tr("Cadastros"), aba == PreachingTab.CADASTROS) { aba = PreachingTab.CADASTROS }
        }
        Spacer(modifier = Modifier.height(16.dp))

        when (aba) {
            PreachingTab.CARRITOS -> PreachingCalendarContent(PreachingKind.CARRITO)
            PreachingTab.PREDICACION -> PreachingCalendarContent(PreachingKind.PREDICACION)
            PreachingTab.CADASTROS -> PreachingSetupScreenContent()
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    TextButton(
        onClick = onClick,
        modifier = Modifier.background(bg, RoundedCornerShape(8.dp)),
    ) {
        Text(label, color = fg)
    }
}
