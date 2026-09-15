package app.cascata.launcher.data.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import app.cascata.launcher.crash.degraded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL`, copiado porque a
 * constante só existe da API 28 em diante e o mínimo aqui é 26.
 */
private const val FEATURE_CONFIGURATION_OPTIONAL = 4

/** Tag dos avisos desta área: sem widgets a home abre igual, só sem a faixa. */
private const val TAG = "CascataWidgets"

/** Um provedor instalado, já com os rótulos resolvidos — o seletor não toca no PackageManager. */
data class WidgetProvider(
    val info: AppWidgetProviderInfo,
    val user: UserHandle,
    val label: String,
    val appLabel: String,
    val appPackage: String,
    val minCells: Int,
    val resizable: Boolean,
)

/**
 * O host de widgets do launcher.
 *
 * Um launcher de terceiro **não** tem `BIND_APPWIDGET` concedida — é permissão de
 * sistema. O caminho é tentar [bindIfAllowed] e, quando ele falha, mandar o
 * usuário pelo [bindIntent]: o diálogo do Android pergunta uma vez e o sistema
 * lembra da autorização (é o que o manifesto declara a permissão para permitir).
 *
 * `startListening`/`stopListening` acompanham a Activity (onStart/onStop) — quem
 * chama é a UI; aqui só ficam expostos.
 */
class WidgetHostManager(private val context: Context) {

    /**
     * Os dois são opcionais de propósito. Num aparelho sem `FEATURE_APP_WIDGETS`
     * — televisores, relógios, perfis administrados, e o que cada fabricante
     * resolve desligar — `AppWidgetManager.getInstance` devolve null e o
     * construtor do host lança. Como este objeto é criado no `onStart` da home
     * (por `by lazy`, na primeira chamada a `startListening`), uma exceção aqui
     * dentro morre *antes* de qualquer `runCatching` interno e derruba a tela
     * inicial inteira por causa de um recurso que o usuário talvez nem use.
     *
     * Com os dois nulos o launcher abre sem área de widgets: [available] é false,
     * a lista de provedores vem vazia e nenhum slot é desenhado.
     */
    private val host: AppWidgetHost? = runCatching { AppWidgetHost(context, WIDGET_HOST_ID) }
        .onFailure { degraded(TAG, "sem host de widgets", it) }
        .getOrNull()

    private val widgetManager: AppWidgetManager? = runCatching { AppWidgetManager.getInstance(context) }
        .onFailure { degraded(TAG, "sem AppWidgetManager", it) }
        .getOrNull()

    private val packageManager = runCatching { context.packageManager }.getOrNull()

    /**
     * Se este aparelho tem widgets. Falso desliga a área inteira: sem host não há
     * o que alocar, ligar, desenhar nem ouvir.
     */
    val available: Boolean get() = host != null && widgetManager != null

    /** Altura de uma célula em px, para converter o mínimo que o provedor pede. */
    private val cellPx: Int
        get() = (CELL_HEIGHT_DP * context.resources.displayMetrics.density).toInt()

    /** Sem widget na tela o host não tem o que ouvir; e alguns aparelhos lançam ao parar. */
    fun startListening() {
        runCatching { host?.startListening() }
    }

    fun stopListening() {
        runCatching { host?.stopListening() }
    }

    /** [AppWidgetManager.INVALID_APPWIDGET_ID] quando não há host: ninguém tem o que colocar. */
    fun allocateId(): Int = runCatching { host?.allocateAppWidgetId() }
        .onFailure { degraded(TAG, "não foi possível alocar um id", it) }
        .getOrNull()
        ?: AppWidgetManager.INVALID_APPWIDGET_ID

    fun deleteId(appWidgetId: Int) {
        runCatching { host?.deleteAppWidgetId(appWidgetId) }
    }

    /** Null quando o id não está mais ligado a provedor nenhum (app removido, bind perdido). */
    fun providerInfo(appWidgetId: Int): AppWidgetProviderInfo? =
        runCatching { widgetManager?.getAppWidgetInfo(appWidgetId) }.getOrNull()

    /** True se o sistema já nos deixa ligar o id ao provedor sem passar pelo diálogo. */
    fun bindIfAllowed(appWidgetId: Int, provider: ComponentName, user: UserHandle?): Boolean = runCatching {
        val manager = widgetManager ?: return@runCatching false
        if (user != null) {
            manager.bindAppWidgetIdIfAllowed(appWidgetId, user, provider, null)
        } else {
            manager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
        }
    }.getOrDefault(false)

    /** O diálogo do sistema: é ele quem concede o bind que [bindIfAllowed] não conseguiu. */
    fun bindIntent(appWidgetId: Int, provider: ComponentName, user: UserHandle?): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
            if (user != null) putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, user)
        }

    /**
     * Se a tela de configuração do widget é obrigatória. Da API 28 em diante o
     * provedor pode marcá-la como opcional; antes disso, ter `configure` já quer
     * dizer que ela precisa rodar antes do widget aparecer.
     */
    fun needsConfiguration(info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true
        return info.widgetFeatures and FEATURE_CONFIGURATION_OPTIONAL == 0
    }

    /**
     * Abre a configuração pelo host (e não por `startActivityForResult` direto):
     * é o host que tem permissão para lançar a Activity de outro app com o id
     * alocado. Pode falhar se o provedor sumiu no meio do caminho.
     */
    fun startConfigure(activity: Activity, appWidgetId: Int, requestCode: Int) {
        runCatching {
            host?.startAppWidgetConfigureActivityForResult(activity, appWidgetId, 0, requestCode, null)
        }
    }

    /**
     * A view do widget, ou null quando não dá para criá-la — sem host, ou porque
     * o processo do outro app caiu no meio da inflação do `RemoteViews`. Quem
     * desenha mostra a moldura de "widget indisponível" no lugar.
     */
    fun createView(context: Context, appWidgetId: Int, info: AppWidgetProviderInfo): AppWidgetHostView? =
        runCatching { host?.createView(context, appWidgetId, info)?.apply { setAppWidget(appWidgetId, info) } }
            .onFailure { degraded(TAG, "widget $appWidgetId não pôde ser desenhado", it) }
            .getOrNull()

    /**
     * Avisa o widget do tamanho que ele ganhou. A forma com `Bundle` só existe da
     * API 31 em diante; esta aqui funciona desde o mínimo do projeto. Largura e
     * altura mínimas e máximas são as mesmas: a faixa é do tamanho que é.
     */
    fun updateSize(view: AppWidgetHostView, widthDp: Int, heightDp: Int) {
        runCatching { view.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp) }
    }

    /**
     * Tudo o que dá para colocar na home, de todos os perfis (trabalho incluso),
     * ordenado por app e depois por widget — é a ordem em que o seletor mostra.
     */
    fun installedProviders(): List<WidgetProvider> {
        val manager = widgetManager ?: return emptyList()
        val cell = runCatching { cellPx }.getOrDefault(0)
        // Um perfil que recusa a consulta (Secure Folder, perfil administrado)
        // some da lista; os outros continuam valendo.
        val providers = profiles().flatMap { user ->
            runCatching { manager.getInstalledProvidersForProfile(user) }
                .onFailure { degraded(TAG, "perfil sem provedores de widget", it) }
                .getOrDefault(emptyList())
        }
        return providers.mapNotNull { info -> runCatching { toProvider(info, cell) }.getOrNull() }
            .sortedWith(
                compareBy<WidgetProvider, String>(String.CASE_INSENSITIVE_ORDER) { it.appLabel }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
            )
    }

    /** A imagem do seletor: a prévia que o provedor desenhou ou, na falta, o ícone dele. */
    suspend fun loadPreview(provider: WidgetProvider): Drawable? = withContext(Dispatchers.IO) {
        runCatching { provider.info.loadPreviewImage(context, 0) }.getOrNull()
            ?: runCatching { provider.info.loadIcon(context, 0) }.getOrNull()
    }

    private fun profiles(): List<UserHandle> {
        val manager = runCatching { context.getSystemService(UserManager::class.java) }.getOrNull()
            ?: return emptyList()
        return runCatching { manager.userProfiles }
            .onFailure { degraded(TAG, "não foi possível listar os perfis", it) }
            .getOrDefault(emptyList())
    }

    private fun toProvider(info: AppWidgetProviderInfo, cell: Int): WidgetProvider? {
        val component = info.provider ?: return null
        val user = info.profile ?: return null
        val label = runCatching { packageManager?.let { info.loadLabel(it) } }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: component.className.substringAfterLast('.')
        val appPackage = component.packageName
        val appLabel = runCatching {
            val pm = packageManager ?: return@runCatching null
            pm.getApplicationLabel(pm.getApplicationInfo(appPackage, 0)).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: appPackage
        // O que o provedor aceita encolher; sem isso, o tamanho que ele pede de saída.
        val minHeightPx = info.minResizeHeight.takeIf { it > 0 } ?: info.minHeight
        return WidgetProvider(
            info = info,
            user = user,
            label = label,
            appLabel = appLabel,
            appPackage = appPackage,
            minCells = cellsFor(minHeightPx, cell),
            resizable = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
        )
    }
}
