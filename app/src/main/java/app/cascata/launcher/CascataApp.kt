package app.cascata.launcher

import android.app.Application
import app.cascata.launcher.data.AppRepository
import app.cascata.launcher.data.LauncherPrefs
import app.cascata.launcher.data.glance.AlarmSource
import app.cascata.launcher.data.glance.BatterySource
import app.cascata.launcher.data.glance.CalendarSource
import app.cascata.launcher.data.glance.GlancePrefs
import app.cascata.launcher.data.glance.MediaSource
import app.cascata.launcher.data.glance.weather.WeatherCache
import app.cascata.launcher.data.glance.weather.WeatherSource
import app.cascata.launcher.data.glance.weather.WeatherSourceFactory
import app.cascata.launcher.data.iconpack.IconPackRepository
import app.cascata.launcher.data.notifications.NotificationAccess
import app.cascata.launcher.data.notifications.NotificationPrefs
import app.cascata.launcher.data.notifications.NotificationStore
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

    // Fase 3. Cada fonte só faz trabalho quando alguém assina o Flow dela, então
    // criar o objeto de um card desligado não custa nada.
    val glancePrefs: GlancePrefs by lazy { GlancePrefs(this) }
    val alarmSource: AlarmSource by lazy { AlarmSource(this) }
    val batterySource: BatterySource by lazy { BatterySource(this) }
    val calendarSource: CalendarSource by lazy { CalendarSource(this) }
    val weatherCache: WeatherCache by lazy { WeatherCache(this) }

    /** Implementação do flavor: real na edição `full`, inerte na `lite`. */
    val weatherSource: WeatherSource by lazy { WeatherSourceFactory.create(this, weatherCache) }

    // Fase 4. O store é um objeto de processo porque quem o alimenta é o
    // NotificationListenerService, instanciado pelo sistema — não há construtor
    // onde injetar nada. Exposto aqui para o ViewModel não ir buscar um global.
    val notificationStore: NotificationStore get() = NotificationStore
    val notificationPrefs: NotificationPrefs by lazy { NotificationPrefs(this) }
    val notificationAccess: NotificationAccess by lazy { NotificationAccess(this) }
    val mediaSource: MediaSource by lazy { MediaSource(this) }
}
