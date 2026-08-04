package com.example.sonntag.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sonntag.data.repos.SettingsRepository
import com.example.sonntag.data.sqldelight.Settings
import com.example.sonntag.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private const val DEFAULT_TITLE = "Programação do Salão"
private val DrawerWidth = 240.dp

data class NavItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val content: @Composable () -> Unit,
)

sealed interface NavEntry {
    data class Single(val item: NavItem) : NavEntry
    data class Group(
        val id: String,
        val label: String,
        val icon: ImageVector,
        val children: List<NavItem>,
    ) : NavEntry
}

private fun navEntries(onNavigate: (String) -> Unit): List<NavEntry> = listOf(
    NavEntry.Single(
        NavItem("dashboard", "Dashboard", Icons.Outlined.Dashboard) { DashboardScreen().Content(onNavigate) },
    ),
    NavEntry.Group(
        id = "programacoes",
        label = "Programações",
        icon = Icons.Outlined.CalendarMonth,
        children = listOf(
            NavItem("weekend", "Fim de semana", Icons.Outlined.Weekend) { WeekendProgramsScreen().Content() },
            NavItem("midweek", "Meio de semana", Icons.AutoMirrored.Outlined.MenuBook) { MidweekProgramsScreen().Content() },
        ),
    ),
    NavEntry.Single(
        NavItem("membros", "Membros", Icons.Outlined.People) { MembersScreen().Content() },
    ),
    NavEntry.Single(
        NavItem("av", "Áudio/vídeo e acomodadores", Icons.Outlined.Headphones) { AvAssignmentsScreen().Content() },
    ),
    NavEntry.Single(
        NavItem("limpeza", "Limpeza", Icons.Outlined.CleaningServices) { CleaningScreen().Content() },
    ),
    NavEntry.Single(
        NavItem("configuracoes", "Configurações", Icons.Outlined.Settings) { SettingsScreen().Content() },
    ),
)

private fun List<NavEntry>.allItems(): List<NavItem> = flatMap { entry ->
    when (entry) {
        is NavEntry.Single -> listOf(entry.item)
        is NavEntry.Group -> entry.children
    }
}

@Composable
fun MainNavigationShell() {
    val selectedIdState = remember { mutableStateOf("dashboard") }
    var selectedId by selectedIdState
    val entries = remember { navEntries { selectedIdState.value = it } }
    val allItems = remember(entries) { entries.allItems() }
    val settingsRepo = koinInject<SettingsRepository>()
    var settings by remember { mutableStateOf<Settings?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            val latest = withContext(Dispatchers.IO) { settingsRepo.getSettingsOnce() }
            if (latest != settings) settings = latest
            kotlinx.coroutines.delay(2000)
        }
    }

    val selected = allItems.firstOrNull { it.id == selectedId } ?: allItems.first()

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AppNavigationDrawer(
            entries = entries,
            selectedId = selected.id,
            onItemSelected = { selectedId = it },
            congregationName = settings?.nome?.ifBlank { null } ?: DEFAULT_TITLE,
            congregationSubtitle = settings?.endereco?.let { addressSummary(it) },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            selected.content()
        }
    }
}

private fun addressSummary(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val firstLine = trimmed.lineSequence().firstOrNull()?.trim().orEmpty()
    if (firstLine.isEmpty()) return null
    val firstSegment = firstLine.split(",", "-", "•").firstOrNull()?.trim().orEmpty()
    return firstSegment.ifEmpty { firstLine }
}

@Composable
private fun AppNavigationDrawer(
    entries: List<NavEntry>,
    selectedId: String,
    onItemSelected: (String) -> Unit,
    congregationName: String,
    congregationSubtitle: String?,
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(DrawerWidth),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            DrawerHeader(name = congregationName, subtitle = congregationSubtitle)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                entries.forEach { entry ->
                    when (entry) {
                        is NavEntry.Single -> DrawerItem(
                            label = entry.item.label,
                            icon = entry.item.icon,
                            selected = entry.item.id == selectedId,
                            onClick = { onItemSelected(entry.item.id) },
                        )
                        is NavEntry.Group -> DrawerGroup(
                            group = entry,
                            selectedId = selectedId,
                            onItemSelected = onItemSelected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader(name: String, subtitle: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Text(
            text = tr(name),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DrawerGroup(
    group: NavEntry.Group,
    selectedId: String,
    onItemSelected: (String) -> Unit,
) {
    val childSelected = group.children.any { it.id == selectedId }
    var expanded by remember { mutableStateOf(childSelected) }

    // Navegacao vinda de fora do menu (ex.: cards do painel) abre o grupo correspondente.
    LaunchedEffect(childSelected) {
        if (childSelected) expanded = true
    }

    DrawerGroupHeader(
        label = group.label,
        icon = group.icon,
        expanded = expanded,
        // Highlight the collapsed parent when one of its children is active.
        highlighted = childSelected && !expanded,
        onClick = { expanded = !expanded },
    )
    AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            group.children.forEach { child ->
                DrawerItem(
                    label = child.label,
                    icon = child.icon,
                    selected = child.id == selectedId,
                    onClick = { onItemSelected(child.id) },
                    startPadding = 24.dp,
                )
            }
        }
    }
}

@Composable
private fun DrawerGroupHeader(
    label: String,
    icon: ImageVector,
    expanded: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = if (highlighted) colors.primary.copy(alpha = 0.10f) else Color.Transparent
    val contentColor = if (highlighted) colors.primary else colors.onSurface
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(44.dp)
            .background(background, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(28.dp)
                .background(Color.Transparent, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = tr(label),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .rotate(rotation),
        )
    }
}

@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    startPadding: Dp = 12.dp,
) {
    val colors = MaterialTheme.colorScheme
    val background = if (selected) colors.primary.copy(alpha = 0.10f) else Color.Transparent
    val contentColor = if (selected) colors.primary else colors.onSurface
    val indicatorColor = if (selected) colors.primary else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(44.dp)
            .background(background, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(28.dp)
                .background(indicatorColor, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
        )
        Spacer(modifier = Modifier.width(startPadding))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = tr(label),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}
