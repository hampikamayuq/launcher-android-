package app.cascata.launcher.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import app.cascata.launcher.data.AppEntry
import app.cascata.launcher.data.AppRepository

private val HIDDEN_ICON = 36.dp

/** Onde os apps escondidos voltam a existir — é a única porta de saída de "esconder". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenAppsSheet(
    hidden: List<AppEntry>,
    repository: AppRepository,
    onUnhide: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.hidden_apps),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .semantics { heading() }
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            items(count = hidden.size, key = { hidden[it].key }) { index ->
                val entry = hidden[index]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        // Ícone e nome são uma parada só; o botão tem a sua.
                        .semantics(mergeDescendants = true) { }
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                ) {
                    AppIcon(entry = entry, repository = repository, size = HIDDEN_ICON)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onUnhide(entry) }) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            // "Mostrar na lista" repetido não diz de qual app é.
                            contentDescription = stringResource(R.string.unhide_app, entry.label),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
