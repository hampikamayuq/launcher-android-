package app.cascata.launcher.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/** Quanto o dedo precisa subir para a busca abrir. Abaixo disso é rolagem comum. */
private val SWIPE_THRESHOLD = 64.dp

/**
 * Swipe-up sobre a lista, sem tirar nada do scroll normal.
 *
 * A regra é olhar só o arraste que a lista **não** consumiu (`onPostScroll`):
 * enquanto há conteúdo para rolar ela consome tudo e nada acontece; quando ela
 * chega ao fim — ou quando é curta demais para rolar, como numa busca com dois
 * resultados — a sobra chega aqui e vira o gesto. Interceptar em `onPreScroll`
 * faria o contrário: roubaria os primeiros 64.dp de toda rolagem.
 */
@Composable
fun rememberSwipeUpToSearch(onOpenSearch: () -> Unit): NestedScrollConnection {
    val threshold = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    val open by rememberUpdatedState(onOpenSearch)

    return remember(threshold) {
        object : NestedScrollConnection {
            /** Quanto já subiu sem a lista aproveitar. Zera assim que ela volta a rolar. */
            private var pulled = 0f

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (consumed.y != 0f || available.y > 0f) {
                    pulled = 0f
                } else if (available.y < 0f) {
                    pulled -= available.y
                    if (pulled >= threshold) {
                        pulled = 0f
                        open()
                    }
                }
                return Offset.Zero
            }

            /** Fim do gesto: o resto de velocidade não deve somar no próximo arraste. */
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }
}

/**
 * O mesmo gesto onde não há nada rolando — o cabeçalho. É o caminho curto para
 * quem está no topo da lista, onde a sobra de rolagem nunca aparece.
 */
fun Modifier.swipeUpToSearch(onOpenSearch: () -> Unit): Modifier = composed {
    val threshold = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    val open by rememberUpdatedState(onOpenSearch)

    pointerInput(threshold) {
        var pulled = 0f
        var fired = false
        detectVerticalDragGestures(
            onDragStart = { pulled = 0f; fired = false },
            onDragEnd = { pulled = 0f; fired = false },
            onDragCancel = { pulled = 0f; fired = false },
        ) { _, delta ->
            pulled -= delta
            if (!fired && pulled >= threshold) {
                fired = true
                open()
            }
        }
    }
}
