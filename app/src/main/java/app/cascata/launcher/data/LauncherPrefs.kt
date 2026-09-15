package app.cascata.launcher.data

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.cascata.launcher.crash.catchEmitting
import app.cascata.launcher.crash.degraded
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Tag dos avisos deste arquivo. */
private const val TAG = "CascataPrefs"

/** Ordem dos favoritos, uma chave por linha. Ver [encodeKeys]. */
private val FAVORITES_ORDER = stringPreferencesKey("favorites_order")

/** Apps escondidos da lista. Sem ordem: é só pertinência. */
private val HIDDEN = stringSetPreferencesKey("hidden")

/** Até a v0.1 os favoritos eram um conjunto sem ordem, sob este nome. */
private val LEGACY_FAVORITES = stringSetPreferencesKey("favorites")

/**
 * Converte o conjunto antigo para a lista ordenada e apaga a chave velha. Roda
 * uma vez: depois da remoção, [DataMigration.shouldMigrate] nunca mais dá true.
 */
private val favoritesMigration = object : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData.contains(LEGACY_FAVORITES)

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        val existing = decodeKeys(prefs[FAVORITES_ORDER])
        val legacy = prefs[LEGACY_FAVORITES].orEmpty().filterNot { it in existing }
        prefs[FAVORITES_ORDER] = encodeKeys(existing + legacy)
        prefs.remove(LEGACY_FAVORITES)
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cascata",
    produceMigrations = { listOf(favoritesMigration) },
)

/**
 * Tudo o que o launcher persiste: ordem dos favoritos, apps escondidos e
 * apelidos. Nada de histórico de uso.
 */
class LauncherPrefs(private val context: Context) {

    // Um DataStore pode lançar ao ler: arquivo corrompido por um desligamento no
    // meio da escrita (IOException) ou uma chave gravada com outro tipo por uma
    // versão anterior (ClassCastException). Quem coleta é a composição da home —
    // sem isto, uma preferência ilegível derruba a tela inicial; com isto, ela
    // volta ao padrão. O `catch` depois de cada `map` cobre a leitura da chave,
    // que é onde o tipo errado aparece.
    private val data: Flow<Preferences> = context.dataStore.data
        .catch { error ->
            degraded(TAG, "preferências ilegíveis", error)
            emit(emptyPreferences())
        }

    /** Chaves de [AppEntry] na ordem escolhida pelo usuário. */
    val favorites: Flow<List<String>> = data
        .map { decodeKeys(it[FAVORITES_ORDER]) }
        .distinctUntilChanged()
        .catchEmitting(TAG, "favoritos ilegíveis", emptyList())

    val hidden: Flow<Set<String>> = data
        .map { it[HIDDEN] ?: emptySet() }
        .distinctUntilChanged()
        .catchEmitting(TAG, "apps escondidos ilegíveis", emptySet())

    /**
     * Chave do app -> apelido. Cada apelido é uma preferência própria (`alias:<chave>`),
     * então renomear um app não reescreve o mapa inteiro.
     */
    val aliases: Flow<Map<String, String>> = data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            val entryKey = aliasEntryKey(key.name) ?: return@mapNotNull null
            val alias = value as? String ?: return@mapNotNull null
            entryKey to alias
        }.toMap()
    }.distinctUntilChanged()
        .catchEmitting(TAG, "apelidos ilegíveis", emptyMap())

    /** Fixa no fim da lista, ou desafixa mantendo a ordem do resto. */
    suspend fun toggleFavorite(key: String) {
        context.dataStore.edit { prefs ->
            val current = decodeKeys(prefs[FAVORITES_ORDER])
            prefs[FAVORITES_ORDER] =
                encodeKeys(if (key in current) current - key else current + key)
        }
    }

    suspend fun setFavoritesOrder(keys: List<String>) {
        context.dataStore.edit { prefs -> prefs[FAVORITES_ORDER] = encodeKeys(keys) }
    }

    suspend fun setHidden(key: String, hidden: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[HIDDEN] ?: emptySet()
            prefs[HIDDEN] = if (hidden) current + key else current - key
        }
    }

    /** Apelido nulo ou em branco remove a chave — o app volta ao rótulo do sistema. */
    suspend fun setAlias(key: String, alias: String?) {
        val prefKey = stringPreferencesKey(aliasPrefName(key))
        val trimmed = alias?.trim()
        context.dataStore.edit { prefs ->
            if (trimmed.isNullOrEmpty()) prefs.remove(prefKey) else prefs[prefKey] = trimmed
        }
    }

    /**
     * Substitui os três conjuntos de uma vez, para restaurar um backup. Os
     * apelidos antigos saem antes: cada um é uma preferência própria, então
     * gravar por cima deixaria vivos os que o arquivo não tem.
     */
    suspend fun restore(favorites: List<String>, hidden: Set<String>, aliases: Map<String, String>) {
        context.dataStore.edit { prefs ->
            prefs[FAVORITES_ORDER] = encodeKeys(favorites)
            prefs[HIDDEN] = hidden
            prefs.asMap().keys.filter { aliasEntryKey(it.name) != null }.forEach { prefs.remove(it) }
            aliases.forEach { (key, alias) ->
                val trimmed = alias.trim()
                if (trimmed.isNotEmpty()) prefs[stringPreferencesKey(aliasPrefName(key))] = trimmed
            }
        }
    }

    /** Limpa as chaves: o launcher volta ao estado de app recém-instalado. */
    suspend fun reset() {
        context.dataStore.edit { it.clear() }
    }
}
