package app.cascata.launcher.ui.widgets

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import app.cascata.launcher.data.widgets.CELL_HEIGHT_DP
import app.cascata.launcher.data.widgets.MAX_CELLS
import app.cascata.launcher.data.widgets.MIN_CELLS
import app.cascata.launcher.data.widgets.PlacedWidget
import app.cascata.launcher.data.widgets.WidgetHostManager
import app.cascata.launcher.data.widgets.WidgetSlot
import app.cascata.launcher.data.widgets.cellsFor
import app.cascata.launcher.ui.theme.SurfaceTheme

private val SHEET_PADDING = 24.dp

/**
 * A edição de um slot. Tudo o que o toque longo faria em outro launcher está
 * aqui: altura, ordem, pilha e remoção — cada linha uma chamada das
 * [WidgetActions], que gravam e voltam pelo Flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetSlotSheet(
    slot: WidgetSlot,
    host: WidgetHostManager,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    actions: WidgetActions,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val unavailable = stringResource(R.string.widget_unavailable)
    val limits = remember(slot.widgets) { stackLimits(context, host, slot.widgets) }
    val labels = remember(slot.widgets, unavailable) {
        slot.widgets.map { widgetLabel(context, host, it.appWidgetId, unavailable) }
    }
    // "Remover" sozinho se repete uma vez por widget da pilha.
    val removeLabels = labels.map { stringResource(R.string.widget_remove_named, it) }

    SurfaceTheme {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Text(
                text = stringResource(R.string.widget_slot_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .semantics { heading() }
                    .padding(horizontal = SHEET_PADDING, vertical = 8.dp),
            )

            val cells = pluralStringResource(R.plurals.widget_cells, slot.heightCells, slot.heightCells)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    // "Altura" e "2 células" são uma parada só; os dois botões,
                    // que já são nós próprios, continuam separados.
                    .semantics(mergeDescendants = true) { }
                    .padding(horizontal = SHEET_PADDING, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.widget_height),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                // Provedor que não aceita redimensionar na vertical mostra a altura
                // que tem, sem botões que não fariam nada.
                if (limits.resizable) {
                    IconButton(
                        onClick = { actions.onResize(slot.id, slot.heightCells - 1) },
                        enabled = slot.heightCells > limits.minCells,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.widget_height_decrease),
                        )
                    }
                }
                Text(
                    text = cells,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (limits.resizable) {
                    IconButton(
                        onClick = { actions.onResize(slot.id, slot.heightCells + 1) },
                        enabled = slot.heightCells < MAX_CELLS,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.widget_height_increase),
                        )
                    }
                }
            }

            SheetRow(
                label = stringResource(R.string.widget_move_up),
                enabled = canMoveUp,
                onClick = { actions.onMove(slot.id, -1) },
            )
            SheetRow(
                label = stringResource(R.string.widget_move_down),
                enabled = canMoveDown,
                onClick = { actions.onMove(slot.id, 1) },
            )
            SheetRow(
                label = stringResource(R.string.widget_add_to_stack),
                // O seletor já abre sabendo em que slot o escolhido vai cair.
                onClick = {
                    actions.onAddToSlot(slot.id)
                    onDismiss()
                },
            )

            slot.widgets.forEachIndexed { index, _ ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = SHEET_PADDING, end = 8.dp, top = 2.dp, bottom = 2.dp),
                ) {
                    Text(
                        text = labels[index],
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { actions.onRemove(slot.widgets[index].appWidgetId) },
                        // Numa pilha, três botões "Remover" não dizem qual é qual.
                        modifier = Modifier.semantics {
                            contentDescription = removeLabels[index]
                        },
                    ) {
                        Text(stringResource(R.string.widget_remove))
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SheetRow(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = SHEET_PADDING, vertical = 12.dp),
    )
}

/** O que a pilha inteira permite: o maior mínimo manda, e um widget fixo trava todos. */
private data class StackLimits(val minCells: Int, val resizable: Boolean)

private fun stackLimits(
    context: Context,
    host: WidgetHostManager,
    widgets: List<PlacedWidget>,
): StackLimits {
    val cellPx = (CELL_HEIGHT_DP * context.resources.displayMetrics.density).toInt()
    var minCells = MIN_CELLS
    var resizable = widgets.isNotEmpty()
    widgets.forEach { widget ->
        val info = host.providerInfo(widget.appWidgetId) ?: return@forEach
        val minHeightPx = info.minResizeHeight.takeIf { it > 0 } ?: info.minHeight
        minCells = maxOf(minCells, cellsFor(minHeightPx, cellPx))
        if (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL == 0) resizable = false
    }
    return StackLimits(minCells, resizable)
}
