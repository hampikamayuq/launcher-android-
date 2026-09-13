package app.cascata.launcher.data.glance.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/** Endpoint público do Open-Meteo: sem chave, sem cadastro, sem cota por app. */
private const val FORECAST_ENDPOINT = "https://api.open-meteo.com/v1/forecast"

private val json = Json {
    // A API acrescenta campos (unidades, `interval`, `elevation`) sem avisar.
    ignoreUnknownKeys = true
}

/**
 * A URL da previsão. Fica aqui, pura, para ser conferida em teste — a montagem
 * de URL é onde vazamento de dado acontece, e este arquivo prova que só vão
 * duas coordenadas.
 *
 * As coordenadas são arredondadas a duas casas (~1 km): o suficiente para o
 * clima, grosseiro demais para dizer onde a pessoa está dentro da cidade.
 */
fun buildForecastUrl(lat: Double, lon: Double): String {
    val latText = String.format(Locale.ROOT, "%.2f", lat)
    val lonText = String.format(Locale.ROOT, "%.2f", lon)
    return "$FORECAST_ENDPOINT?latitude=$latText&longitude=$lonText" +
        "&current=temperature_2m,weather_code" +
        "&daily=temperature_2m_max,temperature_2m_min" +
        "&timezone=auto&forecast_days=1"
}

/**
 * Lê a resposta da previsão. Devolve falha (nunca lança) quando o JSON é
 * inválido ou quando falta a temperatura atual — o resto é opcional e o card
 * sabe viver sem.
 */
fun parseForecast(text: String, nowMillis: Long): Result<WeatherSnapshot> = runCatching {
    val forecast = json.decodeFromString<ForecastResponse>(text)
    val current = requireNotNull(forecast.current) { "resposta sem o bloco `current`" }
    val temperature = requireNotNull(current.temperature) { "resposta sem temperature_2m" }
    WeatherSnapshot(
        temperatureC = temperature,
        minC = forecast.daily?.min?.firstOrNull(),
        maxC = forecast.daily?.max?.firstOrNull(),
        condition = current.weatherCode?.let(::conditionFromWmoCode) ?: WeatherCondition.UNKNOWN,
        fetchedAtMillis = nowMillis,
        locationLabel = forecast.timezone?.toLocationLabel(),
    )
}

/**
 * `timezone=auto` volta como "America/Sao_Paulo". A última parte serve de rótulo
 * do lugar sem precisar de um segundo serviço (geocodificação reversa); um fuso
 * sem cidade ("UTC", "GMT") não vira rótulo nenhum.
 */
private fun String.toLocationLabel(): String? =
    substringAfterLast('/').replace('_', ' ').takeIf { contains('/') && it.isNotBlank() }

@Serializable
private data class ForecastResponse(
    val timezone: String? = null,
    val current: CurrentBlock? = null,
    val daily: DailyBlock? = null,
)

@Serializable
private data class CurrentBlock(
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
)

@Serializable
private data class DailyBlock(
    @SerialName("temperature_2m_max") val max: List<Double> = emptyList(),
    @SerialName("temperature_2m_min") val min: List<Double> = emptyList(),
)
