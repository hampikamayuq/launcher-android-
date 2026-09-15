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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import app.cascata.launcher.data.theme.FavoritesStyle
import app.cascata.launcher.data.theme.IndexStyle
import app.cascata.launcher.data.theme.ThemeSettings
import app.cascata.launcher.data.theme.WallpaperText
import app.cascata.launcher.settings.AppearanceSection
import app.cascata.launcher.ui.AlphabetIndex
import app.cascata.launcher.ui.AppRow
import app.cascata.launcher.ui.FavoritesList
import app.cascata.launcher.ui.SectionHeader
import app.cascata.launcher.ui.clock.ClockFace
import app.cascata.launcher.ui.glance.ChipIcon
import app.cascata.launcher.ui.glance.ChipText
import app.cascata.launcher.ui.glance.GlanceChip
import app.cascata.launcher.ui.notifications.NotificationBlock
import app.cascata.launcher.ui.search.calculationItem
import app.cascata.launcher.ui.search.searchExtraItems
import app.cascata.launcher.ui.theme.CascataTheme
import app.cascata.launcher.ui.theme.LocalOnWallpaper
import app.cascata.launcher.ui.theme.SurfaceTheme
import app.cascata.launcher.ui.theme.WALLPAPER_VEIL_ALPHA
import app.cascata.launcher.ui.usage.UsageSheetContent
import app.cascata.launcher.SearchExtras
import app.cascata.launcher.data.search.SearchEngine
import app.cascata.launcher.data.search.SystemSettingsIndex

/**
 * As telas das imagens da loja. Cada uma monta a home (ou a folha) com os
 * composables de verdade — `AppRow`, `FavoritesList`, `AlphabetIndex`,
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

/**
 * O tema das prévias. Por padrão é o da home: superfície transparente, texto
 * claro com sombra, como fica sobre um papel de parede escuro. [overWallpaper]
 * falso é para as telas que têm fundo próprio e opaco — as configurações.
 */
@Composable
internal fun PreviewTheme(
    dark: Boolean,
    settings: ThemeSettings = previewSettings(dark),
    overWallpaper: Boolean = true,
    content: @Composable () -> Unit,
) {
    CascataTheme(settings = settings, overWallpaper = overWallpaper, content = content)
}

/**
 * Aparência das prévias: cor de destaque escolhida a dedo e a home sobre o
 * papel de parede, que é como ela nasce. Não há papel de parede nenhum aqui —
 * quem faz o papel dele é o gradiente de [Screen] —, então a tinta do texto é
 * fixada em clara em vez de perguntar ao sistema.
 */
internal fun previewSettings(dark: Boolean): ThemeSettings = ThemeSettings(
    darkMode = if (dark) DarkMode.DARK else DarkMode.LIGHT,
    colorSource = ColorSource.ACCENT,
    backgroundOpacity = 0f,
    clockStyle = ClockStyle.BASIC,
    favoritesStyle = FavoritesStyle.LIST,
    indexStyle = IndexStyle.WAVE,
    wallpaperText = WallpaperText.LIGHT,
)

/**
 * O papel de parede das fotos: um degradê azul-acinzentado, mar ao anoitecer.
 * Fora do aparelho não há papel de parede, e sem nada por baixo as imagens não
 * mostrariam o que a home virou — texto claro com sombra, direto sobre a foto.
 */
private val DUSK = Brush.verticalGradient(
    listOf(Color(0xFF0A111C), Color(0xFF16273A), Color(0xFF32516D)),
)

/** Fundo da tela inteira: o degradê, ou a superfície do tema nas telas opacas. */
@Composable
private fun Screen(opaque: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (opaque) {
                    Modifier.background(MaterialTheme.colorScheme.surface)
                } else {
                    Modifier.background(DUSK)
                }
            ),
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
    val veil = MaterialTheme.colorScheme.surface.copy(alpha = WALLPAPER_VEIL_ALPHA)
    OutlinedTextField(
        value = query,
        onValueChange = {},
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = if (LocalOnWallpaper.current) {
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = veil,
                unfocusedContainerColor = veil,
            )
        } else {
            OutlinedTextFieldDefaults.colors()
        },
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

/**
 * (a) Tela inicial: relógio, cards do glance, favoritos em lista e o começo da
 * gaveta. Não há campo de busca — ele só aparece no gesto de subir.
 */
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
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().padding(end = INDEX_WIDTH)) {
                        // Como na home: os favoritos são as primeiras linhas da
                        // lista, e as seções vêm logo depois.
                        FavoritesList(
                            favorites = favorites,
                            repository = DemoIcons,
                            onLaunch = {},
                            onMoveFavorite = { _, _ -> },
                            onLongPress = {},
                        )
                        AppList(rows.take(9))
                    }
                    AlphabetIndex(
                        letters = demoLetters(apps),
                        onLetterFocused = {},
                        onHome = {},
                        style = IndexStyle.WAVE,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}

/**
 * (b) O índice em onda em uso: o dedo parado numa letra, a coluna curvada ao
 * redor dele, a bolha repetindo a letra e a lista reduzida àquela seção.
 */
@Composable
internal fun DrawerPreview(dark: Boolean) {
    val demo = demo()
    val apps = demo.apps.map(::demoEntry)
    val rows = demoRows(apps, demo.favorites.toSet())

    PreviewTheme(dark) {
        Screen {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = SIDE).padding(top = TOP_INSET)) {
                ClockRow()
                GlanceChips(demo)
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().padding(end = INDEX_WIDTH)) {
                        AppList(demoSection(rows, PEEK_LETTER))
                    }
                    AlphabetIndex(
                        letters = demoLetters(apps),
                        onLetterFocused = {},
                        onHome = {},
                        style = IndexStyle.WAVE,
                        previewLetter = PEEK_LETTER,
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
                ClockRow()
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
                        onHome = {},
                        style = IndexStyle.WAVE,
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
                FavoritesList(
                    favorites = demo.favorites.map(::demoEntry),
                    repository = DemoIcons,
                    onLaunch = {},
                    onMoveFavorite = { _, _ -> },
                    onLongPress = {},
                )
                AppList(demoRows(demo.apps.map(::demoEntry), demo.favorites.toSet()).take(4))
            }
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
            // A folha tem superfície própria: `SurfaceTheme` desfaz nela a tinta
            // e a sombra de wallpaper, como a home faz com as folhas de verdade.
            SurfaceTheme {
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
}

/** (f) Configurações: a seção de aparência, que é a que muda a cara do launcher. */
@Composable
internal fun AppearancePreview(dark: Boolean) {
    // A própria tela é renderizada com os valores que ela mostra: os controles
    // não anunciam uma coisa e desenham outra. A escala fica em 1, que é onde os
    // rótulos dos botões segmentados cabem em todos os três idiomas.
    val shown = previewSettings(dark).copy(backgroundOpacity = 0.55f, density = Density.COMFORTABLE)
    PreviewTheme(dark, settings = shown, overWallpaper = false) {
        Screen(opaque = true) {
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
