package app.cascata.launcher.data.glance.weather

import app.cascata.launcher.data.glance.TemperatureUnit
import kotlinx.serialization.Serializable

/** Cache velho demais para mostrar sem avisar — e o gatilho para buscar de novo. */
const val WEATHER_MAX_AGE_MILLIS = 30L * 60L * 1000L

/** O que o card precisa saber desenhar. Menos que o WMO tem, mais que "sol ou chuva". */
enum class WeatherCondition {
    CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, THUNDERSTORM, UNKNOWN
}

/**
 * Tabela WMO 4677, na faixa que o Open-Meteo usa em `weather_code`. Código fora
 * da tabela vira [WeatherCondition.UNKNOWN] em vez de quebrar o card.
 */
fun conditionFromWmoCode(code: Int): WeatherCondition = when (code) {
    0 -> WeatherCondition.CLEAR
    1, 2 -> WeatherCondition.PARTLY_CLOUDY
    3 -> WeatherCondition.CLOUDY
    45, 48 -> WeatherCondition.FOG
    in 51..57 -> WeatherCondition.DRIZZLE
    in 61..67, in 80..82 -> WeatherCondition.RAIN
    in 71..77, 85, 86 -> WeatherCondition.SNOW
    in 95..99 -> WeatherCondition.THUNDERSTORM
    else -> WeatherCondition.UNKNOWN
}

/**
 * O clima de um lugar num instante. Guardado sempre em Celsius: a unidade é
 * escolha de exibição e pode mudar sem invalidar o cache.
 */
@Serializable
data class WeatherSnapshot(
    val temperatureC: Double,
    val minC: Double? = null,
    val maxC: Double? = null,
    val condition: WeatherCondition = WeatherCondition.UNKNOWN,
    val fetchedAtMillis: Long = 0L,
    val locationLabel: String? = null,
)

/**
 * Vale mostrar sem ir à rede? Relógio andando para trás (fuso, ajuste manual)
 * conta como velho — melhor buscar de novo do que confiar num futuro.
 */
fun WeatherSnapshot.isFresh(
    nowMillis: Long,
    maxAgeMillis: Long = WEATHER_MAX_AGE_MILLIS,
): Boolean = (nowMillis - fetchedAtMillis) in 0..maxAgeMillis

/** Converte de Celsius (como está guardado) para a unidade escolhida. */
fun Double.toUnit(unit: TemperatureUnit): Double = when (unit) {
    TemperatureUnit.CELSIUS -> this
    TemperatureUnit.FAHRENHEIT -> this * 9.0 / 5.0 + 32.0
}
