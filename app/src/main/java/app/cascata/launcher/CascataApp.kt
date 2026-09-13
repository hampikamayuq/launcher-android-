package app.cascata.launcher

import android.app.Application
import app.cascata.launcher.data.AppRepository
import app.cascata.launcher.data.LauncherPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Dependências vivem aqui, criadas sob demanda. Um launcher desse tamanho não
 * precisa de framework de injeção — o custo de inicialização aparece no tempo
 * até a primeira tela.
 */
class CascataApp : Application() {

    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob()) }
    val appRepository: AppRepository by lazy { AppRepository(this, appScope) }
    val launcherPrefs: LauncherPrefs by lazy { LauncherPrefs(this) }
}
