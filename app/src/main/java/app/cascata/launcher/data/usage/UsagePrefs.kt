package app.cascata.launcher.data.usage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.cascata.launcher.crash.catchEmitting
import app.cascata.launcher.crash.degraded
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * As preferências do recurso — e só elas. Nenhum campo aqui descreve uso: o
 * tempo de tela é lido do sistema na hora (ver `UsageSource`).
 */
@Serializable
data class UsageSettings(
    /** A intenção do usuário. O acesso de verdade é do sistema — ver `UsageAccess`. */
    val enabled: Boolean = false,
    val showCard: Boolean = true,
    /** Quanto a tela de "respira" segura antes de liberar o app. */
    val pauseSeconds: Int = 5,
    /** Pacote -> minutos por dia. Pacote sem entrada é app sem limite. */
    val limitsMinutes: Map<String, Int> = emptyMap(),
) {
    /** Valor gravado fora da faixa (arquivo editado à mão) não vira pausa eterna. */
    fun coerced(): UsageSettings {
        val seconds = pauseSeconds.coerceIn(MIN_PAUSE_SECONDS, MAX_PAUSE_SECONDS)
        return if (seconds == pauseSeconds) this else copy(pauseSeconds = seconds)
    }

    companion object {
        const val MIN_PAUSE_SECONDS = 3
        const val MAX_PAUSE_SECONDS = 30
        val DEFAULT = UsageSettings()
    }
}

private val ENABLED = booleanPreferencesKey("enabled")
private val SHOW_CARD = booleanPreferencesKey("show_card")
private val PAUSE_SECONDS = intPreferencesKey("pause_seconds")

/** Uma preferência por app com limite, como os apelidos: mexer num não reescreve os outros. */
private const val LIMIT_PREFIX = "limit:"

private fun limitKey(packageName: String) = intPreferencesKey(LIMIT_PREFIX + packageName)

private fun limitPackage(prefName: String): String? =
    if (prefName.startsWith(LIMIT_PREFIX)) prefName.substring(LIMIT_PREFIX.length).takeIf { it.isNotEmpty() } else null

/** Tag dos avisos deste arquivo. */
private const val TAG = "CascataPrefs"

/** Arquivo próprio, como o dos cards: desligar o uso não mexe em mais nada. */
private val Context.usageDataStore: DataStore<Preferences> by preferencesDataStore(name = "cascata_usage")

class UsagePrefs(private val context: Context) {

    // Um DataStore pode lançar ao ler: arquivo corrompido por um desligamento no
    // meio da escrita (IOException) ou uma chave gravada com outro tipo por uma
    // versão anterior (ClassCastException). Quem coleta é a composição — sem
    // isto, uma preferência ilegível derruba a tela; com isto, ela volta ao
    // padrão. O segundo `catch` cobre a leitura das chaves, que é onde o tipo
    // errado aparece.
    val settings: Flow<UsageSettings> = context.usageDataStore.data
        .catch { error ->
            degraded(TAG, "preferências de uso ilegíveis", error)
            emit(emptyPreferences())
        }
        .map { it.toSettings() }
        .distinctUntilChanged()
        .catchEmitting(TAG, "preferências de uso ilegíveis", UsageSettings.DEFAULT)

    /** Lê, transforma e grava numa transação só: dois toques seguidos não se perdem. */
    suspend fun update(transform: (UsageSettings) -> UsageSettings) {
        context.usageDataStore.edit { prefs -> prefs.write(transform(prefs.toSettings()).coerced()) }
    }

    /** `null` (ou zero) remove o limite: app sem entrada abre sem passar pelo gate. */
    suspend fun setLimit(packageName: String, minutes: Int?) {
        context.usageDataStore.edit { prefs ->
            val value = minutes?.takeIf { it > 0 }
            if (value == null) prefs.remove(limitKey(packageName)) else prefs[limitKey(packageName)] = value
        }
    }

    /**
     * Grava tudo de uma vez, sem olhar o que havia — é o que a restauração
     * precisa. `write` já apaga os limites que não vieram no mapa.
     */
    suspend fun replace(settings: UsageSettings) {
        context.usageDataStore.edit { prefs -> prefs.write(settings.coerced()) }
    }

    /** Limpa as chaves: a leitura volta a devolver os defaults (recurso desligado, sem limites). */
    suspend fun reset() {
        context.usageDataStore.edit { it.clear() }
    }
}

private fun Preferences.toSettings(): UsageSettings = UsageSettings(
    enabled = this[ENABLED] ?: UsageSettings.DEFAULT.enabled,
    showCard = this[SHOW_CARD] ?: UsageSettings.DEFAULT.showCard,
    pauseSeconds = this[PAUSE_SECONDS] ?: UsageSettings.DEFAULT.pauseSeconds,
    limitsMinutes = asMap().mapNotNull { (key, value) ->
        val packageName = limitPackage(key.name) ?: return@mapNotNull null
        val minutes = value as? Int ?: return@mapNotNull null
        packageName to minutes
    }.toMap(),
).coerced()

private fun MutablePreferences.write(settings: UsageSettings) {
    this[ENABLED] = settings.enabled
    this[SHOW_CARD] = settings.showCard
    this[PAUSE_SECONDS] = settings.pauseSeconds
    // O mapa que chega manda: limite que saiu dele sai também do arquivo.
    asMap().keys.filter { limitPackage(it.name) != null }.forEach { remove(it) }
    settings.limitsMinutes.forEach { (packageName, minutes) ->
        if (minutes > 0) this[limitKey(packageName)] = minutes
    }
}
