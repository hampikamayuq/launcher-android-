package app.cascata.launcher.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cascata.launcher.CascataApp
import app.cascata.launcher.R
import app.cascata.launcher.data.glance.CalendarSource
import app.cascata.launcher.data.glance.GlancePrefs
import app.cascata.launcher.data.glance.GlanceSettings
import app.cascata.launcher.data.glance.weather.WeatherCache
import app.cascata.launcher.data.glance.weather.WeatherSource
import app.cascata.launcher.data.AppRepository
import app.cascata.launcher.data.iconpack.IconPackRepository
import app.cascata.launcher.data.notifications.NotificationAccess
import app.cascata.launcher.data.notifications.NotificationPrefs
import app.cascata.launcher.data.notifications.NotificationSettings
import app.cascata.launcher.data.search.ContactsSource
import app.cascata.launcher.data.search.SearchPrefs
import app.cascata.launcher.data.search.SearchSettings
import app.cascata.launcher.data.theme.FontStore
import app.cascata.launcher.data.theme.ThemePrefs
import app.cascata.launcher.data.theme.ThemeSettings
import app.cascata.launcher.data.widgets.WidgetHostManager
import app.cascata.launcher.data.widgets.WidgetLayout
import app.cascata.launcher.data.widgets.WidgetPrefs
import app.cascata.launcher.ui.theme.CascataTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Uma tela só, rolável, com o mesmo [CascataTheme] da home: a tela de
 * configurações é o próprio preview do tema que está sendo editado.
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as CascataApp

        setContent {
            val settings by app.themePrefs.settings
                .collectAsStateWithLifecycle(initialValue = ThemeSettings.DEFAULT)
            val glance by app.glancePrefs.settings
                .collectAsStateWithLifecycle(initialValue = GlanceSettings.DEFAULT)
            val notifications by app.notificationPrefs.settings
                .collectAsStateWithLifecycle(initialValue = NotificationSettings.DEFAULT)
            val widgets by app.widgetPrefs.layout
                .collectAsStateWithLifecycle(initialValue = WidgetLayout.EMPTY)
            val search by app.searchPrefs.settings
                .collectAsStateWithLifecycle(initialValue = SearchSettings.DEFAULT)

            // O acesso a notificações é concedido numa tela do sistema: nada
            // avisa quando ele muda, então relemos ao voltar para cá.
            var listenerAccess by remember { mutableStateOf(false) }
            LifecycleResumeEffect(Unit) {
                listenerAccess = app.notificationAccess.hasListenerAccess()
                onPauseOrDispose { }
            }

            // Arquivo, não preferência: quem importa ou remove avisa por aqui.
            var customFont by remember { mutableStateOf(app.fontStore.customFile()) }

            val wallpaperSeed by produceState<Int?>(null) {
                app.wallpaperColors.changes()
                    .onStart { emit(Unit) }
                    .collect {
                        value = withContext(Dispatchers.IO) { app.wallpaperColors.primaryArgb() }
                    }
            }

            CascataTheme(
                settings = settings,
                customFont = customFont,
                wallpaperSeed = wallpaperSeed,
            ) {
                SettingsScreen(
                    settings = settings,
                    glance = glance,
                    notifications = notifications,
                    widgets = widgets,
                    search = search,
                    hasListenerAccess = listenerAccess,
                    customFont = customFont,
                    themePrefs = app.themePrefs,
                    glancePrefs = app.glancePrefs,
                    calendarSource = app.calendarSource,
                    weatherSource = app.weatherSource,
                    weatherCache = app.weatherCache,
                    notificationPrefs = app.notificationPrefs,
                    notificationAccess = app.notificationAccess,
                    widgetHost = app.widgetHost,
                    widgetPrefs = app.widgetPrefs,
                    searchPrefs = app.searchPrefs,
                    contactsSource = app.contactsSource,
                    appRepository = app.appRepository,
                    fontStore = app.fontStore,
                    iconPacks = app.iconPacks,
                    onCustomFontChanged = { customFont = app.fontStore.customFile() },
                    onBack = { finish() },
                )
            }
        }
    }
}

/** Grava uma mudança de aparência. Sem botão salvar: cada toque já é a gravação. */
typealias UpdateSettings = ((ThemeSettings) -> ThemeSettings) -> Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    settings: ThemeSettings,
    glance: GlanceSettings,
    notifications: NotificationSettings,
    widgets: WidgetLayout,
    search: SearchSettings,
    hasListenerAccess: Boolean,
    customFont: File?,
    themePrefs: ThemePrefs,
    glancePrefs: GlancePrefs,
    calendarSource: CalendarSource,
    weatherSource: WeatherSource,
    weatherCache: WeatherCache,
    notificationPrefs: NotificationPrefs,
    notificationAccess: NotificationAccess,
    widgetHost: WidgetHostManager,
    widgetPrefs: WidgetPrefs,
    searchPrefs: SearchPrefs,
    contactsSource: ContactsSource,
    appRepository: AppRepository,
    fontStore: FontStore,
    iconPacks: IconPackRepository,
    onCustomFontChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val update: UpdateSettings = { transform -> scope.launch { themePrefs.update(transform) } }
    val message: (String) -> Unit = { text -> scope.launch { snackbar.showSnackbar(text) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            AppearanceSection(settings = settings, update = update)
            GlanceSection(
                settings = settings,
                glance = glance,
                glancePrefs = glancePrefs,
                calendarSource = calendarSource,
                weatherSource = weatherSource,
                weatherCache = weatherCache,
                update = update,
            )
            NotificationsSection(
                settings = notifications,
                prefs = notificationPrefs,
                access = notificationAccess,
                hasAccess = hasListenerAccess,
                repository = appRepository,
            )
            WidgetsSection(
                layout = widgets,
                host = widgetHost,
                prefs = widgetPrefs,
                repository = appRepository,
            )
            SearchSection(
                search = search,
                prefs = searchPrefs,
                contactsSource = contactsSource,
            )
            FontSection(
                settings = settings,
                customFont = customFont,
                fontStore = fontStore,
                update = update,
                onCustomFontChanged = onCustomFontChanged,
                onMessage = message,
            )
            IconPackSection(settings = settings, iconPacks = iconPacks, update = update)
            WallpaperSection()
            ThemeSection(settings = settings, themePrefs = themePrefs, onMessage = message)
            AboutSection()
        }
    }
}
