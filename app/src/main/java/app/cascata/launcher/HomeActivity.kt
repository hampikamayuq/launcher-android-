package app.cascata.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
                HomeScreen(viewModel = viewModel, repository = app.appRepository)
            }
        }
    }

    /** O usuário pode ter trocado a home padrão nas configurações enquanto estávamos fora. */
    override fun onResume() {
        super.onResume()
        viewModel.refreshDefaultLauncher()
    }
}
