package app.cascata.launcher.data.glance.weather

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Edição `lite`: não há INTERNET no manifesto, então não há como buscar clima —
 * e é esse o ponto. A fonte existe só para a UI não precisar de `if` por flavor:
 * ela olha [WeatherSource.available] e some com o card.
 */
object WeatherSourceFactory {
    fun create(context: Context, cache: WeatherCache): WeatherSource = UnavailableWeatherSource
}

private object UnavailableWeatherSource : WeatherSource {
    override val available: Boolean = false
    override fun hasLocationPermission(): Boolean = false
    override fun snapshot(): Flow<WeatherSnapshot?> = flowOf(null)
    override suspend fun refresh(force: Boolean): Result<Unit> =
        Result.failure(UnsupportedOperationException("esta edição do Cascata não usa rede"))
}
