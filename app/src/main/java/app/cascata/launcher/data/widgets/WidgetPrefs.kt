package app.cascata.launcher.data.widgets

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val LAYOUT = stringPreferencesKey("layout")

/**
 * Arquivo próprio, como o do tema e o dos cards: apagar os widgets não pode
 * encostar em favoritos nem na aparência.
 */
private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "cascata_widgets")

/**
 * Campo novo escrito por uma versão futura não invalida o layout inteiro;
 * `encodeDefaults` porque o JSON é lido de volta sem o `data class` em mãos.
 */
private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * O layout dos widgets, persistido. Aqui é uma chave só com o JSON inteiro (e não
 * uma chave por campo, como nas outras prefs): a estrutura é aninhada e muda em
 * bloco — slot, pilha e ordem só fazem sentido juntos.
 */
class WidgetPrefs(private val context: Context) {

    val layout: Flow<WidgetLayout> = context.widgetDataStore.data
        .map { prefs -> decode(prefs[LAYOUT]) }
        .distinctUntilChanged()

    /** Lê, transforma e grava numa transação só: dois toques seguidos não se perdem. */
    suspend fun update(transform: (WidgetLayout) -> WidgetLayout) {
        context.widgetDataStore.edit { prefs ->
            prefs[LAYOUT] = json.encodeToString(transform(decode(prefs[LAYOUT])))
        }
    }

    suspend fun replace(layout: WidgetLayout) {
        context.widgetDataStore.edit { prefs -> prefs[LAYOUT] = json.encodeToString(layout) }
    }
}

/** Layout corrompido não pode derrubar a home: volta vazio e o usuário recoloca. */
private fun decode(raw: String?): WidgetLayout {
    if (raw.isNullOrBlank()) return WidgetLayout.EMPTY
    return runCatching { json.decodeFromString<WidgetLayout>(raw) }.getOrDefault(WidgetLayout.EMPTY)
}
