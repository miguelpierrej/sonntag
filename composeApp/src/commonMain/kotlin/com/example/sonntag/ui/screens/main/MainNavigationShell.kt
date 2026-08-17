package com.example.sonntag.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sonntag.data.repos.SettingsRepository
import com.example.sonntag.sync.IncomingPackage
import com.example.sonntag.data.sqldelight.Settings
import com.example.sonntag.i18n.tr
import com.example.sonntag.ui.layout.LocalWindowSize
import com.example.sonntag.ui.layout.WindowSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private const val DEFAULT_TITLE = "Programação do Salão"
private val DrawerWidth = 240.dp
private val RailWidth = 96.dp

data class NavItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    /** Usado no rail, onde nao cabe o rotulo inteiro. */
    val shortLabel: String = label,
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
            NavItem("pregacao", "Pregação", Icons.Outlined.Campaign) { PreachingScreen().Content() },
        ),
    ),
    NavEntry.Single(
        NavItem("membros", "Membros", Icons.Outlined.People) { MembersScreen().Content() },
    ),
    NavEntry.Single(
        NavItem("av", "Áudio/vídeo e acomodadores", Icons.Outlined.Headphones, "Áudio/vídeo") {
            AvAssignmentsScreen().Content()
        },
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

    // Um pacote aberto por fora do app (toque num .sonntag) cai na tela de Dados.
    LaunchedEffect(IncomingPackage.bytes) {
        if (IncomingPackage.bytes != null) selectedId = "configuracoes"
    }

    val selected = allItems.firstOrNull { it.id == selectedId } ?: allItems.first()
    val congregationName = settings?.nome?.ifBlank { null } ?: DEFAULT_TITLE
    val congregationSubtitle = settings?.endereco?.let { addressSummary(it) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val windowSize = WindowSize.fromWidth(maxWidth)

        CompositionLocalProvider(LocalWindowSize provides windowSize) {
            if (windowSize.isCompact) {
                // Num celular a gaveta fixa comeria mais da metade da largura:
                // vira gaveta sobreposta, aberta pelo botao da barra superior.
                CompactShell(
                    entries = entries,
                    selected = selected,
                    onItemSelected = { selectedId = it },
                    congregationName = congregationName,
                    congregationSubtitle = congregationSubtitle,
                )
            } else if (windowSize == WindowSize.MEDIUM) {
                // Tablet em pe: a gaveta de 240dp comeria um terco da tela. Vira um
                // rail de icones, devolvendo a largura para o conteudo.
                Row(modifier = Modifier.fillMaxSize()) {
                    AppNavigationRail(
                        items = allItems,
                        selectedId = selected.id,
                        onItemSelected = { selectedId = it },
                    )
                    ContentArea { selected.content() }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    AppNavigationDrawer(
                        entries = entries,
                        selectedId = selected.id,
                        onItemSelected = { selectedId = it },
                        congregationName = congregationName,
                        congregationSubtitle = congregationSubtitle,
                    )
                    ContentArea { selected.content() }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactShell(
    entries: List<NavEntry>,
    selected: NavItem,
    onItemSelected: (String) -> Unit,
    congregationName: String,
    congregationSubtitle: String?,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                AppNavigationDrawer(
                    entries = entries,
                    selectedId = selected.id,
                    onItemSelected = {
                        onItemSelected(it)
                        scope.launch { drawerState.close() }
                    },
                    congregationName = congregationName,
                    congregationSubtitle = congregationSubtitle,
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(),
                )
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(congregationName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Outlined.Menu, contentDescription = tr("Menu"))
                    }
                },
            )
            ContentArea { selected.content() }
        }
    }
}

/** Rail de icones: o menu inteiro em ~80dp, sem os agrupamentos da gaveta. */
@Composable
private fun AppNavigationRail(
    items: List<NavItem>,
    selectedId: String,
    onItemSelected: (String) -> Unit,
) {
    // 80dp (o padrao) corta rotulos como "Fim de semana"; com 96 eles quebram em
    // duas linhas e ainda sobra bem mais espaco que a gaveta de 240dp.
    NavigationRail(
        modifier = Modifier.width(RailWidth),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        items.forEach { item ->
            NavigationRailItem(
                selected = item.id == selectedId,
                onClick = { onItemSelected(item.id) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = {
                    Text(
                        text = tr(item.shortLabel),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
        }
    }
}

/**
 * Area util das telas. Mede de novo porque a gaveta permanente ja consumiu 240dp:
 * decidir o layout pela largura da janela faria a tela se achar mais larga do que e.
 */
@Composable
private fun ContentArea(content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalWindowSize provides WindowSize.fromWidth(maxWidth)) {
            content()
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
    modifier: Modifier = Modifier.fillMaxHeight().width(DrawerWidth),
) {
    Surface(
        modifier = modifier,
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
