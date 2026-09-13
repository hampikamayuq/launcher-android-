package app.cascata.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.cascata.launcher.data.AppEntry
import app.cascata.launcher.data.AppRepository
import app.cascata.launcher.data.FavoritesStore
import app.cascata.launcher.data.normalizeLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Uma linha da lista: cabeçalho de seção ou app. */
sealed interface Row {
    data class Header(val letter: Char) : Row
    data class App(val entry: AppEntry, val favorite: Boolean) : Row
}

data class HomeUiState(
    val rows: List<Row> = emptyList(),
    val favorites: List<AppEntry> = emptyList(),
    /** Letra -> índice da linha onde a seção começa. Alimenta o índice alfabético. */
    val sectionIndex: Map<Char, Int> = emptyMap(),
    val query: String = "",
    val loading: Boolean = true,
)

class HomeViewModel(
    private val repository: AppRepository,
    private val favoritesStore: FavoritesStore,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<HomeUiState> =
        combine(repository.apps, favoritesStore.favorites, query) { apps, favoriteKeys, q ->
            buildState(apps, favoriteKeys, q)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onToggleFavorite(entry: AppEntry) {
        viewModelScope.launch { favoritesStore.toggle(entry.key) }
    }

    private fun buildState(apps: List<AppEntry>, favoriteKeys: Set<String>, q: String): HomeUiState {
        val favorites = apps.filter { it.key in favoriteKeys }
        val needle = normalizeLabel(q)

        if (needle.isNotEmpty()) {
            val matches = apps.filter { it.matches(needle) }
                .sortedWith(compareBy({ it.rank(needle) }, { it.normalizedLabel }))
            return HomeUiState(
                rows = matches.map { Row.App(it, it.key in favoriteKeys) },
                favorites = favorites,
                query = q,
                loading = false,
            )
        }

        val rows = ArrayList<Row>(apps.size + 32)
        val index = LinkedHashMap<Char, Int>()
        var last: Char? = null
        for (app in apps) {
            if (app.section != last) {
                index[app.section] = rows.size
                rows += Row.Header(app.section)
                last = app.section
            }
            rows += Row.App(app, app.key in favoriteKeys)
        }
        return HomeUiState(
            rows = rows,
            favorites = favorites,
            sectionIndex = index,
            query = q,
            loading = apps.isEmpty(),
        )
    }

    /** Casa no começo do rótulo ou de qualquer palavra dele — nunca no meio de uma palavra. */
    private fun AppEntry.matches(needle: String): Boolean =
        normalizedLabel.startsWith(needle) ||
            normalizedLabel.split(' ').any { it.startsWith(needle) }

    /** Prefixo do rótulo inteiro vem antes de prefixo de palavra interna. */
    private fun AppEntry.rank(needle: String): Int =
        if (normalizedLabel.startsWith(needle)) 0 else 1

    class Factory(
        private val repository: AppRepository,
        private val favoritesStore: FavoritesStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(repository, favoritesStore) as T
    }
}
