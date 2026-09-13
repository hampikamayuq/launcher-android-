package app.cascata.launcher.data.glance.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import app.cascata.launcher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/** Conectar e ler: a previsão é pequena, e o card prefere falhar a travar. */
private const val HTTP_TIMEOUT_MILLIS = 10_000

/** Quanto esperamos por uma posição nova quando não há nenhuma guardada. */
private const val CURRENT_LOCATION_TIMEOUT_MILLIS = 15_000L

/**
 * Sem nome de aparelho, sem id, sem chave de API — só o nome e a versão do app,
 * que é o que um servidor público precisa para saber com quem fala.
 */
private val USER_AGENT = "Cascata/${BuildConfig.VERSION_NAME}"

/**
 * Clima pelo Open-Meteo (sem chave, sem cadastro), com a posição grosseira que o
 * próprio [LocationManager] já tem — nada de Play Services e nada de GPS ligado
 * por nossa conta. A permissão não é pedida aqui: quem pede é a UI ao ligar o card.
 */
class OpenMeteoWeatherSource(
    private val context: Context,
    private val cache: WeatherCache,
) : WeatherSource {

    private val locationManager = context.getSystemService(LocationManager::class.java)

    override val available: Boolean = true

    override fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun snapshot(): Flow<WeatherSnapshot?> = cache.snapshot

    override suspend fun refresh(force: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            return@withContext Result.failure(SecurityException("sem permissão de localização"))
        }
        // Cache fresco não vira requisição: abrir a home não fala com a internet.
        if (!force && cache.snapshot.first()?.isFresh(System.currentTimeMillis()) == true) {
            return@withContext Result.success(Unit)
        }
        val location = location().getOrElse { return@withContext Result.failure(it) }
        runCatching {
            val body = get(buildForecastUrl(location.latitude, location.longitude))
            cache.save(parseForecast(body, System.currentTimeMillis()).getOrThrow())
        }
    }

    /**
     * A posição mais recente que o sistema já tem, de qualquer provedor — é de
     * graça e basta para o clima. Só se não houver nenhuma pedimos uma nova.
     */
    @SuppressLint("MissingPermission") // refresh() confere hasLocationPermission() antes de chegar aqui.
    private suspend fun location(): Result<Location> {
        val manager = locationManager
            ?: return Result.failure(IllegalStateException("aparelho sem serviço de localização"))
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
            LocationManager.GPS_PROVIDER,
        )
        val known = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (known != null) return Result.success(known)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return Result.failure(
                IllegalStateException("sem localização recente; abra um app de mapas uma vez")
            )
        }
        val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER &&
            runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return Result.failure(IllegalStateException("localização desligada no aparelho"))
        val current = currentLocation(manager, provider)
            ?: return Result.failure(IllegalStateException("a localização demorou demais"))
        return Result.success(current)
    }

    /** Uma única posição, com prazo: sem isso o card ficaria "carregando" para sempre. */
    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("MissingPermission") // idem: a permissão já foi conferida em refresh().
    private suspend fun currentLocation(manager: LocationManager, provider: String): Location? =
        withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(context),
                ) { location -> if (continuation.isActive) continuation.resume(location) }
            }
        }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = HTTP_TIMEOUT_MILLIS
        connection.readTimeout = HTTP_TIMEOUT_MILLIS
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "application/json")
        try {
            val code = connection.responseCode
            require(code == HttpURLConnection.HTTP_OK) { "a previsão respondeu $code" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
