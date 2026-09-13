package app.cascata.launcher.data.glance

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val SHOW_ALARM = booleanPreferencesKey("show_alarm")
private val SHOW_BATTERY = booleanPreferencesKey("show_battery")
private val SHOW_CALENDAR = booleanPreferencesKey("show_calendar")
private val SHOW_WEATHER = booleanPreferencesKey("show_weather")
private val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")

/**
 * Arquivo próprio, como o do tema: desligar todos os cards não pode mexer em
 * favoritos nem na aparência.
 */
private val Context.glanceDataStore: DataStore<Preferences> by preferencesDataStore(name = "cascata_glance")

/** O que está ligado no topo da home. Uma chave por card. */
class GlancePrefs(private val context: Context) {

    val settings: Flow<GlanceSettings> = context.glanceDataStore.data
        .map { it.toSettings() }
        .distinctUntilChanged()

    /** Lê, transforma e grava numa transação só: dois toques seguidos não se perdem. */
    suspend fun update(transform: (GlanceSettings) -> GlanceSettings) {
        context.glanceDataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
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
