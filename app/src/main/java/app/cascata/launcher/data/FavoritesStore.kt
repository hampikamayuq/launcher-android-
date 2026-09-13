package app.cascata.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cascata")

/** Favoritos fixados, guardados como chaves de [AppEntry]. Nada mais é persistido. */
class FavoritesStore(private val context: Context) {

    private val key = stringSetPreferencesKey("favorites")

    val favorites: Flow<Set<String>> = context.dataStore.data.map { it[key] ?: emptySet() }

    suspend fun toggle(entryKey: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[key] ?: emptySet()
            prefs[key] = if (entryKey in current) current - entryKey else current + entryKey
        }
    }
}
