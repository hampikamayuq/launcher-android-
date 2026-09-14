package app.cascata.launcher.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import app.cascata.launcher.Row as UiRow
import app.cascata.launcher.data.AppEntry
import app.cascata.launcher.data.notifications.BadgeStyle
import app.cascata.launcher.data.theme.ClockStyle
import app.cascata.launcher.data.theme.ColorSource
import app.cascata.launcher.data.theme.DarkMode
import app.cascata.launcher.data.theme.Density
import app.cascata.launcher.data.theme.ThemeSettings
import app.cascata.launcher.settings.AppearanceSection
import app.cascata.launcher.ui.AlphabetIndex
import app.cascata.launcher.ui.AppRow
import app.cascata.launcher.ui.FavoritesRow
import app.cascata.launcher.ui.SectionHeader
import app.cascata.launcher.ui.clock.ClockFace
import app.cascata.launcher.ui.glance.ChipIcon
import app.cascata.launcher.ui.glance.ChipText
import app.cascata.launcher.ui.glance.GlanceChip
import app.cascata.launcher.ui.notifications.NotificationBlock
import app.cascata.launcher.ui.search.calculationItem
import app.cascata.launcher.ui.search.searchExtraItems
import app.cascata.launcher.ui.theme.CascataTheme
import app.cascata.launcher.ui.usage.UsageSheetContent
import app.cascata.launcher.SearchExtras
import app.cascata.launcher.data.search.SearchEngine
import app.cascata.launcher.data.search.SystemSettingsIndex

/**
 * As telas das imagens da loja. Cada uma monta a home (ou a folha) com os
 * composables de verdade — `AppRow`, `FavoritesRow`, `AlphabetIndex`,
 * `NotificationInline`, `UsageSheetContent`, `AppearanceSection` — sobre dados
 * fictícios. O que não dá para renderizar fora do aparelho (LauncherApps,
 * AppWidgetHost, sessões de mídia) fica de fora ou entra como ícone fictício.
 */

/** Margem lateral da home. A mesma de `HomeScreen`. */
private val SIDE = 20.dp

/** Largura do índice alfabético, reservada à direita da lista. */
private val INDEX_WIDTH = 28.dp

/** Espaço do topo no lugar da barra de status, que a prévia não desenha. */
private val TOP_INSET = 28.dp

/** O tema das prévias: cor de destaque escolhida, e superfície opaca (sem papel de parede por baixo). */
@Composable
internal fun PreviewTheme(
    dark: Boolean,
    settings: ThemeSettings = previewSettings(dark),
    content: @Composable () -> Unit,
) {
    CascataTheme(settings = settings, content = content)
}

/** Aparência das prévias: cor escolhida a dedo e superfície opaca (não há papel de parede). */
internal fun previewSettings(dark: Boolean): ThemeSettings = ThemeSettings(
    darkMode = if (dark) DarkMode.DARK else DarkMode.LIGHT,
    colorSource = ColorSource.ACCENT,
    backgroundOpacity = 1f,
    clockStyle = ClockStyle.BASIC,
)

/** Fundo da tela inteira, na cor da superfície do tema. */
@Composable
private fun Screen(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        content = content,
    )
}

/** Relógio e a porta das configurações, como no topo da home. */
@Composable
private fun ClockRow() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            ClockFace(
                clockStyle = ClockStyle.BASIC,
                now = DEMO_NOW,
                is24h = LocalLocale.current.platformLocale.language != "en",
                locale = LocalLocale.current.platformLocale,
            )
        }
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.settings_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A linha de cards do "at a glance". Os chips de verdade leem alarme, bateria,
 * agenda e clima do aparelho; aqui o desenho é o mesmo ([GlanceChip]) com
 * conteúdo fictício.
 */
@Composable
private fun GlanceChips(demo: Demo) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        GlanceChip(description = demo.alarmChip, onClick = {}) {
            ChipIcon(Icons.Outlined.Notifications)
            ChipText(demo.alarmChip)
        }
        GlanceChip(description = "82%", onClick = {}) {
            ChipIcon(painterResource(R.drawable.ic_battery))
            ChipText("82%")
        }
        GlanceChip(description = demo.calendarChip, onClick = {}) {
            ChipIcon(Icons.Outlined.DateRange)
            ChipText(demo.calendarChip)
            ChipText(demo.calendarTime)
        }
        GlanceChip(description = demo.weatherChip, onClick = {}) {
            ChipIcon(painterResource(R.drawable.ic_weather_partly_cloudy))
            ChipText(demo.weatherChip)
            ChipText(demo.weatherPlace)
        }
    }
}

/** O campo de busca da home, com o texto que a prévia quiser mostrar. */
@Composable
private fun SearchField(query: String) {
    OutlinedTextField(
        value = query,
        onValueChange = {},
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.search_clear),
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

/** Uma fatia da gaveta: cabeçalhos de letra e linhas de app, como na home. */
@Composable
private fun AppList(
    rows: List<UiRow<AppEntry>>,
    notificationsOn: String? = null,
    notificationCount: Int = 0,
    expanded: @Composable () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            when (row) {
                is UiRow.Header -> SectionHeader(row.letter)
                is UiRow.App -> {
                    AppRow(
                        entry = row.entry,
                        favorite = row.favorite,
                        repository = DemoIcons,
                        onLaunch = {},
                        notificationCount = if (row.entry.label == notificationsOn) notificationCount else 0,
                        badgeStyle = BadgeStyle.COUNT,
                        onBadgeClick = {},
                        onLongPress = {},
                    )
                    if (row.entry.label == notificationsOn) expanded()
                }
            }
        }
    }
}

/** (a) Tela inicial: relógio, cards do glance, favoritos e o começo da gaveta. */
@Composable
internal fun HomePreview(dark: Boolean) {
    val demo = demo()
    val apps = demo.apps.map(::demoEntry)
    val favorites = demo.favorites.map(::demoEntry)
    val rows = demoRows(apps, demo.favorites.toSet())

    PreviewTheme(dark) {
        Screen {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = SIDE).padding(top = TOP_INSET)) {
                ClockRow()
                GlanceChips(demo)
                SearchField("")
                FavoritesRow(
                    favorites = favorites,
                    repository = DemoIcons,
                    onLaunch = {},
                    onMoveFavorite = { _, _ -> },
                )
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().padding(end = INDEX_WIDTH)) {
                        AppList(rows.take(14))
                    }
                    AlphabetIndex(
                        letters = demoLetters(apps),
                        onLetterFocused = {},
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}

/** (b) A gaveta inteira, com o índice alfabético do lado. */
@Composable
internal fun DrawerPreview(dark: Boolean) {
    val demo = demo()
    val apps = demo.apps.map(::demoEntry)
    val rows = demoRows(apps, demo.favorites.toSet())

    PreviewTheme(dark) {
        Screen {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = SIDE).padding(top = TOP_INSET)) {
                SearchField("")
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().padding(end = INDEX_WIDTH)) {
                        AppList(rows)
                    }
                    AlphabetIndex(
                        letters = demoLetters(apps),
                        onLetterFocused = {},
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}

/**
 * (c) A busca: conta, contatos e telas do sistema no mesmo lugar. É uma
 * composição de vitrine — cada seção existe no app, e aqui elas aparecem juntas
 * para mostrar o alcance da busca numa imagem só.
 */
@Composable
internal fun SearchPreview(dark: Boolean) {
    val demo = demo()
    val extras = SearchExtras(
        calculation = DEMO_CALCULATION,
        contacts = demoContacts(demo),
        settings = SystemSettingsIndex.entries.filter {
            it.id in setOf("wifi", "battery", "display", "sound", "storage")
        },
        web = SearchEngine.DUCKDUCKGO to demo.searchQuery,
    )

    PreviewTheme(dark) {
        Screen {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = SIDE).padding(top = TOP_INSET)) {
                ClockRow()
                SearchField(demo.searchQuery)
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    calculationItem(extras.calculation!!)
                    searchExtraItems(
                        extras = extras,
                        repository = DemoIcons,
                        onOpenShortcut = {},
                        onOpenContact = {},
                        onCallContact = {},
                        onMessageContact = {},
                        onOpenSetting = {},
                        onWebSearch = {},
                    )
                }
            }
        }
    }
}

/** (d) Notificações abertas embaixo da linha do app, sem sair da lista. */
@Composable
internal fun NotificationsPreview(dark: Boolean) {
    val demo = demo()
    val apps = demo.apps.map(::demoEntry)
    val rows = demoRows(apps, demo.favorites.toSet())
    val notifications = demoNotifications(demo)
    // A lista começa algumas linhas antes do app com notificações, para o bloco
    // aberto ficar no meio da tela e não colado no topo.
    val start = rows.indexOfFirst { it is UiRow.App && it.entry.label == demo.notificationApp } - 4

    PreviewTheme(dark) {
        Screen {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = SIDE).padding(top = TOP_INSET)) {
                SearchField("")
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().padding(end = INDEX_WIDTH)) {
                        AppList(
                            rows = rows.drop(start.coerceAtLeast(0)),
                            notificationsOn = demo.notificationApp,
                            notificationCount = notifications.size,
                        ) {
                            NotificationBlock(
                                appLabel = demo.notificationApp,
                                notifications = notifications,
                                onOpen = {},
                                onDismiss = {},
                                onDismissAll = {},
                                onFireAction = {},
                                onReply = { _, _ -> true },
                                onCollapse = {},
                            )
                        }
                    }
                    AlphabetIndex(
                        letters = demoLetters(apps),
                        onLetterFocused = {},
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}

/** (e) Uso do dia e limites por app, na folha que o card do glance abre. */
@Composable
internal fun UsagePreview(dark: Boolean) {
    val demo = demo()
    val usage = demoUsage(demo)
    val apps = demo.favorites.associate { demoPackage(it) to demoEntry(it) }
    val limits = mapOf(demoPackage(demo.favorites[0]) to 60, demoPackage(demo.favorites[2]) to 30)

    PreviewTheme(dark) {
        Screen {
            // A home continua atrás da folha, escurecida — é o que se vê no app.
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = SIDE).padding(top = TOP_INSET)) {
                ClockRow()
                GlanceChips(demo)
                SearchField("")
                FavoritesRow(
                    favorites = demo.favorites.map(::demoEntry),
                    repository = DemoIcons,
                    onLaunch = {},
                    onMoveFavorite = { _, _ -> },
                )
                AppList(demoRows(demo.apps.map(::demoEntry), demo.favorites.toSet()).take(6))
            }
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .align(Alignment.CenterHorizontally)
                            .size(width = 32.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                    UsageSheetContent(
                        usage = usage,
                        apps = apps,
                        limits = limits,
                        repository = DemoIcons,
                        onEdit = {},
                    )
                }
            }
        }
    }
}

/** (f) Configurações: a seção de aparência, que é a que muda a cara do launcher. */
@Composable
internal fun AppearancePreview(dark: Boolean) {
    // A própria tela é renderizada com os valores que ela mostra: os controles
    // não anunciam uma coisa e desenham outra. A escala fica em 1, que é onde os
    // rótulos dos botões segmentados cabem em todos os três idiomas.
    val shown = previewSettings(dark).copy(backgroundOpacity = 0.55f, density = Density.COMFORTABLE)
    PreviewTheme(dark, settings = shown) {
        Screen {
            Column(modifier = Modifier.fillMaxSize().padding(top = TOP_INSET)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                AppearanceSection(settings = shown, update = {})
            }
        }
    }
}
