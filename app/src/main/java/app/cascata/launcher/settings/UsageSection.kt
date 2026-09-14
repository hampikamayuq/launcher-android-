package app.cascata.launcher.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import app.cascata.launcher.data.AppEntry
import app.cascata.launcher.data.AppRepository
import app.cascata.launcher.data.usage.UsageAccess
import app.cascata.launcher.data.usage.UsagePrefs
import app.cascata.launcher.data.usage.UsageSettings
import app.cascata.launcher.ui.AppIcon
import app.cascata.launcher.ui.usage.LimitDialog
import app.cascata.launcher.ui.usage.limitText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val LIMIT_ICON = 36.dp

/**
 * Uso do aparelho. Como o das notificações, o interruptor grava só a intenção:
 * `PACKAGE_USAGE_STATS` é acesso especial, concedido numa tela do sistema, e por
 * isso [hasAccess] é relido a cada `onResume` (ver `SettingsActivity`).
 *
 * As sub-opções ficam visíveis e desligadas enquanto o recurso está desligado —
 * quem chega aqui vê o que o recurso faz antes de ligá-lo.
 */
@Composable
internal fun UsageSection(
    settings: UsageSettings,
    prefs: UsagePrefs,
    access: UsageAccess,
    hasAccess: Boolean,
    repository: AppRepository,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val write: ((UsageSettings) -> UsageSettings) -> Unit = { transform ->
        scope.launch { prefs.update(transform) }
    }
    val openAccess = {
        runCatching {
            context.startActivity(access.settingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        Unit
    }

    var showLimits by remember { mutableStateOf(false) }
    val enabled = settings.enabled

    SettingsSection(stringResource(R.string.section_usage)) {
        SwitchRow(
            // O rótulo é o título da seção: a seção é este interruptor.
            label = stringResource(R.string.section_usage),
            checked = enabled,
            supporting = when {
                enabled && hasAccess -> stringResource(R.string.usage_access_granted)
                enabled -> stringResource(R.string.usage_access_missing)
                else -> null
            },
            action = if (enabled && !hasAccess) {
                { TextButton(onClick = openAccess) { Text(stringResource(R.string.usage_access_open)) } }
            } else {
                null
            },
            onCheckedChange = { on ->
                write { it.copy(enabled = on) }
                // Ligar sem o acesso não mostraria tempo nenhum: a tela do
                // sistema é o único caminho, e abre junto com o interruptor.
                if (on && !access.hasAccess()) openAccess()
            },
        )

        SwitchRow(
            label = stringResource(R.string.usage_show_card),
            checked = settings.showCard,
            enabled = enabled,
            onCheckedChange = { on -> write { it.copy(showCard = on) } },
        )

        PauseSlider(
            label = stringResource(R.string.usage_pause),
            seconds = settings.pauseSeconds,
            enabled = enabled,
            onChange = { seconds -> write { it.copy(pauseSeconds = seconds) } },
        )

        SettingRow(
            label = stringResource(R.string.usage_limits),
            value = if (settings.limitsMinutes.isEmpty()) {
                stringResource(R.string.usage_limits_none)
            } else {
                pluralStringResource(
                    R.plurals.usage_limits_count,
                    settings.limitsMinutes.size,
                    settings.limitsMinutes.size,
                )
            },
            enabled = enabled,
            onClick = { showLimits = true },
        )

        Text(
            text = stringResource(R.string.usage_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SIDE_PADDING, vertical = 8.dp),
        )
    }

    if (showLimits) {
        LimitsSheet(
            limits = settings.limitsMinutes,
            repository = repository,
            onSetLimit = { packageName, minutes ->
                scope.launch { prefs.setLimit(packageName, minutes) }
            },
            onDismiss = { showLimits = false },
        )
    }
}

/**
 * Os segundos da pausa, em passos inteiros. O polegar anda pelo estado local
 * para acompanhar o dedo; o valor gravado volta pelo DataStore um instante
 * depois — o mesmo arranjo da [PercentSlider].
 */
@Composable
private fun PauseSlider(
    label: String,
    seconds: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    var live by remember { mutableFloatStateOf(seconds.toFloat()) }
    LaunchedEffect(seconds) { live = seconds.toFloat() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SIDE_PADDING, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.usage_pause_seconds, live.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Slider(
            value = live,
            onValueChange = { novo ->
                live = novo
                // Uma gravação por passo, não por quadro de arraste.
                val step = novo.roundToInt()
                if (step != seconds) onChange(step)
            },
            valueRange = UsageSettings.MIN_PAUSE_SECONDS.toFloat()..
                UsageSettings.MAX_PAUSE_SECONDS.toFloat(),
            // Os valores inteiros entre as pontas, que o Slider não conta sozinho.
            steps = UsageSettings.MAX_PAUSE_SECONDS - UsageSettings.MIN_PAUSE_SECONDS - 1,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

/**
 * Um limite por app. A lista é a mesma da home, um app por pacote: o limite é
 * do app, não de cada atividade dele.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LimitsSheet(
    limits: Map<String, Int>,
    repository: AppRepository,
    onSetLimit: (String, Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    var editing by remember { mutableStateOf<AppEntry?>(null) }

    // O Flow começa vazio e só carrega quando alguém assina: esperamos a
    // primeira lista de verdade, não a foto inicial.
    LaunchedEffect(repository) {
        apps = repository.apps.first { it.isNotEmpty() }
            .distinctBy { it.component.packageName }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.usage_limits),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .semantics { heading() }
                .padding(horizontal = SIDE_PADDING, vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            items(count = apps.size, key = { apps[it].key }) { index ->
                val entry = apps[index]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { editing = entry }
                        .padding(horizontal = SIDE_PADDING, vertical = 6.dp),
                ) {
                    AppIcon(entry = entry, repository = repository, size = LIMIT_ICON)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = limitText(limits[entry.component.packageName]),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    editing?.let { entry ->
        LimitDialog(
            appLabel = entry.label,
            minutes = limits[entry.component.packageName],
            onConfirm = { minutes ->
                onSetLimit(entry.component.packageName, minutes)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}
