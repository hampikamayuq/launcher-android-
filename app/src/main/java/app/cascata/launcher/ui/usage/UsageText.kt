package app.cascata.launcher.ui.usage

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.cascata.launcher.R

/** O tempo como ele aparece na tela: "1 h 20 min", "2 h", "35 min". */
@Composable
internal fun durationText(millis: Long): String = when (val duration = formatDuration(millis)) {
    is UsageDuration.Minutes -> stringResource(R.string.usage_minutes, duration.value)
    is UsageDuration.Hours -> stringResource(R.string.usage_hours, duration.value)
    is UsageDuration.HoursMinutes ->
        stringResource(R.string.usage_hours_minutes, duration.hours, duration.minutes)
}

/**
 * O mesmo tempo por extenso, para leitores de tela: "1 hora e 20 minutos". "1 h"
 * lido em voz alta vira "um h", e o card do topo é lido inteiro de uma vez.
 */
@Composable
internal fun durationSpoken(millis: Long): String = when (val duration = formatDuration(millis)) {
    is UsageDuration.Minutes -> spokenMinutes(duration.value)
    is UsageDuration.Hours -> spokenHours(duration.value)
    is UsageDuration.HoursMinutes -> stringResource(
        R.string.usage_spoken_hours_minutes,
        spokenHours(duration.hours),
        spokenMinutes(duration.minutes),
    )
}

@Composable
private fun spokenHours(value: Int): String =
    pluralStringResource(R.plurals.usage_spoken_hours, value, value)

@Composable
private fun spokenMinutes(value: Int): String =
    pluralStringResource(R.plurals.usage_spoken_minutes, value, value)

/** A linha de limite de um app: "Limite: 30 min" ou "Sem limite". */
@Composable
internal fun limitText(minutes: Int?): String =
    if (minutes == null) {
        stringResource(R.string.usage_limit_none)
    } else {
        stringResource(R.string.usage_limit_value, minutes)
    }
