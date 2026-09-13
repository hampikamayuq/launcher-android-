package app.cascata.launcher.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import app.cascata.launcher.data.search.ContactsSource
import app.cascata.launcher.data.search.SearchEngine
import app.cascata.launcher.data.search.SearchPrefs
import app.cascata.launcher.data.search.SearchSettings
import kotlinx.coroutines.launch

/**
 * O que a busca mostra além dos apps. Mesma regra dos cards do topo: contatos é
 * o único item que custa uma permissão, e ela só é pedida no instante em que a
 * opção é ligada.
 */
@Composable
internal fun SearchSection(
    search: SearchSettings,
    prefs: SearchPrefs,
    contactsSource: ContactsSource,
) {
    val scope = rememberCoroutineScope()
    val write: ((SearchSettings) -> SearchSettings) -> Unit = { transform ->
        scope.launch { prefs.update(transform) }
    }

    var sheet by remember { mutableStateOf(false) }
    // Negada agora, nesta tela: o recado só faz sentido logo depois do diálogo.
    var contactsDenied by remember { mutableStateOf(false) }

    val contactsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        contactsDenied = !granted
        // Só grava se concedeu: opção ligada sem permissão seria uma seção vazia.
        if (granted) write { it.copy(showContacts = true) }
    }

    SettingsSection(stringResource(R.string.section_search)) {
        SettingRow(
            label = stringResource(R.string.search_engine),
            value = search.engine.label,
            onClick = { sheet = true },
        )

        SwitchRow(
            label = stringResource(R.string.search_show_shortcuts),
            checked = search.showShortcuts,
            onCheckedChange = { on -> write { it.copy(showShortcuts = on) } },
        )

        SwitchRow(
            label = stringResource(R.string.search_show_calculator),
            checked = search.showCalculator,
            onCheckedChange = { on -> write { it.copy(showCalculator = on) } },
        )

        SwitchRow(
            label = stringResource(R.string.search_section_contacts),
            checked = search.showContacts,
            supporting = if (contactsDenied) stringResource(R.string.search_contacts_denied) else null,
            action = if (contactsDenied) {
                { AppSettingsButton() }
            } else {
                null
            },
            onCheckedChange = { on ->
                when {
                    !on -> {
                        contactsDenied = false
                        write { it.copy(showContacts = false) }
                    }

                    contactsSource.hasPermission() -> {
                        contactsDenied = false
                        write { it.copy(showContacts = true) }
                    }

                    else -> contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                }
            },
        )

        SwitchRow(
            label = stringResource(R.string.search_section_settings),
            checked = search.showSettings,
            onCheckedChange = { on -> write { it.copy(showSettings = on) } },
        )

        SwitchRow(
            label = stringResource(R.string.search_show_web),
            checked = search.showWeb,
            onCheckedChange = { on -> write { it.copy(showWeb = on) } },
        )

        Text(
            text = stringResource(R.string.search_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SIDE_PADDING, vertical = 8.dp),
        )
    }

    if (sheet) {
        SearchEngineSheet(
            selected = search.engine,
            onSelect = { engine ->
                write { it.copy(engine = engine) }
                sheet = false
            },
            onDismiss = { sheet = false },
        )
    }
}

/** Os nomes são marcas: vêm do próprio enum, não de strings.xml. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchEngineSheet(
    selected: SearchEngine,
    onSelect: (SearchEngine) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.search_engine),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = SIDE_PADDING, vertical = 8.dp),
        )

        SearchEngine.entries.forEach { engine ->
            ChoiceRow(
                label = engine.label,
                selected = engine == selected,
                onClick = { onSelect(engine) },
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}
