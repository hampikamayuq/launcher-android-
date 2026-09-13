package app.cascata.launcher.ui

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import app.cascata.launcher.settings.SettingsActivity
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date

/** Relógio e data. Acorda só no minuto seguinte — nada de tick por segundo. */
@Composable
fun ClockHeader(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Observável: trocar o idioma do sistema refaz os formatos sem reiniciar o app.
    val locale = LocalLocale.current.platformLocale
    val timeFormat = remember(locale) {
        SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", locale)
    }
    val dateFormat = remember(locale) { SimpleDateFormat("EEEE, d 'de' MMMM", locale) }

    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            val millis = System.currentTimeMillis()
            delay(60_000 - millis % 60_000)
            now = Date()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { openClock(context) }
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = timeFormat.format(now),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = dateFormat.format(now).replaceFirstChar { it.uppercase(locale) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

private fun openClock(context: Context) {
    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openSettings(context: Context) {
    runCatching { context.startActivity(Intent(context, SettingsActivity::class.java)) }
}
