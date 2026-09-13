package app.cascata.launcher.ui.clock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.Date
import kotlin.math.cos
import kotlin.math.sin

/** Frações do raio: onde cada marca começa e termina, e até onde vai cada ponteiro. */
private const val TICK_INNER = 0.80f
private const val TICK_OUTER = 0.92f
private const val HOUR_HAND = 0.52f
private const val MINUTE_HAND = 0.78f

private val RIM_WIDTH = 2.dp
private val TICK_WIDTH = 2.dp
private val HOUR_WIDTH = 4.dp
private val MINUTE_WIDTH = 3.dp

/**
 * Mostrador desenhado à mão: aro, doze marcas e dois ponteiros. Sem ponteiro de
 * segundos de propósito — o cabeçalho só acorda de minuto em minuto, e um
 * ponteiro parado por 60 s seria pior que nenhum.
 */
@Composable
fun AnalogDial(now: Date, size: Dp, modifier: Modifier = Modifier) {
    // Só o minuto muda o desenho; refazer o Calendar a cada recomposição não muda.
    val minuteOfDay = remember(now) {
        val calendar = Calendar.getInstance().apply { time = now }
        calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }
    val face = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        drawCircle(
            color = face,
            radius = radius - RIM_WIDTH.toPx() / 2f,
            center = center,
            style = Stroke(width = RIM_WIDTH.toPx()),
        )

        // As doze marcas. A das horas cheias é a mesma para todas: um relógio de
        // 96.dp não tem espaço para diferenciar 12h de 3h sem virar borrão.
        repeat(12) { index ->
            val angle = index * 30f
            drawLine(
                color = face,
                start = center.polar(angle, radius * TICK_INNER),
                end = center.polar(angle, radius * TICK_OUTER),
                strokeWidth = TICK_WIDTH.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Ponteiro das horas anda com os minutos: às 14h30 ele fica no meio.
        hand(center, (minuteOfDay % 720) * 0.5f, radius * HOUR_HAND, face, HOUR_WIDTH.toPx())
        hand(center, (minuteOfDay % 60) * 6f, radius * MINUTE_HAND, accent, MINUTE_WIDTH.toPx())

        drawCircle(color = accent, radius = MINUTE_WIDTH.toPx(), center = center)
    }
}

private fun DrawScope.hand(
    center: Offset,
    degrees: Float,
    length: Float,
    color: Color,
    width: Float,
) {
    drawLine(
        color = color,
        start = center,
        end = center.polar(degrees, length),
        strokeWidth = width,
        cap = StrokeCap.Round,
    )
}

/** Ângulo contado do meio-dia, no sentido horário — o sistema do relógio, não o do seno. */
private fun Offset.polar(degrees: Float, length: Float): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    return Offset(
        x = x + (sin(radians) * length).toFloat(),
        y = y - (cos(radians) * length).toFloat(),
    )
}
