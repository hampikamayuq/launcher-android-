package app.cascata.launcher.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import app.cascata.launcher.data.AppRepository
import app.cascata.launcher.data.widgets.WidgetHostManager
import app.cascata.launcher.data.widgets.WidgetLayout
import app.cascata.launcher.data.widgets.WidgetPrefs
import app.cascata.launcher.data.widgets.removeWidget
import app.cascata.launcher.ui.widgets.widgetLabel
import app.cascata.launcher.widgets.WidgetPickerActivity
import kotlinx.coroutines.launch

/**
 * Widgets. A edição fina (altura, pilha, ordem) vive na própria home, no botão
 * do canto de cada slot: aqui ficam só as duas coisas que não cabem lá — pôr o
 * primeiro widget e tirar um slot inteiro de uma vez.
 */
@Composable
internal fun WidgetsSection(
    layout: WidgetLayout,
    host: WidgetHostManager,
    prefs: WidgetPrefs,
    repository: AppRepository,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unavailable = stringResource(R.string.widget_unavailable)

    // Sem ser a tela inicial padrão, o sistema não confia o bind ao app sem
    // perguntar. Lido uma vez: trocar a home padrão reinicia esta tela.
    val isDefaultLauncher = remember { repository.isDefaultLauncher() }

    // Um rótulo por slot, com a pilha inteira: consultar o PackageManager a cada
    // recomposição da tela de configurações seria trabalho repetido à toa.
    val labels = remember(layout, unavailable) {
        layout.slots.associate { slot ->
            slot.id to slot.widgets.joinToString(", ") {
                widgetLabel(context, host, it.appWidgetId, unavailable)
            }
        }
    }

    SettingsSection(stringResource(R.string.section_widgets)) {
        SettingRow(
            label = stringResource(R.string.widget_add),
            onClick = {
                runCatching {
                    context.startActivity(Intent(context, WidgetPickerActivity::class.java))
                }
            },
        )

        if (!isDefaultLauncher) {
            Text(
                text = stringResource(R.string.widgets_not_default),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SIDE_PADDING, vertical = 4.dp),
            )
        }

        if (layout.slots.isEmpty()) {
            Text(
                text = stringResource(R.string.widgets_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SIDE_PADDING, vertical = 8.dp),
            )
        }

        layout.slots.forEach { slot ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = SIDE_PADDING, end = 8.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = labels[slot.id].orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.widget_cells,
                            slot.heightCells,
                            slot.heightCells,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        // O slot sai inteiro: cada id volta para o host antes de
                        // sumir do layout, senão ele ficaria alocado para sempre.
                        val ids = slot.widgets.map { it.appWidgetId }
                        ids.forEach(host::deleteId)
                        scope.launch {
                            prefs.update { current ->
                                ids.fold(current) { acc, id -> acc.removeWidget(id) }
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.widget_remove))
                }
            }
        }

        Text(
            text = stringResource(R.string.widgets_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SIDE_PADDING, vertical = 8.dp),
        )
    }
}
