package app.cascata.launcher.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import app.cascata.launcher.data.iconpack.IconPackInfo
import app.cascata.launcher.data.iconpack.IconPackRepository
import app.cascata.launcher.data.theme.ThemeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun IconPackSection(
    settings: ThemeSettings,
    iconPacks: IconPackRepository,
    update: UpdateSettings,
) {
    var sheet by remember { mutableStateOf(false) }
    var packs by remember { mutableStateOf(emptyList<IconPackInfo>()) }

    // Consultar o PackageManager por três ações não é trabalho de main thread.
    LaunchedEffect(Unit) {
        packs = withContext(Dispatchers.IO) { iconPacks.installedPacks() }
    }

    val systemLabel = stringResource(R.string.icon_pack_system)
    // O rótulo do pacote enquanto a lista não chegou (ou se ele foi desinstalado)
    // é o próprio nome do pacote — melhor que uma linha vazia.
    val current = settings.iconPack?.let { chosen ->
        packs.firstOrNull { it.packageName == chosen }?.label ?: chosen
    } ?: systemLabel

    SettingsSection(stringResource(R.string.section_icons)) {
        SettingRow(
            label = stringResource(R.string.icon_pack),
            value = current,
            onClick = { sheet = true },
        )
    }

    if (sheet) {
        IconPackSheet(
            selected = settings.iconPack,
            packs = packs,
            onSelect = { pack ->
                update { it.copy(iconPack = pack) }
                iconPacks.invalidate()
                sheet = false
            },
            onDismiss = { sheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconPackSheet(
    selected: String?,
    packs: List<IconPackInfo>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.icon_pack),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = SIDE_PADDING, vertical = 8.dp),
        )

        ChoiceRow(
            label = stringResource(R.string.icon_pack_system),
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        packs.forEach { pack ->
            ChoiceRow(
                label = pack.label,
                selected = selected == pack.packageName,
                onClick = { onSelect(pack.packageName) },
            )
        }

        if (packs.isEmpty()) {
            Text(
                text = stringResource(R.string.icon_pack_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SIDE_PADDING, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}
