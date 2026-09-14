package app.cascata.launcher.data.search

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
import kotlinx.serialization.Serializable

/**
 * O que a busca mostra além dos apps. Contatos começam desligados porque são o
 * único item que pede permissão; o resto não custa nada e vem ligado.
 */
@Serializable
data class SearchSettings(
    val engine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val showShortcuts: Boolean = true,
    val showCalculator: Boolean = true,
    val showContacts: Boolean = false,
    val showSettings: Boolean = true,
    val showWeb: Boolean = true,
) {
    companion object {
        val DEFAULT = SearchSettings()
    }
}

private val ENGINE = stringPreferencesKey("engine")
private val SHOW_SHORTCUTS = booleanPreferencesKey("show_shortcuts")
private val SHOW_CALCULATOR = booleanPreferencesKey("show_calculator")
private val SHOW_CONTACTS = booleanPreferencesKey("show_contacts")
private val SHOW_SETTINGS = booleanPreferencesKey("show_settings")
private val SHOW_WEB = booleanPreferencesKey("show_web")

/** Arquivo próprio, como o dos cards e o das notificações. */
private val Context.searchDataStore: DataStore<Preferences> by preferencesDataStore(name = "cascata_search")

class SearchPrefs(private val context: Context) {

    val settings: Flow<SearchSettings> = context.searchDataStore.data
        .map { it.toSettings() }
        .distinctUntilChanged()

    /** Lê, transforma e grava numa transação só: dois toques seguidos não se perdem. */
    suspend fun update(transform: (SearchSettings) -> SearchSettings) {
        context.searchDataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    /** Grava tudo de uma vez, sem olhar o que havia — é o que a restauração precisa. */
    suspend fun replace(settings: SearchSettings) {
        context.searchDataStore.edit { prefs -> prefs.write(settings) }
    }

    /** Limpa as chaves: a leitura volta a devolver os defaults. */
    suspend fun reset() {
        context.searchDataStore.edit { it.clear() }
    }
}

private fun Preferences.toSettings(): SearchSettings = SearchSettings(
    engine = enumOr(this[ENGINE], SearchSettings.DEFAULT.engine),
    showShortcuts = this[SHOW_SHORTCUTS] ?: SearchSettings.DEFAULT.showShortcuts,
    showCalculator = this[SHOW_CALCULATOR] ?: SearchSettings.DEFAULT.showCalculator,
    showContacts = this[SHOW_CONTACTS] ?: SearchSettings.DEFAULT.showContacts,
    showSettings = this[SHOW_SETTINGS] ?: SearchSettings.DEFAULT.showSettings,
    showWeb = this[SHOW_WEB] ?: SearchSettings.DEFAULT.showWeb,
)

private fun MutablePreferences.write(settings: SearchSettings) {
    this[ENGINE] = settings.engine.name
    this[SHOW_SHORTCUTS] = settings.showShortcuts
    this[SHOW_CALCULATOR] = settings.showCalculator
    this[SHOW_CONTACTS] = settings.showContacts
    this[SHOW_SETTINGS] = settings.showSettings
    this[SHOW_WEB] = settings.showWeb
}

/** Enum gravado pelo `name`; valor desconhecido cai no default. */
private inline fun <reified T : Enum<T>> enumOr(raw: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: default
