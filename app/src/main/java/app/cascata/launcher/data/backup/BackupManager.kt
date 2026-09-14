package app.cascata.launcher.data.backup

import app.cascata.launcher.data.LauncherPrefs
import app.cascata.launcher.data.glance.GlancePrefs
import app.cascata.launcher.data.notifications.NotificationPrefs
import app.cascata.launcher.data.search.SearchPrefs
import app.cascata.launcher.data.theme.ThemePrefs
import app.cascata.launcher.data.usage.UsagePrefs
import app.cascata.launcher.data.widgets.WidgetLayout
import app.cascata.launcher.data.widgets.WidgetPrefs
import app.cascata.launcher.data.widgets.allWidgetIds
import kotlinx.coroutines.flow.first

/**
 * A ponte entre os DataStores e o arquivo `.cascata-backup`. Não faz I/O de
 * arquivo nem toca no `AppWidgetHost`: quem abre o SAF e quem libera ids de
 * widget é a UI, que tem a Activity e o host.
 *
 * Fluxo esperado da UI:
 *  - exportar: [collect] -> [encode] -> escrever no `Uri` do SAF;
 *  - importar: ler o `Uri` -> [decode] -> `remapUserHashes(perfis atuais)` -> [apply];
 *  - apagar tudo: [resetAll] e `WidgetHostManager.deleteId` em cada id devolvido.
 */
class BackupManager(
    private val launcherPrefs: LauncherPrefs,
    private val themePrefs: ThemePrefs,
    private val glancePrefs: GlancePrefs,
    private val notificationPrefs: NotificationPrefs,
    private val searchPrefs: SearchPrefs,
    private val usagePrefs: UsagePrefs,
    private val widgetPrefs: WidgetPrefs,
    private val appVersion: String,
) {

    /** O estado atual de tudo o que é persistido — o primeiro valor de cada Flow. */
    suspend fun collect(): BackupPayload = BackupPayload(
        favorites = launcherPrefs.favorites.first(),
        hidden = launcherPrefs.hidden.first(),
        aliases = launcherPrefs.aliases.first(),
        theme = themePrefs.settings.first(),
        glance = glancePrefs.settings.first(),
        notifications = notificationPrefs.settings.first(),
        search = searchPrefs.settings.first(),
        usage = usagePrefs.settings.first(),
        widgets = widgetPrefs.layout.first(),
    )

    /**
     * Grava o backup por cima do que existe. É substituição, não mistura: o que
     * não está no arquivo deixa de existir — senão o apelido apagado no aparelho
     * antigo voltaria do nada.
     *
     * Com [includeWidgets] falso o layout atual fica intocado (a restauração "sem
     * widgets"); com ele verdadeiro os widgets do arquivo entram como estão, e os
     * ids que não valem neste aparelho aparecem como "Widget indisponível".
     * Os ids que estavam na home antes da restauração ficam órfãos no host — a UI
     * que quiser liberá-los lê `widgetPrefs.layout.first().allWidgetIds()` antes
     * de chamar este método.
     */
    suspend fun apply(payload: BackupPayload, includeWidgets: Boolean = true) {
        launcherPrefs.restore(payload.favorites, payload.hidden, payload.aliases)
        themePrefs.replace(payload.theme)
        glancePrefs.replace(payload.glance)
        notificationPrefs.replace(payload.notifications)
        searchPrefs.replace(payload.search)
        usagePrefs.replace(payload.usage)
        if (includeWidgets) widgetPrefs.replace(payload.widgets)
    }

    /**
     * Devolve cada DataStore ao padrão de app recém-instalado e responde com os
     * ids de widget que ficaram órfãos: eles seguem alocados no
     * `AppWidgetHost` até alguém chamar `deleteId`, e quem tem o host é a UI.
     */
    suspend fun resetAll(): List<Int> {
        val orphans = widgetPrefs.layout.first().allWidgetIds()
        launcherPrefs.reset()
        themePrefs.reset()
        glancePrefs.reset()
        notificationPrefs.reset()
        searchPrefs.reset()
        usagePrefs.reset()
        widgetPrefs.replace(WidgetLayout.EMPTY)
        return orphans
    }

    /** O texto do arquivo, já com a versão do app que o gerou no envelope. */
    fun encode(payload: BackupPayload): String = BackupFile.encode(payload, appVersion)

    fun decode(text: String): Result<BackupPayload> = BackupFile.decode(text)
}
