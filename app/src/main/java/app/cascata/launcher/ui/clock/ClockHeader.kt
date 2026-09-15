package app.cascata.launcher.ui.clock

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cascata.launcher.R
import app.cascata.launcher.data.theme.ClockStyle
import app.cascata.launcher.settings.SettingsActivity
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Dígitos do estilo de duas linhas: maior do que qualquer estilo da tipografia. */
private val TWO_LINE_SIZE = 64.sp

/** Lado do mostrador analógico, com a data ao lado. */
private val DIAL_SIZE = 96.dp

/**
 * Relógio e data, no desenho escolhido em [clockStyle]. Acorda só no minuto
 * seguinte — nada de tick por segundo, e é por isso que o analógico não tem
 * ponteiro de segundos.
 */
@Composable
fun ClockHeader(clockStyle: ClockStyle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Observável: trocar o idioma do sistema refaz os formatos sem reiniciar o app.
    val locale = LocalLocale.current.platformLocale
    val is24h = remember(locale) {
        runCatching { android.text.format.DateFormat.is24HourFormat(context) }.getOrDefault(true)
    }

    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            val millis = System.currentTimeMillis()
            delay(60_000 - millis % 60_000)
            now = Date()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button) { openClock(context) }
                .padding(vertical = 8.dp)
        ) {
            ClockFace(clockStyle = clockStyle, now = now, is24h = is24h, locale = locale)
        }

        // A porta das configurações fica aqui, visível: a alternativa seria um
        // gesto escondido, e o launcher já usa o swipe-up para a busca.
        IconButton(onClick = { openSettings(context) }) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.settings_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * O desenho do relógio, com o instante vindo de fora. Separado de [ClockHeader]
 * porque quem chama de fora do aparelho — a prévia de screenshot — precisa de
 * uma hora fixa: senão a imagem mudaria a cada renderização.
 */
@Composable
internal fun ClockFace(clockStyle: ClockStyle, now: Date, is24h: Boolean, locale: Locale) {
    // Formatar é a primeira coisa que a home desenha: um `Locale` que o ICU do
    // aparelho não conhece lançaria aqui e levaria a tela junto. Sem formato, o
    // texto some — o relógio continua, o app continua.
    val timeFormat = remember(locale, is24h) {
        runCatching { SimpleDateFormat(if (is24h) "HH:mm" else "h:mm a", locale) }.getOrNull()
    }
    // O padrão vem do idioma, não de um literal em português: "quinta-feira, 12
    // de fevereiro", "Thursday, February 12", "jueves, 12 de febrero".
    val dateFormat = remember(locale) {
        runCatching {
            SimpleDateFormat(
                android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"),
                locale,
            )
        }.getOrNull()
    }
    val time = remember(timeFormat, now) {
        runCatching { timeFormat?.format(now) }.getOrNull().orEmpty()
    }
    val date = remember(dateFormat, now, locale) {
        runCatching { dateFormat?.format(now)?.replaceFirstChar { it.uppercase(locale) } }
            .getOrNull()
            .orEmpty()
    }

    when (clockStyle) {
        ClockStyle.BASIC -> StackedClock(
            time = time,
            date = date,
            timeStyle = MaterialTheme.typography.displayMedium,
            dateStyle = MaterialTheme.typography.bodyMedium,
        )

        ClockStyle.BIG -> StackedClock(
            time = time,
            date = date,
            timeStyle = MaterialTheme.typography.displayLarge,
            dateStyle = MaterialTheme.typography.bodyLarge,
        )

        ClockStyle.TWO_LINE -> TwoLineClock(
            now = now,
            date = date,
            is24h = is24h,
            locale = locale,
        )

        ClockStyle.ANALOG -> AnalogClockRow(
            now = now,
            date = date,
            time = time,
        )
    }
}

/** Um pedaço de data formatado, ou vazio quando o aparelho recusa o padrão. */
private fun format(pattern: String, locale: Locale, now: Date): String =
    runCatching { SimpleDateFormat(pattern, locale).format(now) }.getOrDefault("")

/** Hora em cima, data embaixo: serve ao estilo básico e ao de dígitos grandes. */
@Composable
private fun StackedClock(
    time: String,
    date: String,
    timeStyle: TextStyle,
    dateStyle: TextStyle,
) {
    Column {
        Text(
            text = time,
            style = timeStyle,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = date,
            style = dateStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Horas numa linha, minutos na seguinte. Em formato de 12 horas o AM/PM vai ao
 * lado dos minutos — na linha das horas ele roubaria a largura dos dígitos.
 */
@Composable
private fun TwoLineClock(now: Date, date: String, is24h: Boolean, locale: Locale) {
    // Mesmo cuidado do formato de cima: um `Locale` que o aparelho não conhece
    // não pode derrubar a tela inicial — no pior caso o dígito não aparece.
    val hour = remember(locale, is24h, now) { format(if (is24h) "HH" else "h", locale, now) }
    val minute = remember(locale, now) { format("mm", locale, now) }
    val marker = remember(locale, now) { format("a", locale, now) }

    val digits = MaterialTheme.typography.displayLarge.copy(
        fontSize = TWO_LINE_SIZE,
        lineHeight = TWO_LINE_SIZE,
        fontWeight = FontWeight.Light,
    )

    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = hour,
            style = digits,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = minute,
                style = digits,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!is24h) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = marker,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        Text(
            text = date,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Mostrador ao lado da data — empilhar os dois deixaria o cabeçalho alto demais. */
@Composable
private fun AnalogClockRow(now: Date, date: String, time: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // O desenho é o único lugar onde a hora aparece neste estilo: sem esta
        // descrição, quem usa leitor de tela ouviria só a data.
        AnalogDial(
            now = now,
            size = DIAL_SIZE,
            modifier = Modifier.semantics { contentDescription = time },
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = date,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun openClock(context: Context) {
    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openSettings(context: Context) {
    runCatching { context.startActivity(Intent(context, SettingsActivity::class.java)) }
}
