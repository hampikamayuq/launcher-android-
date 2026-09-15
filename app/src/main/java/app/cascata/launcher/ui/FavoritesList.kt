package app.cascata.launcher.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.cascata.launcher.R
import app.cascata.launcher.data.AppEntry
import app.cascata.launcher.data.IconSource
import app.cascata.launcher.ui.theme.LocalLauncherDensity
import app.cascata.launcher.ui.theme.iconSize
import app.cascata.launcher.ui.theme.rowPadding
import kotlin.math.abs
import kotlin.math.roundToInt

/** O mesmo espaço que a `LazyColumn` da home deixa entre duas linhas. */
private val FAVORITE_GAP = 2.dp

/** Estrela do cabeçalho: a mesma marca que o índice usa para "tela inicial". */
private val HEADER_STAR = 14.dp

/** O que o arraste precisa saber: quem está na mão, onde ele está e quanto andou. */
private class FavoritesListDrag {
    var key by mutableStateOf<String?>(null)
    var index by mutableIntStateOf(-1)
    var offset by mutableFloatStateOf(0f)

    /** Caminho total do dedo. Abaixo do slop isto foi um toque longo parado. */
    var travel = 0f

    fun stop() {
        key = null
        index = -1
        offset = 0f
        travel = 0f
    }
}

/**
 * Favoritos empilhados, com a mesma linha da gaveta — ícone, rótulo, densidade.
 * É a forma que o launcher usa por padrão desde a Fase 10: sobre o papel de
 * parede, uma coluna de nomes lê melhor que uma fileira de ícones miúdos.
 *
 * O cabeçalho é só a estrela, a mesma do índice lateral: a palavra "Favoritos"
 * em corpo grande competiria com os nomes dos apps logo abaixo. Para o leitor
 * de tela ela continua sendo o título da seção.
 *
 * **Reordenar e menu de contexto no mesmo toque longo.** `detectDragGesturesAfterLongPress`
 * e o `onLongClick` do `combinedClickable` não convivem — o segundo abriria a
 * folha de contexto no instante em que o arraste começaria. Então há um detector
 * só: o toque longo sempre entra em modo de arraste, e é o fim do gesto que
 * decide. Andou mais que o slop, foi reordenação (a posição já mudou durante o
 * arraste); não andou, foi um toque longo parado e a folha abre. O leitor de
 * tela não arrasta: para ele fica a ação de toque longo declarada no `semantics`
 * antes do `combinedClickable` — ali ela vence a ação vazia que ele registra.
 */
@Composable
fun FavoritesList(
    favorites: List<AppEntry>,
    repository: IconSource,
    /** Abrir passa pelo ViewModel: é lá que mora o gate da pausa por limite. */
    onLaunch: (AppEntry) -> Unit,
    onMoveFavorite: (Int, Int) -> Unit,
    /** Toque longo parado: o mesmo menu de contexto das linhas da gaveta. */
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (favorites.isEmpty()) return

    val haptics = LocalHapticFeedback.current
    val density = LocalLauncherDensity.current
    val drag = remember { FavoritesListDrag() }
    // Lidos dentro dos gestos, que sobrevivem à recomposição e veriam listas velhas.
    val current by rememberUpdatedState(favorites)
    val move by rememberUpdatedState(onMoveFavorite)
    val longPress by rememberUpdatedState(onLongPress)

    // Todas as linhas têm a mesma altura; medir uma basta para saber o passo do
    // arraste, e medir é mais seguro que somar ícone, espaçamento e fonte.
    var rowHeight by remember { mutableIntStateOf(0) }
    val gapPx = with(LocalDensity.current) { FAVORITE_GAP.toPx() }
    val favoritesLabel = stringResource(R.string.favorites)
    val optionsLabel = stringResource(R.string.app_options)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FAVORITE_GAP),
    ) {
        Icon(
            imageVector = Icons.Outlined.Star,
            contentDescription = favoritesLabel,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .semantics { heading() }
                .padding(top = 4.dp, bottom = 4.dp)
                .size(HEADER_STAR),
        )

        favorites.forEach { entry ->
            // A identidade da linha é o app, não a posição: reordenar move o nó
            // em vez de recriá-lo. Sem isto, a troca de posição reinicia o
            // `pointerInput` desta linha e o arraste em curso é cancelado no
            // primeiro passo — o dedo continua andando e nada mais se move.
            key(entry.key) {
                val dragging = drag.key == entry.key

                /** Fim do gesto: ou a ordem já mudou, ou isto foi um toque longo parado. */
                fun finish(slop: Float) {
                    if (drag.key == entry.key && drag.travel <= slop) longPress(entry)
                    drag.stop()
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { if (it.height > 0) rowHeight = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            if (dragging) {
                                translationY = drag.offset
                                scaleX = 1.03f
                                scaleY = 1.03f
                            }
                        }
                        // Antes do `combinedClickable`: na fusão de semânticas quem
                        // vem primeiro fica com a ação, e a dele é vazia de propósito.
                        .semantics {
                            onLongClick(optionsLabel) {
                                onLongPress(entry)
                                true
                            }
                        }
                        .combinedClickable(
                            role = Role.Button,
                            onClick = { onLaunch(entry) },
                            // O toque longo é do detector abaixo; aqui só evitamos
                            // que ele termine virando um toque simples (e abrindo o app).
                            onLongClick = {},
                        )
                        .pointerInput(entry.key) {
                            val slop = viewConfiguration.touchSlop
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    drag.key = entry.key
                                    drag.index = current.indexOfFirst { it.key == entry.key }
                                    drag.offset = 0f
                                    drag.travel = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = { finish(slop) },
                                onDragCancel = { finish(slop) },
                            ) { change, delta ->
                                change.consume()
                                if (drag.index < 0) return@detectDragGesturesAfterLongPress
                                drag.travel += abs(delta.y)
                                drag.offset += delta.y
                                // `rowHeight` só existe depois da primeira medida; o
                                // espaçamento sozinho daria um passo de dois dp e
                                // qualquer tremida jogaria o favorito para a ponta.
                                if (rowHeight <= 0) return@detectDragGesturesAfterLongPress
                                val slot = rowHeight + gapPx
                                val to = favoriteDropIndex(
                                    offsetPx = drag.offset,
                                    slotPx = slot,
                                    fromIndex = drag.index,
                                    lastIndex = current.lastIndex,
                                )
                                if (to != drag.index) {
                                    move(drag.index, to)
                                    drag.offset -= (to - drag.index) * slot
                                    drag.index = to
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } else {
                                    // Nas pontas o dedo continua andando e a linha não.
                                    drag.offset = drag.offset.coerceIn(-slot, slot)
                                }
                            }
                        }
                        .padding(vertical = density.rowPadding),
                ) {
                    AppIcon(entry = entry, repository = repository, size = density.iconSize)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}

/**
 * Para onde vai o favorito que está sendo arrastado: quantos passos inteiros de
 * linha o dedo já andou ([offsetPx] sobre [slotPx]), somados ao índice de onde
 * ele saiu e presos dentro da lista.
 *
 * Altura de linha ainda não medida — [slotPx] zero ou negativo — devolve o
 * índice de origem: sem o passo, um arraste de um pixel mandaria o favorito
 * para a ponta.
 */
internal fun favoriteDropIndex(
    offsetPx: Float,
    slotPx: Float,
    fromIndex: Int,
    lastIndex: Int,
): Int {
    if (slotPx <= 0f || fromIndex < 0 || lastIndex < 0) return fromIndex
    val steps = (offsetPx / slotPx).roundToInt()
    return (fromIndex + steps).coerceIn(0, lastIndex)
}
