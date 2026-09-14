package app.cascata.launcher.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.cascata.launcher.R
import app.cascata.launcher.data.AppEntry
import app.cascata.launcher.data.AppRepository
import kotlin.math.roundToInt

private val FAVORITE_ICON = 48.dp
private val FAVORITE_SLOT = 64.dp
private val FAVORITE_GAP = 8.dp

/** O que o arraste precisa saber: quem está na mão, onde ele está agora e o quanto saiu do lugar. */
private class FavoritesDrag {
    var key by mutableStateOf<String?>(null)
    var index by mutableIntStateOf(-1)
    var offset by mutableFloatStateOf(0f)

    fun stop() {
        key = null
        index = -1
        offset = 0f
    }
}

/**
 * Favoritos numa linha rolável. Toque abre; toque longo entra em modo de
 * arraste e a posição X do dedo escolhe o novo lugar.
 *
 * O alvo sai de uma conta simples — quantos slots o dedo andou — porque os
 * itens têm largura fixa. Assim não é preciso medir item por item, e a ordem
 * real fica sempre com o ViewModel: aqui só se acompanha o índice atual.
 */
@Composable
fun FavoritesRow(
    favorites: List<AppEntry>,
    repository: AppRepository,
    /** Abrir passa pelo ViewModel: é lá que mora o gate da pausa por limite. */
    onLaunch: (AppEntry) -> Unit,
    onMoveFavorite: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val slotPx = with(LocalDensity.current) { (FAVORITE_SLOT + FAVORITE_GAP).toPx() }
    val drag = remember { FavoritesDrag() }
    // Lida dentro dos gestos, que sobrevivem à recomposição e veriam uma lista velha.
    val current by rememberUpdatedState(favorites)
    val move by rememberUpdatedState(onMoveFavorite)

    Column(modifier = modifier.padding(bottom = 8.dp)) {
        Text(
            text = stringResource(R.string.favorites),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(FAVORITE_GAP),
            contentPadding = PaddingValues(vertical = 6.dp),
            // Durante o arraste a linha não rola: o dedo já está ocupado.
            userScrollEnabled = drag.key == null,
        ) {
            items(count = favorites.size, key = { favorites[it].key }) { index ->
                val entry = favorites[index]
                val dragging = drag.key == entry.key

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(FAVORITE_SLOT)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            if (dragging) {
                                translationX = drag.offset
                                scaleX = 1.1f
                                scaleY = 1.1f
                            }
                        }
                        .combinedClickable(
                            onClick = { onLaunch(entry) },
                            // O arraste é de outro detector; aqui só evitamos que
                            // o toque longo termine virando um toque simples.
                            onLongClick = {},
                        )
                        .pointerInput(entry.key) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    drag.key = entry.key
                                    drag.index = current.indexOfFirst { it.key == entry.key }
                                    drag.offset = 0f
                                },
                                onDragEnd = { drag.stop() },
                                onDragCancel = { drag.stop() },
                            ) { change, delta ->
                                change.consume()
                                if (drag.index < 0) return@detectDragGesturesAfterLongPress
                                drag.offset += delta.x
                                val steps = (drag.offset / slotPx).roundToInt()
                                val target = (drag.index + steps).coerceIn(0, current.lastIndex)
                                if (target != drag.index) {
                                    move(drag.index, target)
                                    drag.offset -= (target - drag.index) * slotPx
                                    drag.index = target
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } else {
                                    // Nas pontas o dedo continua andando e o item não: segura o offset.
                                    drag.offset = drag.offset.coerceIn(-slotPx, slotPx)
                                }
                            }
                        },
                ) {
                    AppIcon(entry = entry, repository = repository, size = FAVORITE_ICON)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
