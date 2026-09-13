package app.cascata.launcher.ui

import android.content.pm.ShortcutInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cascata.launcher.HomeViewModel
import app.cascata.launcher.R
import app.cascata.launcher.data.AppEntry
import app.cascata.launcher.data.AppRepository

private val SHEET_ICON = 48.dp
private val SHORTCUT_ICON = 32.dp

/**
 * Menu do toque longo. Vira folha em vez de menu suspenso porque o conteúdo
 * cresceu: além das ações, lista os atalhos que o próprio app publica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContextSheet(
    entry: AppEntry,
    favorite: Boolean,
    hasShortcutHost: Boolean,
    repository: AppRepository,
    viewModel: HomeViewModel,
    onDismiss: () -> Unit,
) {
    var renaming by remember(entry.key) { mutableStateOf(false) }

    if (renaming) {
        RenameDialog(
            entry = entry,
            onConfirm = { newLabel ->
                viewModel.onRename(entry, newLabel)
                onDismiss()
            },
            onCancel = { renaming = false },
        )
        return
    }

    // Sem ser o launcher padrão a consulta volta vazia; nem chamamos.
    var shortcuts by remember(entry.key) { mutableStateOf(emptyList<ShortcutInfo>()) }
    LaunchedEffect(entry.key, hasShortcutHost) {
        shortcuts = if (hasShortcutHost) viewModel.shortcutsFor(entry) else emptyList()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            SheetHeader(entry = entry, repository = repository)

            if (shortcuts.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = stringResource(R.string.shortcuts),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
                shortcuts.forEach { shortcut ->
                    ShortcutRow(
                        shortcut = shortcut,
                        repository = repository,
                        onClick = {
                            repository.startShortcut(shortcut)
                            onDismiss()
                        },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SheetAction(
                icon = if (favorite) Icons.Filled.Star else Icons.Outlined.Star,
                label = stringResource(if (favorite) R.string.unpin else R.string.pin),
                onClick = {
                    viewModel.onToggleFavorite(entry)
                    onDismiss()
                },
            )
            SheetAction(
                icon = Icons.Outlined.Edit,
                label = stringResource(R.string.rename),
                onClick = { renaming = true },
            )
            SheetAction(
                icon = Icons.Outlined.Close,
                label = stringResource(R.string.hide),
                onClick = {
                    viewModel.onHide(entry)
                    onDismiss()
                },
            )
            SheetAction(
                icon = Icons.Outlined.Info,
                label = stringResource(R.string.app_info),
                onClick = {
                    repository.openAppInfo(entry)
                    onDismiss()
                },
            )
            SheetAction(
                icon = Icons.Outlined.Delete,
                label = stringResource(R.string.uninstall),
                onClick = {
                    repository.uninstall(entry)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun SheetHeader(entry: AppEntry, repository: AppRepository) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
    ) {
        AppIcon(entry = entry, repository = repository, size = SHEET_ICON)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // O nome do sistema só aparece quando foi trocado por um apelido.
            if (entry.label != entry.originalLabel) {
                Text(
                    text = entry.originalLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ShortcutRow(
    shortcut: ShortcutInfo,
    repository: AppRepository,
    onClick: () -> Unit,
) {
    val label = (shortcut.shortLabel ?: shortcut.longLabel)?.toString().orEmpty()
    if (label.isEmpty()) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        ShortcutIcon(shortcut = shortcut, repository = repository, size = SHORTCUT_ICON)
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Renomear: campo já preenchido, e um atalho para voltar ao nome do sistema. */
@Composable
private fun RenameDialog(
    entry: AppEntry,
    onConfirm: (String?) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember(entry.key) { mutableStateOf(entry.label) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.rename)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(entry.originalLabel) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (entry.label != entry.originalLabel) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { onConfirm(null) }) {
                        Text(stringResource(R.string.rename_reset))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
