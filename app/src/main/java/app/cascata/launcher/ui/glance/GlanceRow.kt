package app.cascata.launcher.ui.glance

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cascata.launcher.R
import app.cascata.launcher.data.glance.AlarmSource
import app.cascata.launcher.data.glance.BatterySource
import app.cascata.launcher.data.glance.CalendarEvent
import app.cascata.launcher.data.glance.CalendarSource
import app.cascata.launcher.data.glance.GlanceSettings
import app.cascata.launcher.data.glance.MediaSource
import app.cascata.launcher.data.glance.weather.WeatherSource
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/** Quanto do `surfaceVariant` fica: o chip precisa se separar do fundo sem virar botão. */
internal const val CHIP_ALPHA = 0.7f

/** Lado do ícone dentro do chip. */
internal val CHIP_ICON = 18.dp

/** Um título de evento não pode empurrar o resto da linha para fora da tela. */
private val CHIP_TEXT_MAX = 140.dp

/**
 * A linha de cards do topo. Cada card só é composto quando está ligado — e como
 * cada fonte só trabalha quando alguém assina o Flow dela, card desligado é
 * broadcast não registrado e consulta não feita.
 *
 * Sem nenhum card ligado a linha não ocupa altura nenhuma.
 */
@Composable
fun GlanceRow(
    settings: GlanceSettings,
    alarmSource: AlarmSource,
    batterySource: BatterySource,
    calendarSource: CalendarSource,
    weatherSource: WeatherSource,
    mediaSource: MediaSource,
    /** Card de mídia ligado *e* serviço de notificações conectado — sem o acesso não há sessão. */
    showMedia: Boolean,
    modifier: Modifier = Modifier,
) {
    // O clima depende da edição: no `lite` a fonte existe, mas não sabe buscar.
    val showWeather = settings.showWeather && weatherSource.available
    if (!settings.showAlarm && !settings.showBattery && !settings.showCalendar &&
        !showWeather && !showMedia
    ) {
        return
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (settings.showAlarm) AlarmChip(alarmSource)
        if (settings.showBattery) BatteryChip(batterySource)
        if (settings.showCalendar) CalendarChip(calendarSource)
        if (showWeather) WeatherChip(weatherSource, settings.temperatureUnit)
        if (showMedia) MediaChip(mediaSource)
    }
}

/**
 * O chip: Surface arredondada, baixa, tocável. A descrição completa fica no nó
 * inteiro — quem usa leitor de tela ouve "Bateria 82 por cento, carregando", e
 * não "ícone, 82%".
 */
@Composable
internal fun GlanceChip(
    description: String,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CHIP_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(percent = 50),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .semantics(mergeDescendants = true) { contentDescription = description },
            content = content,
        )
    }
}

/** Texto padrão do chip. O ícone ao lado já diz do que se trata. */
@Composable
internal fun ChipText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Ícone do chip: sem descrição própria, porque o chip inteiro já tem uma. */
@Composable
internal fun ChipIcon(image: ImageVector) {
    Icon(
        imageVector = image,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(CHIP_ICON),
    )
}

@Composable
internal fun ChipIcon(painter: Painter) {
    Icon(
        painter = painter,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(CHIP_ICON),
    )
}

/** Sem alarme registrado não há chip: um "—" ocuparia espaço para não dizer nada. */
@Composable
private fun AlarmChip(source: AlarmSource) {
    val context = LocalContext.current
    val alarm by remember(source) { source.next() }.collectAsStateWithLifecycle(null)
    val next = alarm ?: return

    val locale = LocalLocale.current.platformLocale
    val timeFormat = rememberTimeFormat(locale)
    val dayFormat = remember(locale) { SimpleDateFormat("EEE", locale) }
    val time = timeFormat.format(Date(next.triggerAtMillis))

    val label = when (daysFromToday(next.triggerAtMillis)) {
        0L -> time
        1L -> stringResource(R.string.glance_alarm_tomorrow, time)
        else -> stringResource(
            R.string.glance_alarm_day,
            dayFormat.format(Date(next.triggerAtMillis)).replaceFirstChar { it.uppercase(locale) },
            time,
        )
    }

    GlanceChip(
        description = stringResource(R.string.glance_alarm_description, label),
        onClick = {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        },
    ) {
        ChipIcon(Icons.Outlined.Notifications)
        ChipText(label)
    }
}

@Composable
private fun BatteryChip(source: BatterySource) {
    val context = LocalContext.current
    val state by remember(source) { source.state() }.collectAsStateWithLifecycle(null)
    val battery = state ?: return

    val description = stringResource(
        if (battery.charging) {
            R.string.glance_battery_description_charging
        } else {
            R.string.glance_battery_description
        },
        battery.percent,
    )

    GlanceChip(
        description = description,
        onClick = {
            // Nem todo aparelho tem essa tela; sem ela o toque simplesmente não faz nada.
            val intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        },
    ) {
        BatteryIcon(percent = battery.percent, charging = battery.charging)
        ChipText(stringResource(R.string.percent, battery.percent))
    }
}

/**
 * Contorno em vetor com o nível desenhado por cima: um vetor estático não tem
 * como representar 37% de carga. As frações são as do miolo do desenho, para o
 * nível caber dentro do contorno em qualquer tamanho de ícone.
 */
@Composable
private fun BatteryIcon(percent: Int, charging: Boolean) {
    val level = MaterialTheme.colorScheme.onSurfaceVariant
    // O raio é vazado no nível — fica legível tanto sobre a carga quanto fora dela.
    val bolt = MaterialTheme.colorScheme.surface
    val fraction = percent.coerceIn(0, 100) / 100f

    Box(modifier = Modifier.size(CHIP_ICON)) {
        ChipIcon(painterResource(R.drawable.ic_battery))
        Canvas(modifier = Modifier.size(CHIP_ICON)) {
            val left = size.width * 0.15f
            val top = size.height * 0.40f
            val full = size.width * 0.575f
            val height = size.height * 0.20f
            if (fraction > 0f) {
                drawRect(
                    color = level,
                    topLeft = Offset(left, top),
                    size = Size(full * fraction, height),
                )
            }
            if (charging) drawPath(boltPath(size.width, size.height), bolt)
        }
    }
}

/** Raio de "carregando", em frações do ícone — ver [BatteryIcon]. */
private fun boltPath(width: Float, height: Float): Path {
    val points = listOf(
        0.446f to 0.360f,
        0.320f to 0.540f,
        0.397f to 0.540f,
        0.384f to 0.660f,
        0.500f to 0.480f,
        0.423f to 0.480f,
    )
    return Path().apply {
        points.forEachIndexed { index, (x, y) ->
            if (index == 0) moveTo(x * width, y * height) else lineTo(x * width, y * height)
        }
        close()
    }
}

@Composable
private fun CalendarChip(source: CalendarSource) {
    val context = LocalContext.current
    // A permissão é lida na assinatura: voltar do sistema com ela concedida
    // precisa de uma assinatura nova, e é isso que o contador de retomadas faz.
    val resumes = rememberResumeCount()
    val events by remember(source, resumes) { source.upcoming() }
        .collectAsStateWithLifecycle(emptyList())
    val next = events.firstOrNull() ?: return

    val locale = LocalLocale.current.platformLocale
    val timeFormat = rememberTimeFormat(locale)
    val time = if (next.allDay) {
        stringResource(R.string.glance_calendar_all_day)
    } else {
        timeFormat.format(Date(next.beginMillis))
    }
    val extra = events.size - 1
    val description = if (extra > 0) {
        stringResource(R.string.glance_calendar_description_more, next.title, time, extra)
    } else {
        stringResource(R.string.glance_calendar_description, next.title, time)
    }

    GlanceChip(
        description = description,
        onClick = { openEvent(context, next) },
    ) {
        ChipIcon(Icons.Outlined.DateRange)
        ChipText(next.title, modifier = Modifier.widthIn(max = CHIP_TEXT_MAX))
        ChipText(time)
        if (extra > 0) ChipText(stringResource(R.string.glance_calendar_more, extra))
    }
}

/** Abre o evento no app de agenda: o id sozinho não basta, a janela vai nos extras. */
private fun openEvent(context: Context, event: CalendarEvent) {
    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
    val intent = Intent(Intent.ACTION_VIEW, uri)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.beginMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** O formato de hora do sistema — 24h ou AM/PM, como o usuário escolheu. */
@Composable
internal fun rememberTimeFormat(locale: Locale): SimpleDateFormat {
    val context = LocalContext.current
    val is24h = remember(locale) { android.text.format.DateFormat.is24HourFormat(context) }
    return remember(locale, is24h) { SimpleDateFormat(if (is24h) "HH:mm" else "h:mm a", locale) }
}

/**
 * Quantas vezes a tela voltou ao primeiro plano. Serve de chave para o que
 * precisa ser refeito ao voltar — nada é refeito enquanto a home está parada.
 */
@Composable
internal fun rememberResumeCount(): Int {
    var count by remember { mutableIntStateOf(0) }
    // A primeira retomada é a própria entrada na tela: o que depende disto
    // acabou de ser criado, e contá-la faria tudo nascer duas vezes.
    val entered = remember { booleanArrayOf(false) }
    LifecycleResumeEffect(Unit) {
        if (entered[0]) count++ else entered[0] = true
        onPauseOrDispose { }
    }
    return count
}

/** 0 é hoje, 1 é amanhã. Em dias de calendário, não em múltiplos de 24 h. */
private fun daysFromToday(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    ChronoUnit.DAYS.between(
        Instant.now().atZone(zone).toLocalDate(),
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate(),
    )
