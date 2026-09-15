package app.cascata.launcher.data.glance

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.cascata.launcher.crash.catchEmitting
import app.cascata.launcher.crash.degraded
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val SHOW_ALARM = booleanPreferencesKey("show_alarm")
private val SHOW_BATTERY = booleanPreferencesKey("show_battery")
private val SHOW_CALENDAR = booleanPreferencesKey("show_calendar")
private val SHOW_WEATHER = booleanPreferencesKey("show_weather")
private val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")

/** Tag dos avisos deste arquivo. */
private const val TAG = "CascataPrefs"

/**
 * Arquivo próprio, como o do tema: desligar todos os cards não pode mexer em
 * favoritos nem na aparência.
 */
private val Context.glanceDataStore: DataStore<Preferences> by preferencesDataStore(name = "cascata_glance")

/** O que está ligado no topo da home. Uma chave por card. */
class GlancePrefs(private val context: Context) {

    // Um DataStore pode lançar ao ler: arquivo corrompido por um desligamento no
    // meio da escrita (IOException) ou uma chave gravada com outro tipo por uma
    // versão anterior (ClassCastException). Quem coleta é a composição — sem
    // isto, uma preferência ilegível derruba a tela; com isto, ela volta ao
    // padrão. O segundo `catch` cobre a leitura das chaves, que é onde o tipo
    // errado aparece.
    val settings: Flow<GlanceSettings> = context.glanceDataStore.data
        .catch { error ->
            degraded(TAG, "cards do topo ilegíveis", error)
            emit(emptyPreferences())
        }
        .map { it.toSettings() }
        .distinctUntilChanged()
        .catchEmitting(TAG, "cards do topo ilegíveis", GlanceSettings.DEFAULT)

    /** Lê, transforma e grava numa transação só: dois toques seguidos não se perdem. */
    suspend fun update(transform: (GlanceSettings) -> GlanceSettings) {
        context.glanceDataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    /** Grava tudo de uma vez, sem olhar o que havia — é o que a restauração precisa. */
    suspend fun replace(settings: GlanceSettings) {
        context.glanceDataStore.edit { prefs -> prefs.write(settings) }
    }

    /** Limpa as chaves: a leitura volta a devolver os defaults (tudo desligado). */
    suspend fun reset() {
        context.glanceDataStore.edit { it.clear() }
    }
}

private fun Preferences.toSettings(): GlanceSettings = GlanceSettings(
    showAlarm = this[SHOW_ALARM] ?: GlanceSettings.DEFAULT.showAlarm,
    showBattery = this[SHOW_BATTERY] ?: GlanceSettings.DEFAULT.showBattery,
    showCalendar = this[SHOW_CALENDAR] ?: GlanceSettings.DEFAULT.showCalendar,
    showWeather = this[SHOW_WEATHER] ?: GlanceSettings.DEFAULT.showWeather,
    temperatureUnit = enumOr(this[TEMPERATURE_UNIT], TemperatureUnit.CELSIUS),
)

private fun MutablePreferences.write(settings: GlanceSettings) {
    this[SHOW_ALARM] = settings.showAlarm
    this[SHOW_BATTERY] = settings.showBattery
    this[SHOW_CALENDAR] = settings.showCalendar
    this[SHOW_WEATHER] = settings.showWeather
    this[TEMPERATURE_UNIT] = settings.temperatureUnit.name
}

/** Enum gravado pelo `name`; valor desconhecido cai no default. */
private inline fun <reified T : Enum<T>> enumOr(raw: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: default
