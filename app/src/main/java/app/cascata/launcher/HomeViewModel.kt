package app.cascata.launcher

import android.content.Context
import android.content.pm.ShortcutInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.cascata.launcher.data.AppEntry
import app.cascata.launcher.data.AppRepository
import app.cascata.launcher.data.LauncherPrefs
import app.cascata.launcher.data.notifications.AppNotification
import app.cascata.launcher.data.notifications.NotificationAction
import app.cascata.launcher.data.notifications.NotificationPrefs
import app.cascata.launcher.data.notifications.NotificationSettings
import app.cascata.launcher.data.notifications.NotificationStore
import app.cascata.launcher.data.notifications.fire
import app.cascata.launcher.data.notifications.open
import app.cascata.launcher.data.notifications.reply
import app.cascata.launcher.data.notifications.visibleNotifications
import app.cascata.launcher.data.withAlias
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator

data class HomeUiState(
    val rows: List<Row<AppEntry>> = emptyList(),
    /** Favoritos na ordem escolhida pelo usuário. */
    val favorites: List<AppEntry> = emptyList(),
    /** Escondidos da lista: só aparecem na tela de gerenciar. */
    val hiddenApps: List<AppEntry> = emptyList(),
    /** Letra -> índice da linha onde a seção começa. Alimenta o índice alfabético. */
    val sectionIndex: Map<Char, Int> = emptyMap(),
    val query: String = "",
    val loading: Boolean = true,
    val isDefaultLauncher: Boolean = false,
    /** Convite para virar a home, até o usuário aceitar ou dispensar. */
    val showWelcome: Boolean = false,
    /** Sem isso os atalhos de long-press vêm vazios — é privilégio do launcher padrão. */
    val hasShortcutHost: Boolean = false,
)

class HomeViewModel(
    private val repository: AppRepository,
    private val prefs: LauncherPrefs,
    /** Objeto de processo: quem o alimenta é o serviço, criado pelo sistema. */
    private val notificationStore: NotificationStore = NotificationStore,
    /**
     * Fase 4. Nulos até a UI passá-los (a HomeActivity ainda usa o construtor de
     * dois argumentos): sem eles o recurso fica desligado e a lista não muda.
     */
    private val notificationPrefs: NotificationPrefs? = null,
    /** Contexto da aplicação: `PendingIntent.send` da resposta direta exige um. */
    private val appContext: Context? = null,
) : ViewModel() {

    /** O que depende do sistema, não do DataStore; re-lido a cada onResume. */
    private data class SystemState(
        val isDefaultLauncher: Boolean = false,
        val hasShortcutHost: Boolean = false,
        val welcomeDismissed: Boolean = false,
    )

    private data class Stored(
        val favorites: List<String>,
        val hidden: Set<String>,
        val aliases: Map<String, String>,
    )

    private val query = MutableStateFlow("")
    private val system = MutableStateFlow(SystemState())

    private val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

    private val builder = HomeStateBuilder<AppEntry>(
        key = { it.key },
        label = { it.label },
        normalized = { it.normalizedLabel },
        section = { it.section },
        withAlias = { entry, alias -> entry.withAlias(alias) },
        compareLabels = { a, b -> collator.compare(a, b) },
    )

    private val stored = combine(prefs.favorites, prefs.hidden, prefs.aliases, ::Stored)

    val state: StateFlow<HomeUiState> =
        combine(repository.apps, stored, query, system) { apps, saved, q, sys ->
            val list = builder.build(apps, saved.favorites, saved.hidden, saved.aliases, q)
            HomeUiState(
                rows = list.rows,
                favorites = list.favorites,
                hiddenApps = list.hidden,
                sectionIndex = list.sectionIndex,
                query = q,
                loading = apps.isEmpty(),
                isDefaultLauncher = sys.isDefaultLauncher,
                showWelcome = !sys.isDefaultLauncher && !sys.welcomeDismissed,
                hasShortcutHost = sys.hasShortcutHost,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** O serviço está conectado? É o que separa "ligado" de "ligado e funcionando". */
    val notificationsConnected: StateFlow<Boolean> get() = notificationStore.connected

    /** O que o usuário escolheu para as notificações; o acesso real é do sistema. */
    val notificationSettings: StateFlow<NotificationSettings> =
        (notificationPrefs?.settings ?: flowOf(NotificationSettings.DEFAULT))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationSettings.DEFAULT)

    /**
     * Por `appKey`, pronto para a lista cruzar com `Row.App.entry.appKey`. Some
     * inteiro com o recurso desligado, e some por app silenciado — o store
     * continua cheio, o que muda é o que a home enxerga.
     */
    val notifications: StateFlow<Map<String, List<AppNotification>>> =
        combine(notificationStore.byApp, notificationSettings) { byApp, settings ->
            visibleNotifications(byApp, settings)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        refreshDefaultLauncher()
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onClearQuery() {
        query.value = ""
    }

    fun onToggleFavorite(entry: AppEntry) {
        viewModelScope.launch { prefs.toggleFavorite(entry.key) }
    }

    /**
     * Ordem que o arraste já pediu mas o DataStore ainda não devolveu. Sem isso,
     * duas trocas seguidas partiriam da mesma foto antiga e a segunda desfaria a
     * primeira. Vale só enquanto o conjunto de favoritos for o mesmo.
     */
    private var pendingFavorites: List<String>? = null

    /** Índices são os da lista visível de favoritos, que é o que o usuário arrasta. */
    fun onMoveFavorite(fromIndex: Int, toIndex: Int) {
        val shown = state.value.favorites.map { it.key }
        val base = pendingFavorites?.takeIf { it.toSet() == shown.toSet() } ?: shown
        val moved = moveItem(base, fromIndex, toIndex) ?: return
        pendingFavorites = moved
        viewModelScope.launch {
            // Favoritos que não estão à vista (app oculto ou desinstalado) ficam no fim.
            val rest = prefs.favorites.first().filterNot { it in moved }
            prefs.setFavoritesOrder(moved + rest)
        }
    }

    fun onHide(entry: AppEntry) {
        viewModelScope.launch { prefs.setHidden(entry.key, true) }
    }

    fun onUnhide(entry: AppEntry) {
        viewModelScope.launch { prefs.setHidden(entry.key, false) }
    }

    /** Rótulo nulo ou em branco devolve o nome que o sistema dá ao app. */
    fun onRename(entry: AppEntry, newLabel: String?) {
        viewModelScope.launch { prefs.setAlias(entry.key, newLabel) }
    }

    fun onDismissWelcome() {
        system.value = system.value.copy(welcomeDismissed = true)
    }

    /** A Activity chama no onResume: o usuário pode ter trocado a home lá fora. */
    fun refreshDefaultLauncher() {
        system.value = system.value.copy(
            isDefaultLauncher = repository.isDefaultLauncher(),
            hasShortcutHost = repository.hasShortcutHostPermission(),
        )
    }

    suspend fun shortcutsFor(entry: AppEntry): List<ShortcutInfo> = repository.shortcuts(entry)

    /** Abre o que a notificação aponta; sem contexto não há como disparar nada. */
    fun onOpenNotification(notification: AppNotification): Boolean =
        appContext?.let { open(notification, it) } ?: false

    fun onDismissNotification(key: String) = notificationStore.dismiss(key)

    fun onDismissAll(appKey: String) = notificationStore.dismissAll(appKey)

    fun onFireAction(action: NotificationAction): Boolean = fire(action)

    fun onReply(action: NotificationAction, text: String): Boolean =
        appContext?.let { reply(action, text, it) } ?: false

    class Factory(
        private val repository: AppRepository,
        private val prefs: LauncherPrefs,
        private val notificationStore: NotificationStore = NotificationStore,
        private val notificationPrefs: NotificationPrefs? = null,
        private val appContext: Context? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(repository, prefs, notificationStore, notificationPrefs, appContext) as T
    }
}
