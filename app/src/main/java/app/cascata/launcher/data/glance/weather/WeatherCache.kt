package app.cascata.launcher.data.glance.weather

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.cascata.launcher.crash.degraded
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val SNAPSHOT = stringPreferencesKey("snapshot")

/**
 * DataStore e não um arquivo em `filesDir`: o que precisamos é exatamente o que
 * ele dá de graça — escrita atômica (sem cache meio escrito depois de um
 * desligamento) e um Flow que re-emite sozinho quando o valor muda. Um arquivo
 * exigiria escrever as duas coisas à mão. O conteúdo é um JSON só, então uma
 * chave de texto basta — nada de uma chave por campo, isto é cache e não
 * preferência do usuário.
 */
private val Context.weatherDataStore: DataStore<Preferences> by preferencesDataStore(name = "cascata_weather")

/** Tag dos avisos deste arquivo. */
private const val TAG = "CascataPrefs"

/** O último clima buscado. Único dado de rede que o app guarda. */
class WeatherCache(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Null quando não há cache, ou quando o guardado é de um formato que não lemos mais. */
    val snapshot: Flow<WeatherSnapshot?> = context.weatherDataStore.data
        // Cache ilegível é cache vazio: o card mostra o que o `refresh` trouxer.
        .catch { error ->
            degraded(TAG, "cache de clima ilegível", error)
            emit(emptyPreferences())
        }
        .map { prefs ->
            prefs[SNAPSHOT]?.let { text ->
                runCatching { json.decodeFromString<WeatherSnapshot>(text) }.getOrNull()
            }
        }
        .distinctUntilChanged()

    suspend fun save(snapshot: WeatherSnapshot) {
        context.weatherDataStore.edit { it[SNAPSHOT] = json.encodeToString(snapshot) }
    }

    /** Desligar o card apaga o cache: função desligada não deixa rastro. */
    suspend fun clear() {
        context.weatherDataStore.edit { it.remove(SNAPSHOT) }
    }
}
