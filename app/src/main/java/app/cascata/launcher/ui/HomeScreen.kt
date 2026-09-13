package app.cascata.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cascata.launcher.HomeViewModel
import app.cascata.launcher.R
import app.cascata.launcher.Row as UiRow
import app.cascata.launcher.data.AppEntry
import app.cascata.launcher.data.AppRepository
import app.cascata.launcher.data.glance.AlarmSource
import app.cascata.launcher.data.glance.BatterySource
import app.cascata.launcher.data.glance.CalendarSource
import app.cascata.launcher.data.glance.GlanceSettings
import app.cascata.launcher.data.glance.MediaSource
import app.cascata.launcher.data.glance.weather.WeatherSource
import app.cascata.launcher.data.notifications.BadgeStyle
import app.cascata.launcher.data.theme.ClockStyle
import app.cascata.launcher.data.widgets.WidgetHostManager
import app.cascata.launcher.data.widgets.WidgetLayout
import app.cascata.launcher.ui.clock.ClockHeader
import app.cascata.launcher.ui.glance.GlanceRow
import app.cascata.launcher.ui.notifications.NotificationBadge
import app.cascata.launcher.ui.notifications.NotificationInline
import app.cascata.launcher.ui.theme.LocalBackgroundOpacity
import app.cascata.launcher.ui.theme.LocalLauncherDensity
import app.cascata.launcher.ui.theme.iconSize
import app.cascata.launcher.ui.theme.rowPadding
import app.cascata.launcher.ui.widgets.WidgetActions
import app.cascata.launcher.ui.widgets.WidgetArea
import kotlinx.coroutines.launch

private val INDEX_WIDTH = 28.dp
private val LOCK_SIZE = 14.dp

/**
 * A home inteira. Recebe o ViewModel direto: as ações já são doze, e passá-las
 * uma a uma só trocaria o acoplamento por uma lista de lambdas.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    repository: AppRepository,
    clockStyle: ClockStyle,
    glance: GlanceSettings,
    alarmSource: AlarmSource,
    batterySource: BatterySource,
    calendarSource: CalendarSource,
    weatherSource: WeatherSource,
    mediaSource: MediaSource,
    widgetLayout: WidgetLayout,
    widgetHost: WidgetHostManager,
    widgetActions: WidgetActions,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Já vem filtrado por silenciados e pelo recurso desligado: mapa vazio é o
    // caso normal de quem nunca ligou as notificações.
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val notificationSettings by viewModel.notificationSettings.collectAsStateWithLifecycle()
    val notificationsConnected by viewModel.notificationsConnected.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    /** O gesto só foca o campo — que continua visível e tocável o tempo todo. */
    val openSearch = {
        runCatching { searchFocus.requestFocus() }
        keyboard?.show()
        Unit
    }
    val swipeUp = rememberSwipeUpToSearch(openSearch)

    var contextApp by remember { mutableStateOf<AppEntry?>(null) }
    var showHidden by remember { mutableStateOf(false) }

    // Um app expandido por vez. `expanded` é o que o usuário pediu; `mounted` é
    // o que ainda ocupa um item da lista — só sai quando a animação de recolher
    // termina, senão ela não teria onde acontecer.
    var expandedAppKey by remember { mutableStateOf<String?>(null) }
    var mountedAppKey by remember { mutableStateOf<String?>(null) }

    // Buscar troca as linhas debaixo do bloco: recolhe antes de a lista virar outra.
    LaunchedEffect(state.query) { expandedAppKey = null }

    // Dispensou a última notificação do app: não sobrou bloco para mostrar, e
    // não há o que animar — os dois estados saem juntos.
    LaunchedEffect(notifications, expandedAppKey) {
        val open = expandedAppKey
        if (open != null && notifications[open].isNullOrEmpty()) {
            expandedAppKey = null
            mountedAppKey = null
        }
    }

    // Voltar limpa a busca. Sem busca não faz nada: aqui já é a tela inicial.
    BackHandler(enabled = state.query.isNotEmpty()) {
        viewModel.onClearQuery()
        focusManager.clearFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Fundo translúcido: o papel de parede continua visível por baixo, e
            // quanto dele aparece é a opacidade escolhida nas configurações.
            .background(MaterialTheme.colorScheme.surface.copy(alpha = LocalBackgroundOpacity.current))
            .safeDrawingPadding()
            .padding(horizontal = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ClockHeader(
                clockStyle = clockStyle,
                modifier = Modifier.swipeUpToSearch(openSearch),
            )

            // Entre o relógio e a busca: sem nenhum card ligado, não ocupa altura.
            GlanceRow(
                settings = glance,
                alarmSource = alarmSource,
                batterySource = batterySource,
                calendarSource = calendarSource,
                weatherSource = weatherSource,
                mediaSource = mediaSource,
                showMedia = notificationSettings.showMedia && notificationsConnected,
            )

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = viewModel::onClearQuery) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.search_clear),
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .focusRequester(searchFocus),
            )

            if (state.favorites.isNotEmpty() && state.query.isEmpty()) {
                FavoritesRow(
                    favorites = state.favorites,
                    repository = repository,
                    onMoveFavorite = viewModel::onMoveFavorite,
                )
            }

            // O bloco de notificações é um item da lista, não um filho da linha:
            // assim a LazyColumn mede e recicla os dois separadamente, e a
            // animação de abrir não estica a altura de uma linha reciclada. O
            // preço é este deslocamento — tudo que vem depois do bloco anda uma
            // posição, inclusive o alvo do índice alfabético.
            val expandedNotifications = mountedAppKey?.let { notifications[it] }.orEmpty()
            val expandedRow = mountedAppKey
                ?.takeIf { expandedNotifications.isNotEmpty() }
                ?.let { key -> state.rows.indexOfFirst { it is UiRow.App && it.entry.appKey == key } }
                ?.takeIf { it >= 0 }
            val notificationItem = expandedRow?.plus(1)
            val rowIndexOf: (Int) -> Int = { item ->
                if (expandedRow == null || item <= expandedRow) item else item - 1
            }
            // Os widgets são um item da lista antes de todas as linhas: o índice
            // dentro do `items` não muda, mas o da LazyColumn inteira anda um.
            val widgetItems = if (widgetLayout.slots.isNotEmpty()) 1 else 0

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = INDEX_WIDTH)
                        .nestedScroll(swipeUp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // Primeiro item, acima das linhas de app: os widgets rolam
                    // junto com a lista em vez de comerem a altura do cabeçalho.
                    if (widgetItems > 0) {
                        item(key = "widgets", contentType = "widgets") {
                            WidgetArea(
                                layout = widgetLayout,
                                host = widgetHost,
                                actions = widgetActions,
                            )
                        }
                    }

                    items(
                        count = state.rows.size + if (expandedRow != null) 1 else 0,
                        key = { index ->
                            if (index == notificationItem) {
                                "notif-$mountedAppKey"
                            } else {
                                when (val row = state.rows[rowIndexOf(index)]) {
                                    is UiRow.Header -> "header-${row.letter}"
                                    is UiRow.App -> row.entry.key
                                }
                            }
                        },
                        contentType = { index ->
                            when {
                                index == notificationItem -> "notifications"
                                state.rows[rowIndexOf(index)] is UiRow.Header -> "header"
                                else -> "app"
                            }
                        },
                    ) { index ->
                        if (index == notificationItem) {
                            val entry = (state.rows[rowIndexOf(index)] as? UiRow.App)?.entry
                            NotificationInline(
                                appLabel = entry?.label.orEmpty(),
                                notifications = expandedNotifications,
                                expanded = expandedAppKey == mountedAppKey,
                                onOpen = { viewModel.onOpenNotification(it) },
                                onDismiss = viewModel::onDismissNotification,
                                onDismissAll = { mountedAppKey?.let(viewModel::onDismissAll) },
                                onFireAction = { viewModel.onFireAction(it) },
                                onReply = viewModel::onReply,
                                onCollapse = { expandedAppKey = null },
                                // Recolheu de verdade: agora o item pode sair da lista.
                                onCollapsed = { if (expandedAppKey == null) mountedAppKey = null },
                            )
                            return@items
                        }
                        when (val row = state.rows[rowIndexOf(index)]) {
                            is UiRow.Header -> SectionHeader(row.letter)
                            is UiRow.App -> AppRow(
                                entry = row.entry,
                                favorite = row.favorite,
                                repository = repository,
                                notificationCount = notifications[row.entry.appKey]?.size ?: 0,
                                badgeStyle = notificationSettings.badgeStyle,
                                onBadgeClick = {
                                    // Sem expansão inline o indicador é só mais
                                    // um lugar por onde abrir o app.
                                    if (!notificationSettings.expandInline) {
                                        repository.launch(row.entry)
                                    } else if (expandedAppKey == row.entry.appKey) {
                                        expandedAppKey = null
                                    } else {
                                        mountedAppKey = row.entry.appKey
                                        expandedAppKey = row.entry.appKey
                                    }
                                },
                                onLongPress = { contextApp = row.entry },
                            )
                        }
                    }

                    // Última linha da lista, e só quando há o que mostrar lá dentro.
                    if (state.hiddenApps.isNotEmpty() && state.query.isEmpty()) {
                        item(key = "hidden-apps", contentType = "hidden") {
                            HiddenAppsEntry(
                                count = state.hiddenApps.size,
                                onClick = { showHidden = true },
                            )
                        }
                    }
                }

                if (state.query.isEmpty()) {
                    AlphabetIndex(
                        letters = state.sectionIndex.keys.toList(),
                        onLetterFocused = { letter ->
                            state.sectionIndex[letter]?.let { index ->
                                // Índice da linha -> índice do item: a área de
                                // widgets vale uma posição, e o bloco de
                                // notificações aberto acima do destino, outra.
                                val target = index + widgetItems +
                                    if (expandedRow != null && expandedRow < index) 1 else 0
                                scope.launch { listState.scrollToItem(target) }
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }

                if (state.rows.isEmpty() && !state.loading && state.query.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.empty_search, state.query),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 32.dp),
                    )
                }
            }
        }
    }

    if (state.showWelcome) {
        WelcomeSheet(
            repository = repository,
            onLauncherChosen = viewModel::refreshDefaultLauncher,
            onDismiss = viewModel::onDismissWelcome,
        )
    }

    contextApp?.let { entry ->
        AppContextSheet(
            entry = entry,
            favorite = state.favorites.any { it.key == entry.key },
            hasShortcutHost = state.hasShortcutHost,
            repository = repository,
            viewModel = viewModel,
            onDismiss = { contextApp = null },
        )
    }

    if (showHidden) {
        HiddenAppsSheet(
            hidden = state.hiddenApps,
            repository = repository,
            onUnhide = viewModel::onUnhide,
            onDismiss = { showHidden = false },
        )
    }
}

@Composable
private fun SectionHeader(letter: Char) {
    Text(
        text = letter.toString(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun AppRow(
    entry: AppEntry,
    favorite: Boolean,
    repository: AppRepository,
    notificationCount: Int,
    badgeStyle: BadgeStyle,
    onBadgeClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val density = LocalLauncherDensity.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { repository.launch(entry) },
                onLongClick = onLongPress,
            )
            .padding(vertical = density.rowPadding),
    ) {
        AppIcon(entry = entry, repository = repository, size = density.iconSize)
        Spacer(Modifier.width(14.dp))
        Text(
            text = entry.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        // Cadeado do perfil privado: o app é o mesmo, o espaço onde ele roda é que não.
        if (entry.isPrivateProfile) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = stringResource(R.string.private_profile),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(LOCK_SIZE),
            )
        }
        if (favorite) {
            Spacer(Modifier.width(6.dp))
            Text(text = "•", color = MaterialTheme.colorScheme.primary)
        }
        // O indicador tem toque próprio (expandir); o resto da linha abre o app.
        NotificationBadge(
            count = notificationCount,
            style = badgeStyle,
            appLabel = entry.label,
            onClick = onBadgeClick,
        )
    }
}

@Composable
private fun HiddenAppsEntry(count: Int, onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.hidden_apps_count, count),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(top = 24.dp, bottom = 32.dp),
    )
}
