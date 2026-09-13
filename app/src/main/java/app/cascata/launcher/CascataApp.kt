package app.cascata.launcher

import android.app.Application
import app.cascata.launcher.data.AppRepository
import app.cascata.launcher.data.LauncherPrefs
import app.cascata.launcher.data.iconpack.IconPackRepository
import app.cascata.launcher.data.theme.FontStore
import app.cascata.launcher.data.theme.ThemePrefs
import app.cascata.launcher.data.theme.WallpaperColorsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Dependências vivem aqui, criadas sob demanda. Um launcher desse tamanho não
 * precisa de framework de injeção — o custo de inicialização aparece no tempo
 * até a primeira tela.
 */
class CascataApp : Application() {

    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob()) }
    val launcherPrefs: LauncherPrefs by lazy { LauncherPrefs(this) }
    val themePrefs: ThemePrefs by lazy { ThemePrefs(this) }
    val iconPacks: IconPackRepository by lazy { IconPackRepository(this) }
    val fontStore: FontStore by lazy { FontStore(this) }
    val wallpaperColors: WallpaperColorsSource by lazy { WallpaperColorsSource(this) }
    val appRepository: AppRepository by lazy {
        AppRepository(this, appScope, iconPacks, themePrefs)
    }
}
