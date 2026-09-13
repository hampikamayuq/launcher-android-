package app.cascata.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cascata.launcher.data.theme.ThemeSettings
import app.cascata.launcher.ui.HomeScreen
import app.cascata.launcher.ui.theme.CascataTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.File

class HomeActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels {
        val app = application as CascataApp
        HomeViewModel.Factory(app.appRepository, app.launcherPrefs)
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

            // Cor do papel de parede: lida uma vez (o `onStart`) e relida a cada
            // troca de fundo. O WallpaperManager é binder — não na main thread.
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
                HomeScreen(viewModel = viewModel, repository = app.appRepository)
            }
        }
    }

    /** O usuário pode ter trocado a home padrão nas configurações enquanto estávamos fora. */
    override fun onResume() {
        super.onResume()
        viewModel.refreshDefaultLauncher()
        customFont = (application as CascataApp).fontStore.customFile()
    }
}
