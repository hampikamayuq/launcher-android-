package app.cascata.launcher.data.glance.weather

import kotlinx.coroutines.flow.Flow

/**
 * De onde o card de clima tira o que mostrar. A implementação de verdade só
 * existe na edição `full`; na `lite` [available] é false e o card não aparece
 * (ver `WeatherSourceFactory`, que tem uma versão por flavor).
 *
 * Ninguém aqui pede permissão: a UI chama [hasLocationPermission], pede se
 * precisar e só então chama [refresh].
 */
interface WeatherSource {

    /** Esta edição do app sabe buscar clima? `false` no `lite`. */
    val available: Boolean

    fun hasLocationPermission(): Boolean

    /** O cache, sem tocar na rede: emite o que está guardado e de novo a cada [refresh] bem-sucedido. */
    fun snapshot(): Flow<WeatherSnapshot?>

    /**
     * Busca e grava. Com [force] `false` não vai à rede se o cache ainda está
     * fresco (30 min). A falha vem no [Result] — SecurityException quando falta
     * a permissão de localização, e a mensagem do erro no resto.
     */
    suspend fun refresh(force: Boolean = false): Result<Unit>
}
