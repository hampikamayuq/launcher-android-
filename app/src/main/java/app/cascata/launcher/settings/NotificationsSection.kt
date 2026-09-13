package app.cascata.launcher.settings

import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import app.cascata.launcher.data.AppEntry
import app.cascata.launcher.data.AppRepository
import app.cascata.launcher.data.notifications.BadgeStyle
import app.cascata.launcher.data.notifications.NotificationAccess
import app.cascata.launcher.data.notifications.NotificationPrefs
import app.cascata.launcher.data.notifications.NotificationSettings
import app.cascata.launcher.ui.AppIcon
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val MUTED_ICON = 36.dp

/**
 * Notificações na lista. O interruptor grava a intenção; o acesso de verdade é
 * concedido numa tela do sistema, e por isso [hasAccess] é relido a cada
 * `onResume` da tela (ver `SettingsActivity`) em vez de virar preferência.
 */
@Composable
internal fun NotificationsSection(
    settings: NotificationSettings,
    prefs: NotificationPrefs,
    access: NotificationAccess,
    hasAccess: Boolean,
    repository: AppRepository,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val write: ((NotificationSettings) -> NotificationSettings) -> Unit = { transform ->
        scope.launch { prefs.update(transform) }
    }
    val openAccess = {
        runCatching {
            context.startActivity(
                access.listenerSettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        Unit
    }

    var showMuted by remember { mutableStateOf(false) }

    SettingsSection(stringResource(R.string.section_notifications)) {
        SwitchRow(
            label = stringResource(R.string.notifications_enabled),
            checked = settings.enabled,
            supporting = when {
                settings.enabled && hasAccess -> stringResource(R.string.notifications_access_granted)
                settings.enabled -> stringResource(R.string.notifications_access_missing)
                // Revogar o acesso é da alçada do sistema: desligar aqui só faz
                // a lista parar de mostrar o que o serviço recebe.
                hasAccess -> stringResource(R.string.notifications_access_revoke)
                else -> null
            },
            action = if (settings.enabled && !hasAccess) {
                { TextButton(onClick = openAccess) { Text(stringResource(R.string.notifications_access_open)) } }
            } else {
                null
            },
            onCheckedChange = { on ->
                write { it.copy(enabled = on) }
                // Ligar sem acesso não mostraria nada: a tela do sistema é o
                // único caminho, e ela abre junto com o interruptor.
                if (on && !access.hasListenerAccess()) openAccess()
            },
        )

        if (settings.enabled) {
            SegmentedChoice(
                label = stringResource(R.string.notifications_badge_style),
                options = listOf(
                    BadgeStyle.COUNT to stringResource(R.string.notifications_badge_count),
                    BadgeStyle.DOT to stringResource(R.string.notifications_badge_dot),
                ),
                selected = settings.badgeStyle,
                onSelect = { style -> write { it.copy(badgeStyle = style) } },
            )

            SwitchRow(
                label = stringResource(R.string.notifications_expand_inline),
                checked = settings.expandInline,
                onCheckedChange = { on -> write { it.copy(expandInline = on) } },
            )

            SwitchRow(
                label = stringResource(R.string.notifications_show_media),
                checked = settings.showMedia,
                onCheckedChange = { on -> write { it.copy(showMedia = on) } },
            )

            SettingRow(
                label = stringResource(R.string.notifications_muted),
                value = if (settings.mutedPackages.isEmpty()) {
                    stringResource(R.string.notifications_muted_none)
                } else {
                    pluralStringResource(
                        R.plurals.notifications_muted_count,
                        settings.mutedPackages.size,
                        settings.mutedPackages.size,
                    )
                },
                onClick = { showMuted = true },
            )
        }

        Text(
            text = stringResource(R.string.notifications_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SIDE_PADDING, vertical = 8.dp),
        )
    }

    if (showMuted) {
        MutedAppsSheet(
            muted = settings.mutedPackages,
            repository = repository,
            onToggle = { packageName, isMuted ->
                scope.launch { prefs.setMuted(packageName, isMuted) }
            },
            onDismiss = { showMuted = false },
        )
    }
}

/**
 * Um interruptor por app. A lista é a mesma da home, um app por pacote: quem
 * silencia o "Gmail" não quer escolher entre as atividades dele.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MutedAppsSheet(
    muted: Set<String>,
    repository: AppRepository,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }

    // O Flow é um StateFlow que começa vazio e só carrega quando alguém assina:
    // esperamos a primeira lista de verdade, não a foto inicial.
    LaunchedEffect(repository) {
        apps = repository.apps.first { it.isNotEmpty() }
            .distinctBy { it.component.packageName }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.notifications_muted),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = SIDE_PADDING, vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            items(count = apps.size, key = { apps[it].key }) { index ->
                val entry = apps[index]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SIDE_PADDING, vertical = 4.dp),
                ) {
                    AppIcon(entry = entry, repository = repository, size = MUTED_ICON)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = entry.component.packageName in muted,
                        onCheckedChange = { on -> onToggle(entry.component.packageName, on) },
                        modifier = Modifier.semantics { contentDescription = entry.label },
                    )
                }
            }
        }
    }
}
