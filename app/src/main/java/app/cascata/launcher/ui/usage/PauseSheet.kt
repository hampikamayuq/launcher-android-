package app.cascata.launcher.ui.usage

import android.content.Context
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cascata.launcher.PausePrompt
import app.cascata.launcher.R
import app.cascata.launcher.data.IconSource
import app.cascata.launcher.ui.AppIcon
import kotlinx.coroutines.delay

private val PAUSE_ICON = 48.dp
private val BREATH_SIZE = 96.dp

/** Meia respiração: cresce nesse tempo e encolhe no mesmo — suave, sem hipnotizar. */
private const val BREATH_MILLIS = 2_000

private const val BREATH_MIN_SCALE = 0.7f

/**
 * A pausa deliberada: o app que o usuário pediu espera uma respiração antes de
 * abrir.
 *
 * Enquanto a contagem corre, a folha não se dispensa sozinha — nem arrastando,
 * nem tocando fora. Sair é uma decisão, e ela tem botão ("Voltar", sempre
 * habilitado); o botão Voltar do aparelho faz o mesmo. O "Abrir mesmo assim" só
 * acende quando a contagem chega a zero: a espera é o recurso inteiro.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PauseSheet(
    prompt: PausePrompt,
    repository: IconSource,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val total = prompt.pauseSeconds.coerceAtLeast(1)
    var remaining by remember(prompt.entry.key) { mutableIntStateOf(total) }

    LaunchedEffect(prompt.entry.key) {
        while (remaining > 0) {
            delay(1_000L)
            remaining -= 1
        }
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // Arrastar para baixo durante a contagem não fecha; depois dela, fecha.
        confirmValueChange = { value -> remaining <= 0 || value != SheetValue.Hidden },
    )

    ModalBottomSheet(
        // Tocar fora só vale depois da contagem — antes disso, nada acontece.
        onDismissRequest = { if (remaining <= 0) onDismiss() },
        sheetState = sheetState,
    ) {
        BackHandler { onDismiss() }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // Ícone e nome são o título da folha: uma parada só, e um cabeçalho.
                modifier = Modifier.semantics(mergeDescendants = true) { heading() },
            ) {
                AppIcon(entry = prompt.entry, repository = repository, size = PAUSE_ICON)
                Spacer(Modifier.width(14.dp))
                Text(
                    text = prompt.entry.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(12.dp))

            val usedMinutes = (prompt.usedTodayMillis / 60_000L).toInt()
            Text(
                text = pluralStringResource(R.plurals.usage_pause_used, usedMinutes, usedMinutes) +
                    " " +
                    pluralStringResource(
                        R.plurals.usage_pause_limit,
                        prompt.limitMinutes,
                        prompt.limitMinutes,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
            BreathingCircle()
            Spacer(Modifier.height(24.dp))

            Text(
                text = pluralStringResource(R.plurals.usage_pause_countdown, remaining, remaining),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                // A contagem muda sozinha: o leitor de tela precisa ser avisado.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { (total - remaining).toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_back))
                }
                Button(onClick = onConfirm, enabled = remaining <= 0) {
                    Text(stringResource(R.string.usage_pause_open))
                }
            }
        }
    }
}

/**
 * O círculo que cresce e encolhe no ritmo de uma respiração.
 *
 * O `LocalAccessibilityManager` do Compose não diz nada sobre movimento — só
 * sobre tempo de leitura. Quem responde por "reduzir animações" no Android é a
 * escala de duração do sistema: em zero, o círculo fica parado em vez de pular
 * entre os dois tamanhos.
 */
@Composable
private fun BreathingCircle() {
    val context = LocalContext.current
    val animate = remember(context) { animationsEnabled(context) }
    val transition = rememberInfiniteTransition(label = "respiracao")
    val scale by transition.animateFloat(
        initialValue = BREATH_MIN_SCALE,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(BREATH_MILLIS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "escala",
    )

    Box(
        modifier = Modifier
            .size(BREATH_SIZE)
            .graphicsLayer {
                val value = if (animate) scale else 1f
                scaleX = value
                scaleY = value
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
    )
}

/** Aparelho com animações desligadas devolve zero aqui. */
private fun animationsEnabled(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) > 0f
}.getOrDefault(true)
