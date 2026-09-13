package app.cascata.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.cascata.launcher.HomeUiState
import app.cascata.launcher.R
import app.cascata.launcher.Row as UiRow
import app.cascata.launcher.data.AppEntry
import app.cascata.launcher.data.AppRepository
import kotlinx.coroutines.launch

private val ICON_SIZE = 40.dp
private val INDEX_WIDTH = 28.dp

@Composable
fun HomeScreen(
    state: HomeUiState,
    repository: AppRepository,
    onQueryChange: (String) -> Unit,
    onToggleFavorite: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            // Fundo translúcido: o papel de parede continua visível por baixo.
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .safeDrawingPadding()
            .padding(horizontal = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ClockHeader()

            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )

            if (state.favorites.isNotEmpty() && state.query.isEmpty()) {
                FavoritesRow(favorites = state.favorites, repository = repository)
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = INDEX_WIDTH),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(
                        count = state.rows.size,
                        key = { index ->
                            when (val row = state.rows[index]) {
                                is UiRow.Header -> "header-${row.letter}"
                                is UiRow.App -> row.entry.key
                            }
                        },
                        contentType = { index ->
                            if (state.rows[index] is UiRow.Header) "header" else "app"
                        },
                    ) { index ->
                        when (val row = state.rows[index]) {
                            is UiRow.Header -> SectionHeader(row.letter)
                            is UiRow.App -> AppRow(
                                entry = row.entry,
                                favorite = row.favorite,
                                repository = repository,
                                onToggleFavorite = onToggleFavorite,
                            )
                        }
                    }
                }

                if (state.query.isEmpty()) {
                    AlphabetIndex(
                        letters = state.sectionIndex.keys.toList(),
                        onLetterFocused = { letter ->
                            state.sectionIndex[letter]?.let { index ->
                                scope.launch { listState.scrollToItem(index) }
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }

                if (state.rows.isEmpty() && !state.loading && state.query.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.empty_search, state.query),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(letter: Char) {
    Text(
        text = letter.toString(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun AppRow(
    entry: AppEntry,
    favorite: Boolean,
    repository: AppRepository,
    onToggleFavorite: (AppEntry) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { repository.launch(entry) },
                onLongClick = { menuOpen = true },
            )
            .padding(vertical = 6.dp),
    ) {
        AppIcon(entry = entry, repository = repository, size = ICON_SIZE)
        Spacer(Modifier.width(14.dp))
        Text(
            text = entry.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (favorite) {
            Spacer(Modifier.width(6.dp))
            Text(text = "•", color = MaterialTheme.colorScheme.primary)
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (favorite) R.string.unpin else R.string.pin)) },
                onClick = {
                    onToggleFavorite(entry)
                    menuOpen = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_info)) },
                onClick = {
                    repository.openAppInfo(entry)
                    menuOpen = false
                },
            )
        }
    }
}

@Composable
private fun FavoritesRow(favorites: List<AppEntry>, repository: AppRepository) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = stringResource(R.string.favorites),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            favorites.take(5).forEach { entry ->
                Box(modifier = Modifier.combinedClickable { repository.launch(entry) }) {
                    AppIcon(entry = entry, repository = repository, size = ICON_SIZE)
                }
            }
        }
    }
}
