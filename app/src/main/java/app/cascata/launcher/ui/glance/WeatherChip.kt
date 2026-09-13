package app.cascata.launcher.ui.glance

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cascata.launcher.R
import app.cascata.launcher.data.glance.TemperatureUnit
import app.cascata.launcher.data.glance.weather.WeatherCondition
import app.cascata.launcher.data.glance.weather.WeatherSource
import app.cascata.launcher.data.glance.weather.toUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Quanto tempo o erro fica no chip antes de dar lugar ao clima de novo. */
private const val ERROR_MILLIS = 3_000L

/** Lado da rodinha de progresso: o mesmo do ícone, para o chip não pular de largura. */
private val SPINNER = CHIP_ICON

/**
 * Clima. A rede é assunto da fonte: aqui só se pede `refresh`, e ela decide se
 * vale uma requisição (`force = false` respeita o cache de 30 minutos) ou não.
 *
 * Sem Snackbar: a home não tem Scaffold e um erro de clima não merece uma. A
 * falha aparece no próprio chip por [ERROR_MILLIS] e some.
 */
@Composable
internal fun WeatherChip(source: WeatherSource, unit: TemperatureUnit) {
    val scope = rememberCoroutineScope()
    val snapshot by remember(source) { source.snapshot() }.collectAsStateWithLifecycle(null)
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val failed = stringResource(R.string.glance_weather_failed)
    val locale = LocalLocale.current.platformLocale

    // Voltar para a home é o gatilho: com cache fresco a fonte nem abre socket.
    LifecycleResumeEffect(source) {
        scope.launch { source.refresh(force = false) }
        onPauseOrDispose { }
    }

    LaunchedEffect(error) {
        if (error != null) {
            delay(ERROR_MILLIS)
            error = null
        }
    }

    val current = snapshot
    val temperature = current?.let {
        stringResource(R.string.glance_weather_temperature, it.temperatureC.toUnit(unit).roundToInt())
    }
    val condition = stringResource(current?.condition.label())

    val description = when {
        loading -> stringResource(R.string.glance_weather_updating)
        error != null -> error.orEmpty()
        temperature == null -> stringResource(R.string.glance_weather_empty_description)
        else -> stringResource(R.string.glance_weather_description, temperature, condition)
    }

    GlanceChip(
        description = description,
        onClick = {
            if (!loading) {
                scope.launch {
                    loading = true
                    val result = source.refresh(force = true)
                    loading = false
                    result.onFailure { cause ->
                        // A mensagem vem da fonte, já em português ("sem localização
                        // recente", "localização desligada no aparelho").
                        error = cause.message?.replaceFirstChar { it.uppercase(locale) } ?: failed
                    }
                }
            }
        },
    ) {
        when {
            loading -> CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(SPINNER),
            )

            error != null -> ChipText(error.orEmpty())

            current == null -> {
                ChipIcon(painterResource(R.drawable.ic_weather_unknown))
                ChipText(stringResource(R.string.glance_weather_empty))
            }

            else -> {
                ChipIcon(painterResource(current.condition.icon()))
                ChipText(temperature.orEmpty())
                current.locationLabel?.takeIf { it.isNotBlank() }?.let { place ->
                    Text(
                        text = place,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Um vetor por condição, desenhados para este app. */
@DrawableRes
private fun WeatherCondition.icon(): Int = when (this) {
    WeatherCondition.CLEAR -> R.drawable.ic_weather_clear
    WeatherCondition.PARTLY_CLOUDY -> R.drawable.ic_weather_partly_cloudy
    WeatherCondition.CLOUDY -> R.drawable.ic_weather_cloudy
    WeatherCondition.FOG -> R.drawable.ic_weather_fog
    WeatherCondition.DRIZZLE -> R.drawable.ic_weather_drizzle
    WeatherCondition.RAIN -> R.drawable.ic_weather_rain
    WeatherCondition.SNOW -> R.drawable.ic_weather_snow
    WeatherCondition.THUNDERSTORM -> R.drawable.ic_weather_thunderstorm
    WeatherCondition.UNKNOWN -> R.drawable.ic_weather_unknown
}

/** O nome falado da condição: o ícone não tem descrição, o chip inteiro tem. */
@StringRes
private fun WeatherCondition?.label(): Int = when (this) {
    WeatherCondition.CLEAR -> R.string.weather_clear
    WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
    WeatherCondition.CLOUDY -> R.string.weather_cloudy
    WeatherCondition.FOG -> R.string.weather_fog
    WeatherCondition.DRIZZLE -> R.string.weather_drizzle
    WeatherCondition.RAIN -> R.string.weather_rain
    WeatherCondition.SNOW -> R.string.weather_snow
    WeatherCondition.THUNDERSTORM -> R.string.weather_thunderstorm
    WeatherCondition.UNKNOWN, null -> R.string.weather_unknown
}
