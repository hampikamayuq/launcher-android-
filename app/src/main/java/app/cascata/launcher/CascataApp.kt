package app.cascata.launcher

import android.app.Application
import app.cascata.launcher.crash.CrashReporter
import app.cascata.launcher.data.AppRepository
import app.cascata.launcher.data.LauncherPrefs
import app.cascata.launcher.data.backup.BackupManager
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
import app.cascata.launcher.data.onboarding.OnboardingPrefs
import app.cascata.launcher.data.search.ContactsSource
import app.cascata.launcher.data.search.SearchPrefs
import app.cascata.launcher.data.theme.FontStore
import app.cascata.launcher.data.theme.ThemePrefs
import app.cascata.launcher.data.theme.WallpaperColorsSource
import app.cascata.launcher.data.usage.UsageAccess
import app.cascata.launcher.data.usage.UsagePrefs
import app.cascata.launcher.data.usage.UsageSource
import app.cascata.launcher.data.widgets.WidgetHostManager
import app.cascata.launcher.data.widgets.WidgetPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Dependências vivem aqui, criadas sob demanda. Um launcher desse tamanho não
 * precisa de framework de injeção — o custo de inicialização aparece no tempo
 * até a primeira tela.
 */
class CascataApp : Application() {

    /**
     * Antes de qualquer outra coisa: um launcher que quebra deixa o aparelho sem
     * tela inicial, e sem isto a falha some sem deixar rastro no aparelho de quem
     * a viu. Instalar o handler não lê disco nem cria nenhuma das dependências
     * abaixo — o custo até a primeira tela continua o mesmo.
     */
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }

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

    // Fase 5. O host precisa existir no processo, e não na Activity: o receiver de
    // restauração e a home falam com o mesmo id de host.
    val widgetPrefs: WidgetPrefs by lazy { WidgetPrefs(this) }
    val widgetHost: WidgetHostManager by lazy { WidgetHostManager(this) }

    // Fase 6. Criar a fonte de contatos não lê nada: sem a opção ligada e sem a
    // permissão concedida ela devolve lista vazia sem tocar no provedor.
    val searchPrefs: SearchPrefs by lazy { SearchPrefs(this) }
    val contactsSource: ContactsSource by lazy { ContactsSource(this) }

    // Fase 7. Nada de uso é persistido: a fonte lê o histórico que o sistema já
    // mantém, e o DataStore guarda só limites, pausa e o card.
    val usagePrefs: UsagePrefs by lazy { UsagePrefs(this) }
    val usageAccess: UsageAccess by lazy { UsageAccess(this) }
    val usageSource: UsageSource by lazy { UsageSource(this, usageAccess) }

    // Fase 8. O gerenciador junta os DataStores que já existem: criá-lo não lê
    // nada, e a versão do app só entra no envelope do arquivo exportado.
    val onboardingPrefs: OnboardingPrefs by lazy { OnboardingPrefs(this) }
    val backupManager: BackupManager by lazy {
        BackupManager(
            launcherPrefs = launcherPrefs,
            themePrefs = themePrefs,
            glancePrefs = glancePrefs,
            notificationPrefs = notificationPrefs,
            searchPrefs = searchPrefs,
            usagePrefs = usagePrefs,
            widgetPrefs = widgetPrefs,
            appVersion = appVersion,
        )
    }

    /** O que o usuário vê como versão; "?" se o próprio pacote não souber dizer. */
    private val appVersion: String
        get() = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
}
