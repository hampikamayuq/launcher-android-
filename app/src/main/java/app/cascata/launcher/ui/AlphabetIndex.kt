package app.cascata.launcher.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Índice alfabético lateral: arrastar o dedo percorre as letras e leva a lista
 * junto. É o atalho que substitui rolar centenas de apps — cada letra presente
 * vira um alvo, e a letra sob o dedo é destacada.
 */
@Composable
fun AlphabetIndex(
    letters: List<Char>,
    onLetterFocused: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return

    val haptics = LocalHapticFeedback.current
    var height by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf<Char?>(null) }

    fun letterAt(y: Float): Char? {
        if (height <= 0) return null
        val slot = height.toFloat() / letters.size
        val index = (y / slot).toInt().coerceIn(0, letters.size - 1)
        return letters[index]
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(28.dp)
            .padding(vertical = 8.dp)
            .onSizeChanged { height = it.height }
            .semantics { contentDescription = "Índice alfabético" }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        letterAt(offset.y)?.let {
                            focused = it
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLetterFocused(it)
                        }
                    },
                    onDragEnd = { focused = null },
                    onDragCancel = { focused = null },
                ) { change, _ ->
                    val letter = letterAt(change.position.y)
                    if (letter != null && letter != focused) {
                        focused = letter
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onLetterFocused(letter)
                    }
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            val active = letter == focused
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
