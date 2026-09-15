package app.cascata.launcher.ui.widgets

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.cascata.launcher.R
import app.cascata.launcher.data.widgets.CELL_HEIGHT_DP
import app.cascata.launcher.data.widgets.PlacedWidget
import app.cascata.launcher.data.widgets.WidgetHostManager
import app.cascata.launcher.data.widgets.WidgetLayout
import app.cascata.launcher.data.widgets.WidgetSlot
import app.cascata.launcher.data.widgets.visibleSlots
import kotlinx.coroutines.flow.drop

private val SLOT_CORNER = 16.dp
private val SLOT_GAP = 8.dp
private val EDIT_BUTTON = 28.dp
private val EDIT_ICON = 18.dp
private val DOT_SIZE = 6.dp
private val DOT_GAP = 3.dp

/** Quanto do papel de parede ainda aparece atrás do slot. */
private const val SLOT_ALPHA = 0.4f

/** O botão de editar precisa se separar do widget que está atrás dele. */
private const val EDIT_ALPHA = 0.8f

private const val DOT_INACTIVE_ALPHA = 0.4f

/**
 * O que a área de widgets sabe pedir. Quem grava é a Activity — aqui não há
 * acesso a prefs nem a corrotina: um toque vira uma chamada e acabou.
 */
data class WidgetActions(
    val onResize: (slotId: Int, cells: Int) -> Unit,
    val onMove: (slotId: Int, delta: Int) -> Unit,
    val onRemove: (appWidgetId: Int) -> Unit,
    val onSetActive: (slotId: Int, index: Int) -> Unit,
    val onAddToSlot: (slotId: Int) -> Unit,
)

/**
 * A faixa de widgets da home: um slot embaixo do outro, cada um com a largura
 * inteira e a altura que ele mesmo guarda em células.
 *
 * Não há toque longo no slot: o `pointerInput` teria de ficar **sobre** a
 * `AppWidgetHostView` e roubaria dela todo toque — inclusive os que o widget
 * existe para receber. Em vez disso cada slot ganha um botão discreto no canto,
 * que é o único pedaço da moldura que não é do outro app.
 */
@Composable
fun WidgetArea(
    layout: WidgetLayout,
    host: WidgetHostManager,
    actions: WidgetActions,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<Int?>(null) }

    // Aparelho sem host de widgets não desenha faixa nenhuma — nem a moldura.
    val slots = visibleSlots(layout, host.available)

    // Tirar o último widget apaga o slot: a folha aberta perde o assunto.
    LaunchedEffect(slots) {
        val open = editing
        if (open != null && slots.none { it.id == open }) editing = null
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(SLOT_GAP),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = SLOT_GAP),
    ) {
        slots.forEach { slot ->
            key(slot.id) {
                SlotBox(
                    slot = slot,
                    host = host,
                    onRemove = actions.onRemove,
                    onSetActive = actions.onSetActive,
                    onEdit = { editing = slot.id },
                )
            }
        }
    }

    slots.firstOrNull { it.id == editing }?.let { slot ->
        WidgetSlotSheet(
            slot = slot,
            host = host,
            canMoveUp = slots.first().id != slot.id,
            canMoveDown = slots.last().id != slot.id,
            actions = actions,
            onDismiss = { editing = null },
        )
    }
}

/**
 * Rótulo do provedor, sem passar por composable: quem monta lista (a folha, as
 * configurações) precisa dele dentro de um `remember`, não a cada recomposição.
 * App desinstalado não tem rótulo — é o que [fallback] diz.
 */
internal fun widgetLabel(
    context: Context,
    host: WidgetHostManager,
    appWidgetId: Int,
    fallback: String,
): String = host.providerInfo(appWidgetId)
    ?.let { runCatching { it.loadLabel(context.packageManager) }.getOrNull() }
    ?.takeIf { it.isNotBlank() }
    ?: fallback

@Composable
private fun SlotBox(
    slot: WidgetSlot,
    host: WidgetHostManager,
    onRemove: (Int) -> Unit,
    onSetActive: (Int, Int) -> Unit,
    onEdit: () -> Unit,
) {
    val heightDp = slot.heightCells * CELL_HEIGHT_DP
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            // O fundo é só uma moldura: o widget desenha por cima dele inteiro.
            .clip(RoundedCornerShape(SLOT_CORNER))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SLOT_ALPHA)),
    ) {
        val widthDp = maxWidth.value.toInt()

        if (slot.widgets.size > 1) {
            WidgetStack(
                slot = slot,
                host = host,
                widthDp = widthDp,
                heightDp = heightDp,
                onRemove = onRemove,
                onSetActive = onSetActive,
            )
        } else {
            slot.widgets.firstOrNull()?.let { widget ->
                WidgetFrame(widget = widget, host = host, widthDp = widthDp, heightDp = heightDp, onRemove = onRemove)
            }
        }

        IconButton(
            onClick = onEdit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(EDIT_BUTTON)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = EDIT_ALPHA)),
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.widget_edit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(EDIT_ICON),
            )
        }
    }
}

/** Vários widgets no mesmo espaço, a um swipe horizontal um do outro. */
@Composable
private fun WidgetStack(
    slot: WidgetSlot,
    host: WidgetHostManager,
    widthDp: Int,
    heightDp: Int,
    onRemove: (Int) -> Unit,
    onSetActive: (Int, Int) -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = slot.activeIndex.coerceIn(0, slot.widgets.lastIndex),
    ) { slot.widgets.size }

    // Só o que o dedo mudou volta para as prefs: a primeira emissão é o valor
    // que já está gravado, e regravá-lo seria uma escrita por composição.
    LaunchedEffect(pagerState, slot.id) {
        snapshotFlow { pagerState.currentPage }
            .drop(1)
            .collect { page -> onSetActive(slot.id, page) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            WidgetFrame(
                widget = slot.widgets[page],
                host = host,
                widthDp = widthDp,
                heightDp = heightDp,
                onRemove = onRemove,
            )
        }

        val position = stringResource(
            R.string.widget_stack_position,
            pagerState.currentPage + 1,
            slot.widgets.size,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(DOT_GAP),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
                // Os pontos são a única pista de que há mais de um widget aqui.
                .semantics { contentDescription = position },
        ) {
            repeat(slot.widgets.size) { index ->
                val active = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .size(DOT_SIZE)
                        .clip(CircleShape)
                        .background(
                            if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DOT_INACTIVE_ALPHA)
                            },
                        ),
                )
            }
        }
    }
}

/**
 * Um widget. A `AppWidgetHostView` é criada uma vez por id e guardada num
 * `remember`: ela é o widget em si, com o processo do outro app do outro lado —
 * recriá-la a cada recomposição piscaria a tela e perderia o estado dele. A
 * `factory` do [AndroidView] roda uma vez; a cada medida nova só [updateSize]
 * avisa o provedor do tamanho que ele ganhou.
 */
@Composable
private fun WidgetFrame(
    widget: PlacedWidget,
    host: WidgetHostManager,
    widthDp: Int,
    heightDp: Int,
    onRemove: (Int) -> Unit,
) {
    val context = LocalContext.current
    val info = remember(widget.appWidgetId) { host.providerInfo(widget.appWidgetId) }

    if (info == null) {
        UnavailableWidget(onRemove = { onRemove(widget.appWidgetId) })
        return
    }

    // Null quando nem a view sai: o processo do provedor pode ter caído ao
    // inflar o RemoteViews dele, e isso não vale a home inteira.
    val view = remember(widget.appWidgetId) { host.createView(context, widget.appWidgetId, info) }
    if (view == null) {
        UnavailableWidget(onRemove = { onRemove(widget.appWidgetId) })
        return
    }

    AndroidView(
        factory = { view },
        update = { host.updateSize(it, widthDp, heightDp) },
        modifier = Modifier.fillMaxSize(),
    )
}

/** O app do widget saiu do aparelho. A moldura fica até alguém dizer o que fazer. */
@Composable
private fun UnavailableWidget(onRemove: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(
            text = stringResource(R.string.widget_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRemove) { Text(stringResource(R.string.widget_remove)) }
    }
}
