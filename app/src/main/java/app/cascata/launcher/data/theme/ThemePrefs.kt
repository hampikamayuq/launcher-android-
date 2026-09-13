package app.cascata.launcher.data.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val DARK_MODE = stringPreferencesKey("dark_mode")
private val COLOR_SOURCE = stringPreferencesKey("color_source")
private val ACCENT_ARGB = intPreferencesKey("accent_argb")
private val BACKGROUND_OPACITY = floatPreferencesKey("background_opacity")
private val DENSITY = stringPreferencesKey("density")
private val FONT_SCALE = floatPreferencesKey("font_scale")
private val FONT_ID = stringPreferencesKey("font_id")
private val ICON_PACK = stringPreferencesKey("icon_pack")

/**
 * Arquivo separado do "cascata" de propósito: assim `reset()` devolve a aparência
 * ao padrão sem encostar em favoritos, apelidos e apps escondidos.
 */
private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "cascata_theme")

/** A aparência persistida. Uma chave por campo — nada de blob serializado aqui. */
class ThemePrefs(private val context: Context) {

    val settings: Flow<ThemeSettings> = context.themeDataStore.data
        .map { it.toSettings() }
        .distinctUntilChanged()

    /** Lê, transforma e grava numa transação só: dois toques seguidos não se perdem. */
    suspend fun update(transform: (ThemeSettings) -> ThemeSettings) {
        context.themeDataStore.edit { prefs ->
            prefs.write(transform(prefs.toSettings()).coerced())
        }
    }

    suspend fun replace(settings: ThemeSettings) {
        context.themeDataStore.edit { prefs -> prefs.write(settings.coerced()) }
    }

    /** Limpa as chaves: o que a leitura devolver a partir daqui são os defaults. */
    suspend fun reset() {
        context.themeDataStore.edit { it.clear() }
    }
}

private fun Preferences.toSettings(): ThemeSettings = ThemeSettings(
    darkMode = enumOr(this[DARK_MODE], DarkMode.SYSTEM),
    colorSource = enumOr(this[COLOR_SOURCE], ColorSource.SYSTEM),
    accentArgb = this[ACCENT_ARGB] ?: ThemeSettings.DEFAULT.accentArgb,
    backgroundOpacity = this[BACKGROUND_OPACITY] ?: ThemeSettings.DEFAULT.backgroundOpacity,
    density = enumOr(this[DENSITY], Density.DEFAULT),
    fontScale = this[FONT_SCALE] ?: ThemeSettings.DEFAULT.fontScale,
    fontId = this[FONT_ID] ?: ThemeSettings.DEFAULT.fontId,
    iconPack = this[ICON_PACK],
).coerced()

private fun MutablePreferences.write(settings: ThemeSettings) {
    this[DARK_MODE] = settings.darkMode.name
    this[COLOR_SOURCE] = settings.colorSource.name
    this[ACCENT_ARGB] = settings.accentArgb
    this[BACKGROUND_OPACITY] = settings.backgroundOpacity
    this[DENSITY] = settings.density.name
    this[FONT_SCALE] = settings.fontScale
    this[FONT_ID] = settings.fontId
    val pack = settings.iconPack
    if (pack == null) remove(ICON_PACK) else this[ICON_PACK] = pack
}

/** Enum gravado pelo `name`; valor desconhecido (versão mais nova, chave mexida) cai no default. */
private inline fun <reified T : Enum<T>> enumOr(raw: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: default
