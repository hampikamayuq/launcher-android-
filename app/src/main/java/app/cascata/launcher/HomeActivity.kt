package app.cascata.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cascata.launcher.ui.HomeScreen
import app.cascata.launcher.ui.theme.CascataTheme

class HomeActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels {
        val app = application as CascataApp
        HomeViewModel.Factory(app.appRepository, app.launcherPrefs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as CascataApp

        setContent {
            CascataTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                HomeScreen(
                    state = state,
                    repository = app.appRepository,
                    onQueryChange = viewModel::onQueryChange,
                    onToggleFavorite = viewModel::onToggleFavorite,
                )
            }
        }
    }

    /** O usuário pode ter trocado a home padrão nas configurações enquanto estávamos fora. */
    override fun onResume() {
        super.onResume()
        viewModel.refreshDefaultLauncher()
    }
}
