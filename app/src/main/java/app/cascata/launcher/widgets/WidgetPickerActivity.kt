package app.cascata.launcher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.cascata.launcher.CascataApp
import app.cascata.launcher.R
import app.cascata.launcher.data.theme.ThemeSettings
import app.cascata.launcher.data.widgets.PlacedWidget
import app.cascata.launcher.data.widgets.WidgetProvider
import app.cascata.launcher.data.widgets.addWidget
import app.cascata.launcher.ui.theme.CascataTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

private val PREVIEW_HEIGHT = 160.dp

/**
 * O seletor de widgets: tudo o que está instalado, agrupado por app.
 *
 * Os dois passos que dependem do sistema — autorizar o bind e rodar a tela de
 * configuração do provedor — voltam pelo `onActivityResult` clássico, e não pelo
 * `ActivityResultContracts`. Não é nostalgia: quem lança a configuração é o
 * `AppWidgetHost` (`startAppWidgetConfigureActivityForResult`), porque é ele que
 * tem permissão para abrir a Activity de outro app com o id alocado, e essa API
 * só sabe devolver por código de requisição. Com um passo preso ao mecanismo
 * antigo, o outro vai junto — dois caminhos de resultado para o mesmo fluxo
 * seria mais confuso que o `@Suppress` aqui embaixo.
 */
class WidgetPickerActivity : ComponentActivity() {

    companion object {
        /** Em que slot o widget escolhido cai. Ausente = slot novo no fim. */
        const val EXTRA_SLOT_ID = "slot_id"

        private const val REQ_BIND = 601
        private const val REQ_CONFIGURE = 602
        private const val STATE_PENDING_ID = "pending_id"
    }

    private val app: CascataApp get() = application as CascataApp

    /** O id alocado que ainda não virou widget na tela. */
    private var pendingId = AppWidgetManager.INVALID_APPWIDGET_ID

    /** O provedor que o usuário tocou; perdido numa recriação, o fluxo aborta. */
    private var pendingProvider: WidgetProvider? = null

    private val slotId: Int?
        get() = intent.getIntExtra(EXTRA_SLOT_ID, -1).takeIf { it >= 0 }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        pendingId = savedInstanceState?.getInt(STATE_PENDING_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setContent {
            val settings by app.themePrefs.settings
                .collectAsStateWithLifecycle(initialValue = ThemeSettings.DEFAULT)
            val customFont = remember { app.fontStore.customFile() }
            val wallpaperSeed by produceState<Int?>(null) {
                runCatching {
                    app.wallpaperColors.changes()
                        .onStart { emit(Unit) }
                        .collect {
                            value = withContext(Dispatchers.IO) { app.wallpaperColors.primaryArgb() }
                        }
                }
            }

            CascataTheme(
                settings = settings,
                customFont = customFont,
                wallpaperSeed = wallpaperSeed,
                // Esta tela tem fundo próprio e opaco: a opacidade da home não
                // diz nada sobre ela, e a tinta de papel de parede sumiria aqui.
                overWallpaper = false,
            ) {
                PickerScreen(
                    load = {
                        withContext(Dispatchers.IO) {
                            runCatching { app.widgetHost.installedProviders() }.getOrDefault(emptyList())
                        }
                    },
                    loadPreview = { provider -> app.widgetHost.loadPreview(provider) },
                    onPick = ::add,
                    onBack = { finish() },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Sem isto, uma recriação no meio do diálogo deixaria o id alocado para sempre.
        outState.putInt(STATE_PENDING_ID, pendingId)
    }

    /**
     * O fluxo inteiro de adicionar, na ordem que o sistema exige: alocar, ligar
     * (sozinho ou pelo diálogo), conferir o provedor, configurar e só então
     * gravar. Qualquer passo cancelado devolve o id.
     */
    @Suppress("DEPRECATION")
    private fun add(provider: WidgetProvider) {
        // Um de cada vez: o segundo toque enquanto o diálogo do sistema está no
        // ar deixaria o primeiro id alocado sem dono.
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) return
        // Sem host o id vem inválido: não há o que ligar nem onde desenhar, e o
        // toque simplesmente não faz nada em vez de derrubar a tela.
        val id = app.widgetHost.allocateId()
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        pendingId = id
        pendingProvider = provider

        val component = provider.info.provider
        if (app.widgetHost.bindIfAllowed(id, component, provider.user)) {
            afterBind()
            return
        }

        val intent = app.widgetHost.bindIntent(id, component, provider.user)
        val started = runCatching { startActivityForResult(intent, REQ_BIND) }.isSuccess
        if (!started) cancelPending()
    }

    private fun afterBind() {
        val id = pendingId
        val info = app.widgetHost.providerInfo(id)
        if (info == null || pendingProvider == null) {
            cancelPending()
            return
        }
        if (app.widgetHost.needsConfiguration(info)) {
            app.widgetHost.startConfigure(this, id, REQ_CONFIGURE)
            return
        }
        place(info)
    }

    private fun place(info: AppWidgetProviderInfo) {
        val id = pendingId
        val provider = pendingProvider ?: return cancelPending()
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProvider = null
        lifecycleScope.launch {
            app.widgetPrefs.update { layout ->
                layout.addWidget(
                    slotId = slotId,
                    widget = PlacedWidget(
                        appWidgetId = id,
                        provider = info.provider.flattenToString(),
                        userHash = provider.user.hashCode(),
                    ),
                    heightCells = provider.minCells,
                )
            }
            finish()
        }
    }

    /**
     * Saindo com um id ainda pendente — desistiu na frente do diálogo, ou a tela
     * de configuração do provedor nem abriu — ele volta para o host. Numa
     * recriação por mudança de configuração não estamos terminando, e o id
     * sobrevive no `savedInstanceState`.
     */
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) cancelPending()
    }

    /** Cancelou, falhou ou perdeu o estado: o id volta para o host e nada é gravado. */
    private fun cancelPending() {
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) app.widgetHost.deleteId(pendingId)
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProvider = null
    }

    // Depreciado em favor do ActivityResult, mas é o que a API do host fala.
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_BIND -> if (resultCode == RESULT_OK) afterBind() else cancelPending()
            REQ_CONFIGURE -> {
                val info = app.widgetHost.providerInfo(pendingId)
                if (resultCode == RESULT_OK && info != null) place(info) else cancelPending()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerScreen(
    load: suspend () -> List<WidgetProvider>,
    loadPreview: suspend (WidgetProvider) -> Drawable?,
    onPick: (WidgetProvider) -> Unit,
    onBack: () -> Unit,
) {
    // Null é "ainda carregando"; lista vazia é "não há widget nenhum instalado".
    var providers by remember { mutableStateOf<List<WidgetProvider>?>(null) }
    LaunchedEffect(Unit) { providers = load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { insets ->
        val list = providers
        when {
            list == null -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(insets).fillMaxSize(),
            ) {
                CircularProgressIndicator()
            }

            list.isEmpty() -> Text(
                text = stringResource(R.string.widget_picker_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(insets).padding(24.dp),
            )

            else -> {
                // A lista já vem ordenada por app e depois por widget: agrupar
                // preserva essa ordem e só marca onde cada app começa.
                val groups = remember(list) { list.groupBy { it.appLabel } }
                LazyColumn(
                    modifier = Modifier.padding(insets).fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    groups.forEach { (appLabel, items) ->
                        item(key = "app-$appLabel", contentType = "header") {
                            Text(
                                text = appLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            )
                        }
                        items(
                            count = items.size,
                            key = { index -> providerKey(items[index]) },
                            contentType = { "provider" },
                        ) { index ->
                            ProviderRow(
                                provider = items[index],
                                loadPreview = loadPreview,
                                onClick = { onPick(items[index]) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(
    provider: WidgetProvider,
    loadPreview: suspend (WidgetProvider) -> Drawable?,
    onClick: () -> Unit,
) {
    val maxHeightPx = with(LocalDensity.current) { PREVIEW_HEIGHT.roundToPx() }
    // A prévia do provedor ou, quando ele não desenhou uma, o ícone dele — a
    // escolha é do host. Aqui só o que cabe na tela é rasterizado.
    val preview by produceState<ImageBitmap?>(null, provider, maxHeightPx) {
        val drawable = loadPreview(provider)
        value = withContext(Dispatchers.Default) {
            drawable?.let { runCatching { it.scaled(maxHeightPx).asImageBitmap() }.getOrNull() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().height(PREVIEW_HEIGHT),
        ) {
            preview?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    // O rótulo logo abaixo já diz o que é; a imagem é decoração.
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = provider.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = pluralStringResource(R.plurals.widget_cells, provider.minCells, provider.minCells),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** O mesmo provedor pode aparecer duas vezes: uma por perfil. */
private fun providerKey(provider: WidgetProvider): String =
    "${provider.info.provider.flattenToString()}@${provider.user.hashCode()}"

/** Rasteriza no tamanho da tela, nunca no da prévia — elas vêm em qualquer escala. */
private fun Drawable.scaled(maxHeightPx: Int): Bitmap {
    val width = intrinsicWidth
    val height = intrinsicHeight
    if (width <= 0 || height <= 0) return toBitmap(maxHeightPx, maxHeightPx)
    val scale = min(1f, maxHeightPx.toFloat() / height)
    return toBitmap(
        width = (width * scale).roundToInt().coerceAtLeast(1),
        height = (height * scale).roundToInt().coerceAtLeast(1),
    )
}
