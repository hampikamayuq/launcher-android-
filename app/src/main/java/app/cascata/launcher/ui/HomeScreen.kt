package app.cascata.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cascata.launcher.HomeViewModel
import app.cascata.launcher.R
import app.cascata.launcher.Row as UiRow
import app.cascata.launcher.rowsForLetter
import app.cascata.launcher.data.AppEntry
import app.cascata.launcher.data.AppRepository
import app.cascata.launcher.data.IconSource
import app.cascata.launcher.data.glance.AlarmSource
import app.cascata.launcher.data.glance.BatterySource
import app.cascata.launcher.data.glance.CalendarSource
import app.cascata.launcher.data.glance.GlanceSettings
import app.cascata.launcher.data.glance.MediaSource
import app.cascata.launcher.data.glance.weather.WeatherSource
import app.cascata.launcher.data.notifications.BadgeStyle
import app.cascata.launcher.data.theme.ClockStyle
import app.cascata.launcher.data.theme.FavoritesStyle
import app.cascata.launcher.data.theme.IndexStyle
import app.cascata.launcher.data.usage.UsageSource
import app.cascata.launcher.data.widgets.WidgetHostManager
import app.cascata.launcher.data.widgets.WidgetLayout
import app.cascata.launcher.ui.clock.ClockHeader
import app.cascata.launcher.ui.glance.GlanceRow
import app.cascata.launcher.ui.notifications.NotificationBadge
import app.cascata.launcher.ui.notifications.NotificationInline
import app.cascata.launcher.ui.search.calculationItem
import app.cascata.launcher.ui.search.searchExtraItems
import app.cascata.launcher.ui.theme.LocalBackgroundOpacity
import app.cascata.launcher.ui.theme.LocalLauncherDensity
import app.cascata.launcher.ui.theme.LocalOnWallpaper
import app.cascata.launcher.ui.theme.WALLPAPER_VEIL_ALPHA
import app.cascata.launcher.ui.theme.iconSize
import app.cascata.launcher.ui.theme.rowPadding
import app.cascata.launcher.ui.usage.PauseSheet
import app.cascata.launcher.ui.usage.UsageSheet
import app.cascata.launcher.ui.widgets.WidgetActions
import app.cascata.launcher.ui.widgets.WidgetArea
import kotlinx.coroutines.launch

private val LOCK_SIZE = 14.dp

/**
 * Quantos quadros esperar pelo campo de busca antes de desistir do foco. Ele
 * entra por animação, e o `FocusRequester` só existe depois de composto.
 */
private const val FOCUS_ATTEMPTS = 10

/**
 * A home inteira. Recebe o ViewModel direto: as ações já são doze, e passá-las
 * uma a uma só trocaria o acoplamento por uma lista de lambdas.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    repository: AppRepository,
    clockStyle: ClockStyle,
    favoritesStyle: FavoritesStyle,
    indexStyle: IndexStyle,
    /** Falso: o campo de busca só aparece no gesto de subir ou com busca em curso. */
    searchBarVisible: Boolean,
    glance: GlanceSettings,
    alarmSource: AlarmSource,
    batterySource: BatterySource,
    calendarSource: CalendarSource,
    weatherSource: WeatherSource,
    mediaSource: MediaSource,
    usageSource: UsageSource,
    widgetLayout: WidgetLayout,
    widgetHost: WidgetHostManager,
    widgetActions: WidgetActions,
    modifier: Modifier = Modifier,
    /** Falso enquanto as boas-vindas de três telas estão na frente da home. */
    onboardingDone: Boolean = true,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Já vem filtrado por silenciados e pelo recurso desligado: mapa vazio é o
    // caso normal de quem nunca ligou as notificações.
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val notificationSettings by viewModel.notificationSettings.collectAsStateWithLifecycle()
    val notificationsConnected by viewModel.notificationsConnected.collectAsStateWithLifecycle()
    // O que a busca acha além dos apps. Tudo vazio com a query em branco.
    val extras by viewModel.searchExtras.collectAsStateWithLifecycle()
    // Uso do aparelho: a lista já chega vazia com o recurso desligado ou sem o
    // acesso do sistema, e o prompt só existe quando um limite estourou.
    val usageSettings by viewModel.usageSettings.collectAsStateWithLifecycle()
    val usageToday by viewModel.usageToday.collectAsStateWithLifecycle()
    val pausePrompt by viewModel.pausePrompt.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // O campo de busca não fica na tela quando ninguém pediu por ele:
    // `searchOpen` é o pedido do gesto, e volta a falso quando a busca esvazia
    // e o foco sai. Com `searchBarVisible` ligado ele é fixo, como antes.
    var searchOpen by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    val searchVisible = searchBarVisible || searchOpen || state.query.isNotEmpty()

    /** O gesto pede o campo; quem o foca é o efeito abaixo, quando ele existir. */
    val openSearch = { searchOpen = true }
    val swipeUp = rememberSwipeUpToSearch(openSearch)

    // O campo entra por animação: a primeira tentativa de foco cai antes de ele
    // existir, então insistimos por alguns quadros.
    LaunchedEffect(searchOpen) {
        if (!searchOpen) return@LaunchedEffect
        repeat(FOCUS_ATTEMPTS) {
            if (runCatching { searchFocus.requestFocus() }.isSuccess) {
                keyboard?.show()
                return@LaunchedEffect
            }
            withFrameNanos { }
        }
    }

    // Busca vazia e sem foco: o campo pode sair de cena de novo.
    LaunchedEffect(searchFocused, state.query) {
        if (!searchFocused && state.query.isEmpty()) searchOpen = false
    }

    var contextApp by remember { mutableStateOf<AppEntry?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    var showUsage by remember { mutableStateOf(false) }

    // Pacote -> app, para o card e a folha de uso nomearem o que o sistema
    // devolve por pacote. Só é refeito fora da busca: durante ela as linhas são
    // o resultado, e o último mapa inteiro continua valendo.
    var usageApps by remember { mutableStateOf(emptyMap<String, AppEntry>()) }
    LaunchedEffect(state.rows, state.hiddenApps, state.query) {
        if (state.query.isNotEmpty()) return@LaunchedEffect
        usageApps = (state.rows.mapNotNull { (it as? UiRow.App)?.entry } + state.hiddenApps)
            .associateBy { it.component.packageName }
    }

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

    // A letra que o índice em onda escolheu: enquanto ela existe, a lista é só
    // aquela seção — favoritos, widgets e extras da busca ficam de fora. Nulo é
    // a home inteira. Letra que não existe mais (a lista mudou) cai fora sozinha.
    var peekLetter by remember { mutableStateOf<Char?>(null) }
    val peekRows = remember(peekLetter, state.rows) {
        peekLetter?.let { state.rowsForLetter(it) }?.takeIf { it.isNotEmpty() }
    }
    val shownRows = peekRows ?: state.rows

    // Começar a digitar sai do filtro: a busca já é outra lista.
    LaunchedEffect(state.query) { if (state.query.isNotEmpty()) peekLetter = null }

    // Rolar com o dedo também sai, e a lista inteira volta debaixo dele. Só o
    // gesto do usuário conta: a rolagem que o próprio filtro dispara, não.
    val exitPeekOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) peekLetter = null
                return Offset.Zero
            }
        }
    }

    /** A estrela do índice: sair do filtro e voltar ao topo, onde estão os favoritos. */
    val goHome = {
        peekLetter = null
        scope.launch { listState.scrollToItem(0) }
        Unit
    }

    // Voltar sai primeiro do filtro, depois limpa a busca. Sem nenhum dos dois
    // não faz nada: aqui já é a tela inicial.
    BackHandler(enabled = peekRows != null || state.query.isNotEmpty()) {
        if (peekRows != null) {
            peekLetter = null
        } else {
            viewModel.onClearQuery()
            focusManager.clearFocus()
        }
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
                showUsage = usageSettings.enabled && usageSettings.showCard,
                usageSource = usageSource,
                usageToday = usageToday,
                usageApps = usageApps,
                onUsageClick = { showUsage = true },
            )

            // Sobre o papel de parede o campo precisa de um véu próprio: as
            // bordas do Material contam com uma superfície que aqui não existe.
            val veil = MaterialTheme.colorScheme.surface.copy(alpha = WALLPAPER_VEIL_ALPHA)
            val fieldColors = if (LocalOnWallpaper.current) {
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = veil,
                    unfocusedContainerColor = veil,
                )
            } else {
                OutlinedTextFieldDefaults.colors()
            }

            AnimatedVisibility(visible = searchVisible) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = fieldColors,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    // A tecla de busca abre o melhor resultado que existir: o
                    // primeiro app, senão o primeiro atalho, senão a web.
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            val app = state.rows.firstNotNullOfOrNull { (it as? UiRow.App)?.entry }
                            val shortcut = extras.shortcuts.firstOrNull()
                            when {
                                app != null -> viewModel.onLaunchRequested(app)
                                shortcut != null -> viewModel.onOpenShortcut(shortcut)
                                extras.web != null -> viewModel.onWebSearch()
                                // Nada para abrir: a query fica onde está.
                                else -> return@KeyboardActions
                            }
                            viewModel.onClearQuery()
                            focusManager.clearFocus()
                        },
                    ),
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
                        .onFocusChanged { searchFocused = it.isFocused }
                        .focusRequester(searchFocus),
                )
            }

            // Em lista os favoritos são itens da própria `LazyColumn`, para
            // rolarem junto com a gaveta; aqui em cima só a fileira antiga.
            if (favoritesStyle == FavoritesStyle.ROW &&
                state.favorites.isNotEmpty() && state.query.isEmpty() && peekRows == null
            ) {
                FavoritesRow(
                    favorites = state.favorites,
                    repository = repository,
                    onLaunch = viewModel::onLaunchRequested,
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
                ?.let { key -> shownRows.indexOfFirst { it is UiRow.App && it.entry.appKey == key } }
                ?.takeIf { it >= 0 }
            val notificationItem = expandedRow?.plus(1)
            val rowIndexOf: (Int) -> Int = { item ->
                if (expandedRow == null || item <= expandedRow) item else item - 1
            }
            // Favoritos e widgets são itens da lista antes de todas as linhas: o
            // índice dentro do `items` não muda, mas o da LazyColumn inteira
            // anda um por bloco. Durante a busca — e no modo filtrado — a lista
            // é só resultado: os dois voltam quando ela volta a ser a gaveta.
            val homeItems = state.query.isEmpty() && peekRows == null
            val favoriteItems = if (
                homeItems && favoritesStyle == FavoritesStyle.LIST && state.favorites.isNotEmpty()
            ) 1 else 0
            val widgetItems = if (homeItems && widgetLayout.slots.isNotEmpty()) 1 else 0
            val leadingItems = favoriteItems + widgetItems

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = INDEX_WIDTH)
                        .nestedScroll(exitPeekOnScroll)
                        .nestedScroll(swipeUp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // Primeiro item de todos: é onde a estrela do índice leva, e
                    // é por rolar junto com a gaveta que eles não comem altura
                    // fixa do topo. O bloco inteiro é um item só — o arraste que
                    // reordena mede a própria linha e não depende do
                    // espaçamento da LazyColumn, e nenhuma chave se repete.
                    if (favoriteItems > 0) {
                        item(key = "favorites", contentType = "favorites") {
                            FavoritesList(
                                favorites = state.favorites,
                                repository = repository,
                                onLaunch = viewModel::onLaunchRequested,
                                onMoveFavorite = viewModel::onMoveFavorite,
                                onLongPress = { contextApp = it },
                            )
                        }
                    }

                    // Logo abaixo dos favoritos e acima das linhas de app.
                    if (widgetItems > 0) {
                        item(key = "widgets", contentType = "widgets") {
                            WidgetArea(
                                layout = widgetLayout,
                                host = widgetHost,
                                actions = widgetActions,
                            )
                        }
                    }

                    // Antes das linhas de app: quem digitou uma conta quer o
                    // número, não a gaveta.
                    if (peekRows == null) extras.calculation?.let { calculationItem(it) }

                    // As chaves são as mesmas nas duas listas — a filtrada é
                    // uma fatia da inteira —, e é isso que faz a seção ficar no
                    // lugar quando o filtro entra e sai.
                    items(
                        count = shownRows.size + if (expandedRow != null) 1 else 0,
                        key = { index ->
                            if (index == notificationItem) {
                                "notif-$mountedAppKey"
                            } else {
                                when (val row = shownRows[rowIndexOf(index)]) {
                                    is UiRow.Header -> "header-${row.letter}"
                                    is UiRow.App -> row.entry.key
                                }
                            }
                        },
                        contentType = { index ->
                            when {
                                index == notificationItem -> "notifications"
                                shownRows[rowIndexOf(index)] is UiRow.Header -> "header"
                                else -> "app"
                            }
                        },
                    ) { index ->
                        if (index == notificationItem) {
                            val entry = (shownRows[rowIndexOf(index)] as? UiRow.App)?.entry
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
                        when (val row = shownRows[rowIndexOf(index)]) {
                            is UiRow.Header -> SectionHeader(row.letter)
                            is UiRow.App -> AppRow(
                                entry = row.entry,
                                favorite = row.favorite,
                                repository = repository,
                                onLaunch = { viewModel.onLaunchRequested(row.entry) },
                                notificationCount = notifications[row.entry.appKey]?.size ?: 0,
                                badgeStyle = notificationSettings.badgeStyle,
                                onBadgeClick = {
                                    // Sem expansão inline o indicador é só mais
                                    // um lugar por onde abrir o app.
                                    if (!notificationSettings.expandInline) {
                                        viewModel.onLaunchRequested(row.entry)
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

                    // Depois dos apps: atalhos, contatos, configurações e web.
                    if (peekRows == null) {
                        searchExtraItems(
                            extras = extras,
                            repository = repository,
                            onOpenShortcut = viewModel::onOpenShortcut,
                            onOpenContact = viewModel::onOpenContact,
                            onCallContact = viewModel::onCallContact,
                            onMessageContact = viewModel::onMessageContact,
                            onOpenSetting = viewModel::onOpenSetting,
                            onWebSearch = viewModel::onWebSearch,
                        )
                    }

                    // Última linha da lista, e só quando há o que mostrar lá dentro.
                    if (state.hiddenApps.isNotEmpty() && homeItems) {
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
                            if (indexStyle == IndexStyle.WAVE) {
                                // Em onda a lista vira a seção da letra, e o
                                // cabeçalho dela é a primeira linha.
                                peekLetter = letter
                                scope.launch { listState.scrollToItem(0) }
                            } else {
                                state.sectionIndex[letter]?.let { index ->
                                    // Índice da linha -> índice do item: os
                                    // favoritos e os widgets valem uma posição
                                    // cada, e o bloco de notificações aberto
                                    // acima do destino, outra.
                                    val target = index + leadingItems +
                                        if (expandedRow != null && expandedRow < index) 1 else 0
                                    scope.launch { listState.scrollToItem(target) }
                                }
                            }
                        },
                        onHome = goHome,
                        style = indexStyle,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }

                // A mensagem só sobra quando nenhuma seção achou nada. Se só a
                // web existe, ela aparece sozinha — buscar lá fora é uma resposta.
                val nothingFound = shownRows.isEmpty() && extras.calculation == null &&
                    extras.shortcuts.isEmpty() && extras.contacts.isEmpty() &&
                    extras.settings.isEmpty() && extras.web == null
                if (nothingFound && !state.loading && state.query.isNotEmpty()) {
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

    // O convite de virar padrão espera o onboarding terminar: duas boas-vindas
    // empilhadas seriam uma a mais.
    if (state.showWelcome && onboardingDone) {
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

    if (showUsage) {
        UsageSheet(
            usage = usageToday,
            apps = usageApps,
            limits = usageSettings.limitsMinutes,
            repository = repository,
            onSetLimit = viewModel::onSetLimit,
            onDismiss = { showUsage = false },
        )
    }

    // A folha de pausa é o caminho de um app que estourou o limite: ela some
    // sozinha quando o ViewModel limpa o pedido, em qualquer das duas saídas.
    pausePrompt?.let { prompt ->
        PauseSheet(
            prompt = prompt,
            repository = repository,
            onConfirm = viewModel::onPauseConfirmed,
            onDismiss = viewModel::onPauseDismissed,
        )
    }
}

/** Visível ao source set de prévias (`src/screenshotTest`), que remonta a lista. */
@Composable
internal fun SectionHeader(letter: Char) {
    Text(
        text = letter.toString(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        // Cabeçalho de seção: o leitor de tela salta de letra em letra por ele.
        modifier = Modifier
            .semantics { heading() }
            .padding(top = 12.dp, bottom = 4.dp),
    )
}

/** Idem: a prévia de screenshot desenha as mesmas linhas que a home desenha. */
@Composable
internal fun AppRow(
    entry: AppEntry,
    favorite: Boolean,
    repository: IconSource,
    /** Nunca `repository.launch` direto: o gate da pausa está no ViewModel. */
    onLaunch: () -> Unit,
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
                role = Role.Button,
                onClick = onLaunch,
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
            val favoriteLabel = stringResource(R.string.favorite_marker)
            Text(
                text = "•",
                color = MaterialTheme.colorScheme.primary,
                // Sem isto o leitor de tela anuncia o caractere do marcador.
                modifier = Modifier.semantics { contentDescription = favoriteLabel },
            )
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
            .combinedClickable(role = Role.Button, onClick = onClick)
            .padding(top = 24.dp, bottom = 32.dp),
    )
}
