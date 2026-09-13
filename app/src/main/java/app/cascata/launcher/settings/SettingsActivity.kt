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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cascata.launcher.CascataApp
import app.cascata.launcher.R
import app.cascata.launcher.data.iconpack.IconPackRepository
import app.cascata.launcher.data.theme.FontStore
import app.cascata.launcher.data.theme.ThemePrefs
import app.cascata.launcher.data.theme.ThemeSettings
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
                    customFont = customFont,
                    themePrefs = app.themePrefs,
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
    customFont: File?,
    themePrefs: ThemePrefs,
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
