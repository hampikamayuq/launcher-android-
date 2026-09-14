package app.cascata.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.cascata.launcher.data.glance.GlanceSettings
import app.cascata.launcher.data.theme.ThemeSettings
import app.cascata.launcher.data.widgets.WidgetLayout
import app.cascata.launcher.data.widgets.moveSlot
import app.cascata.launcher.data.widgets.removeWidget
import app.cascata.launcher.data.widgets.resizeSlot
import app.cascata.launcher.data.widgets.setActive
import app.cascata.launcher.ui.HomeScreen
import app.cascata.launcher.ui.onboarding.OnboardingScreen
import app.cascata.launcher.ui.theme.CascataTheme
import app.cascata.launcher.ui.widgets.WidgetActions
import app.cascata.launcher.widgets.WidgetPickerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HomeActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels {
        val app = application as CascataApp
        HomeViewModel.Factory(
            app.appRepository,
            app.launcherPrefs,
            app.notificationStore,
            app.notificationPrefs,
            // Contexto da aplicação: a resposta direta dispara um PendingIntent
            // que pode sobreviver a esta Activity.
            applicationContext,
            app.searchPrefs,
            app.contactsSource,
            app.usageSource,
            app.usagePrefs,
            app.usageAccess,
        )
    }

    /**
     * A fonte personalizada é um arquivo, não uma preferência: nada avisa quando
     * ela muda. Relemos ao voltar da tela de configurações, que é o único lugar
     * onde ela pode ter sido importada ou removida.
     */
    private var customFont by mutableStateOf<File?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as CascataApp
        customFont = app.fontStore.customFile()

        setContent {
            val settings by app.themePrefs.settings
                .collectAsStateWithLifecycle(initialValue = ThemeSettings.DEFAULT)
            // Quais cards do topo estão ligados. Nada é consultado pelos que não estão.
            val glance by app.glancePrefs.settings
                .collectAsStateWithLifecycle(initialValue = GlanceSettings.DEFAULT)

            // Cor do papel de parede: lida uma vez (o `onStart`) e relida a cada
            // troca de fundo. O WallpaperManager é binder — não na main thread.
            val wallpaperSeed by produceState<Int?>(null) {
                app.wallpaperColors.changes()
                    .onStart { emit(Unit) }
                    .collect {
                        value = withContext(Dispatchers.IO) { app.wallpaperColors.primaryArgb() }
                    }
            }

            // Os widgets colocados. Lista vazia é o caso normal de quem nunca
            // adicionou nenhum: a área nem chega a virar item da lista.
            val widgetLayout by app.widgetPrefs.layout
                .collectAsStateWithLifecycle(initialValue = WidgetLayout.EMPTY)

            // Nulo enquanto a preferência não chegou: desenhar a home e trocá-la
            // pelo onboarding um quadro depois daria um piscar a cada abertura.
            val onboardingDone: Boolean? by app.onboardingPrefs.done
                .collectAsStateWithLifecycle(initialValue = null)

            val widgetActions = remember {
                WidgetActions(
                    onResize = { slotId, cells -> updateWidgets { it.resizeSlot(slotId, cells) } },
                    onMove = { slotId, delta -> updateWidgets { it.moveSlot(slotId, delta) } },
                    // O id volta para o host antes de sair do layout: sem isso
                    // ele ficaria alocado, e o provedor continuaria atualizando.
                    onRemove = { id ->
                        app.widgetHost.deleteId(id)
                        updateWidgets { it.removeWidget(id) }
                    },
                    onSetActive = { slotId, index -> updateWidgets { it.setActive(slotId, index) } },
                    onAddToSlot = { slotId -> openWidgetPicker(slotId) },
                )
            }

            CascataTheme(
                settings = settings,
                customFont = customFont,
                wallpaperSeed = wallpaperSeed,
            ) {
                when (onboardingDone) {
                    // Ainda lendo a preferência: nem home nem boas-vindas.
                    null -> Unit

                    false -> OnboardingScreen(
                        repository = app.appRepository,
                        density = settings.density,
                        // Gravado no toque: sair do onboarding já deixa a lista
                        // no espaçamento escolhido.
                        onDensityChange = { chosen ->
                            lifecycleScope.launch {
                                app.themePrefs.update { it.copy(density = chosen) }
                            }
                        },
                        onFinish = { lifecycleScope.launch { app.onboardingPrefs.markDone() } },
                    )

                    else -> HomeScreen(
                        viewModel = viewModel,
                        repository = app.appRepository,
                        clockStyle = settings.clockStyle,
                        glance = glance,
                        alarmSource = app.alarmSource,
                        batterySource = app.batterySource,
                        calendarSource = app.calendarSource,
                        weatherSource = app.weatherSource,
                        mediaSource = app.mediaSource,
                        usageSource = app.usageSource,
                        widgetLayout = widgetLayout,
                        widgetHost = app.widgetHost,
                        widgetActions = widgetActions,
                        // O convite de virar padrão é para quem já passou pelas
                        // boas-vindas — ele não sobe por cima delas.
                        onboardingDone = true,
                    )
                }
            }
        }
    }

    /**
     * O host só escuta enquanto a home está na tela: widget atualizando com o
     * launcher em segundo plano não tem onde desenhar. `stopListening` também é
     * o que solta as `AppWidgetHostView` que saíram da composição.
     */
    override fun onStart() {
        super.onStart()
        (application as CascataApp).widgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        (application as CascataApp).widgetHost.stopListening()
    }

    /** Uma transação do DataStore por toque; o Flow devolve o layout novo. */
    private fun updateWidgets(transform: (WidgetLayout) -> WidgetLayout) {
        val app = application as CascataApp
        lifecycleScope.launch { app.widgetPrefs.update(transform) }
    }

    /** Sem [slotId] o escolhido abre um slot novo no fim; com ele, empilha. */
    private fun openWidgetPicker(slotId: Int?) {
        val intent = Intent(this, WidgetPickerActivity::class.java)
        if (slotId != null) intent.putExtra(WidgetPickerActivity.EXTRA_SLOT_ID, slotId)
        runCatching { startActivity(intent) }
    }

    /** O usuário pode ter trocado a home padrão nas configurações enquanto estávamos fora. */
    override fun onResume() {
        super.onResume()
        viewModel.refreshDefaultLauncher()
        // O acesso ao uso pode ter sido concedido na tela do sistema, e o tempo
        // de hoje andou enquanto estávamos em outro app.
        viewModel.refreshUsage()
        customFont = (application as CascataApp).fontStore.customFile()
    }
}
