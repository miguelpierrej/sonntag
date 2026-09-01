package com.example.sonntag.ui.screens.dashboard

import com.example.sonntag.i18n.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Pending
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sonntag.ui.components.ScreenScaffold
import org.koin.compose.koinInject

/** Quantidade maxima de reunioes pendentes listadas dentro do card. */
private const val MAX_PENDING_PREVIEW = 3

/** Abaixo disso o card fica estreito demais e o titulo quebra letra a letra. */
private val MIN_CARD_WIDTH = 240.dp

/** Quantos eventos futuros cabem no card. */
private const val MAX_EVENTS_PREVIEW = 3

@Composable
fun DashboardScreenContent(onNavigate: (String) -> Unit = {}) {
    val viewModel = koinInject<DashboardViewModel>()
    val state by viewModel.uiState.collectAsState()

    // Recarrega sempre que o painel volta a ser exibido.
    LaunchedEffect(Unit) { viewModel.load() }

    ScreenScaffold(
        title = tr("Dashboard"),
        subtitle = tr("Visão geral da congregação"),
    ) {
        // Medimos a largura que sobra para o conteudo, nao a da janela: no tablet em
        // pe a gaveta ja consumiu 240dp e tres colunas nao cabem mais.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // O card de eventos so existe quando ha eventos, e por isso entra na conta da
        // largura minima: com quatro cards a linha precisa de mais espaco.
        val cardCount = if (state.upcomingEvents.isEmpty()) 3 else 4
        ResponsiveCards(stacked = maxWidth < MIN_CARD_WIDTH * cardCount) { cardModifier ->
            DashboardCard(
                title = tr("Próxima reunião"),
                icon = Icons.Outlined.CalendarMonth,
                modifier = cardModifier,
                onClick = state.nextMeeting?.let { next -> { onNavigate(next.navTarget) } },
            ) {
                NextMeetingContent(state.nextMeeting, state.isLoading)
            }
            DashboardCard(
                title = tr("Limpeza da semana"),
                icon = Icons.Outlined.CleaningServices,
                modifier = cardModifier,
                onClick = { onNavigate(NAV_CLEANING) },
            ) {
                CleaningContent(state.cleaning, state.isLoading)
            }
            DashboardCard(
                title = tr("Programações pendentes"),
                icon = Icons.Outlined.Pending,
                modifier = cardModifier,
                onClick = state.pendingItems.firstOrNull()?.let { first -> { onNavigate(first.navTarget) } },
            ) {
                PendingContent(state.pendingItems, state.pendingWindowDays, state.isLoading)
            }
            if (state.upcomingEvents.isNotEmpty()) {
                DashboardCard(
                    title = tr("Próximos eventos"),
                    icon = Icons.Outlined.Event,
                    modifier = cardModifier,
                ) {
                    UpcomingEventsContent(state.upcomingEvents)
                }
            }
        }
        }
    }
}

/**
 * Linha de cards no desktop, coluna rolavel no celular. O modifier do card vem de
 * dentro porque `weight` so existe no escopo da Row.
 */
@Composable
private fun ResponsiveCards(stacked: Boolean, content: @Composable (Modifier) -> Unit) {
    if (stacked) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content(Modifier.fillMaxWidth()) }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) { content(Modifier.weight(1f)) }
    }
}

@Composable
private fun NextMeetingContent(next: NextMeetingInfo?, isLoading: Boolean) {
    if (next == null) {
        PlaceholderText(if (isLoading) tr("Carregando...") else tr("Nenhuma reunião agendada"))
        return
    }

    Badge(text = next.relativeLabel)
    Spacer(modifier = Modifier.height(10.dp))
    CardHighlight(next.dateLabel)
    CardCaption("${next.time} • ${next.typeLabel}")
    if (next.details.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        next.details.forEach { DetailRow(label = it.label, value = it.value) }
    }
}

@Composable
private fun CleaningContent(cleaning: CleaningWeekInfo?, isLoading: Boolean) {
    if (cleaning == null) {
        PlaceholderText(if (isLoading) tr("Carregando...") else tr("Sem grupo atribuído"))
        return
    }

    when {
        cleaning.eventLabel != null -> CardHighlight(cleaning.eventLabel, muted = true)
        cleaning.groupName != null -> CardHighlight(cleaning.groupName)
        else -> CardHighlight(tr("Sem grupo atribuído"), muted = true)
    }
    CardCaption(cleaning.periodText)
    Spacer(modifier = Modifier.height(12.dp))
    DetailRow(
        label = tr("Próxima semana"),
        value = cleaning.nextWeekGroupName ?: tr("Sem grupo atribuído"),
    )
}

@Composable
private fun PendingContent(items: List<PendingProgramItem>, windowDays: Int, isLoading: Boolean) {
    if (isLoading) {
        PlaceholderText(tr("Carregando..."))
        return
    }
    if (items.isEmpty()) {
        CardHighlight(tr("Tudo em dia"))
        CardCaption(tr("Próximos {0} dias", windowDays))
        return
    }

    CardHighlight(items.size.toString())
    CardCaption(
        if (items.size == 1) tr("reunião sem programação completa")
        else tr("reuniões sem programação completa"),
    )
    Spacer(modifier = Modifier.height(12.dp))
    items.take(MAX_PENDING_PREVIEW).forEach {
        DetailRow(label = it.typeLabel, value = it.dateLabel)
    }
    if (items.size > MAX_PENDING_PREVIEW) {
        Text(
            text = tr("+{0} outras", items.size - MAX_PENDING_PREVIEW),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UpcomingEventsContent(items: List<UpcomingEventItem>) {
    val first = items.first()
    Badge(text = first.relativeLabel)
    Spacer(modifier = Modifier.height(10.dp))
    CardHighlight(first.nome)
    CardCaption("${first.dateLabel} • ${first.typeLabel}")
    val rest = items.drop(1).take(MAX_EVENTS_PREVIEW - 1)
    if (rest.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        rest.forEach { DetailRow(label = it.typeLabel, value = it.dateLabel) }
    }
}

@Composable
private fun CardHighlight(text: String, muted: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CardCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PlaceholderText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun DashboardCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .heightIn(min = 160.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}
