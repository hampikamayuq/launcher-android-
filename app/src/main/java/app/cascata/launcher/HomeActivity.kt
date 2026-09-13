package app.cascata.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cascata.launcher.ui.HomeScreen
import app.cascata.launcher.ui.theme.CascataTheme

class HomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as CascataApp

        setContent {
            CascataTheme {
                val viewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.Factory(app.appRepository, app.favoritesStore)
                )
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
}
