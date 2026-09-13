package app.cascata.launcher.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import app.cascata.launcher.data.glance.CalendarSource
import app.cascata.launcher.data.glance.GlancePrefs
import app.cascata.launcher.data.glance.GlanceSettings
import app.cascata.launcher.data.glance.TemperatureUnit
import app.cascata.launcher.data.glance.weather.WeatherCache
import app.cascata.launcher.data.glance.weather.WeatherSource
import app.cascata.launcher.data.theme.ClockStyle
import app.cascata.launcher.data.theme.ThemeSettings
import kotlinx.coroutines.launch

/**
 * Os cards do topo da home, um interruptor cada. A regra da seção é a frase do
 * rodapé: nenhuma permissão é pedida na instalação nem ao abrir esta tela — só
 * no instante em que o card que precisa dela é ligado.
 */
@Composable
internal fun GlanceSection(
    settings: ThemeSettings,
    glance: GlanceSettings,
    glancePrefs: GlancePrefs,
    calendarSource: CalendarSource,
    weatherSource: WeatherSource,
    weatherCache: WeatherCache,
    update: UpdateSettings,
) {
    val scope = rememberCoroutineScope()
    val write: ((GlanceSettings) -> GlanceSettings) -> Unit = { transform ->
        scope.launch { glancePrefs.update(transform) }
    }

    // Negada agora, nesta tela: o recado só faz sentido logo depois do diálogo.
    var calendarDenied by remember { mutableStateOf(false) }
    var locationDenied by remember { mutableStateOf(false) }

    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        calendarDenied = !granted
        // Só grava se concedeu: card ligado sem permissão seria um card vazio.
        if (granted) write { it.copy(showCalendar = true) }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        locationDenied = !granted
        if (granted) {
            write { it.copy(showWeather = true) }
            // Primeira busca na hora: o card já nasce com temperatura.
            scope.launch { weatherSource.refresh(force = true) }
        }
    }

    SettingsSection(stringResource(R.string.section_glance)) {
        SegmentedChoice(
            label = stringResource(R.string.clock_style),
            options = listOf(
                ClockStyle.BASIC to stringResource(R.string.clock_style_basic),
                ClockStyle.BIG to stringResource(R.string.clock_style_big),
                ClockStyle.TWO_LINE to stringResource(R.string.clock_style_two_line),
                ClockStyle.ANALOG to stringResource(R.string.clock_style_analog),
            ),
            selected = settings.clockStyle,
            onSelect = { style -> update { it.copy(clockStyle = style) } },
        )

        SwitchRow(
            label = stringResource(R.string.glance_alarm),
            checked = glance.showAlarm,
            onCheckedChange = { on -> write { it.copy(showAlarm = on) } },
        )

        SwitchRow(
            label = stringResource(R.string.glance_battery),
            checked = glance.showBattery,
            onCheckedChange = { on -> write { it.copy(showBattery = on) } },
        )

        SwitchRow(
            label = stringResource(R.string.glance_calendar),
            checked = glance.showCalendar,
            supporting = if (calendarDenied) stringResource(R.string.glance_calendar_denied) else null,
            action = if (calendarDenied) {
                { AppSettingsButton() }
            } else {
                null
            },
            onCheckedChange = { on ->
                when {
                    !on -> {
                        calendarDenied = false
                        write { it.copy(showCalendar = false) }
                    }

                    calendarSource.hasPermission() -> {
                        calendarDenied = false
                        write { it.copy(showCalendar = true) }
                    }

                    else -> calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                }
            },
        )

        if (!weatherSource.available) {
            // Edição `lite`: sem INTERNET no manifesto não há clima, e um
            // interruptor que não liga nada é pior que um interruptor apagado.
            SwitchRow(
                label = stringResource(R.string.glance_weather),
                checked = false,
                enabled = false,
                supporting = stringResource(R.string.glance_weather_unavailable),
                onCheckedChange = {},
            )
        } else {
            SwitchRow(
                label = stringResource(R.string.glance_weather),
                checked = glance.showWeather,
                supporting = if (locationDenied) {
                    stringResource(R.string.glance_location_denied)
                } else {
                    null
                },
                action = if (locationDenied) {
                    { AppSettingsButton() }
                } else {
                    null
                },
                onCheckedChange = { on ->
                    when {
                        !on -> {
                            locationDenied = false
                            write { it.copy(showWeather = false) }
                            // Card desligado não deixa rastro: o cache vai junto.
                            scope.launch { weatherCache.clear() }
                        }

                        weatherSource.hasLocationPermission() -> {
                            locationDenied = false
                            write { it.copy(showWeather = true) }
                            scope.launch { weatherSource.refresh(force = true) }
                        }

                        else -> locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                },
            )

            if (glance.showWeather) {
                SegmentedChoice(
                    label = stringResource(R.string.temperature_unit),
                    options = listOf(
                        TemperatureUnit.CELSIUS to stringResource(R.string.temperature_celsius),
                        TemperatureUnit.FAHRENHEIT to stringResource(R.string.temperature_fahrenheit),
                    ),
                    selected = glance.temperatureUnit,
                    onSelect = { unit -> write { it.copy(temperatureUnit = unit) } },
                )
            }
        }

        Text(
            text = stringResource(R.string.glance_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SIDE_PADDING, vertical = 8.dp),
        )
    }
}

/**
 * Depois de "não perguntar de novo" o diálogo não volta — o único caminho é a
 * tela do app nas configurações do sistema.
 */
@Composable
private fun AppSettingsButton() {
    val context = LocalContext.current
    TextButton(
        onClick = {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        },
    ) {
        Text(stringResource(R.string.glance_open_app_settings))
    }
}
