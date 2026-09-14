package app.cascata.launcher.settings

import android.content.Context
import android.os.Process
import android.os.UserManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import app.cascata.launcher.data.backup.BackupFile
import app.cascata.launcher.data.backup.BackupManager
import app.cascata.launcher.data.backup.BackupPayload
import app.cascata.launcher.data.backup.remapUserHashes
import app.cascata.launcher.data.widgets.WidgetHostManager
import app.cascata.launcher.data.widgets.WidgetPrefs
import app.cascata.launcher.data.widgets.allWidgetIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Teto de leitura na importação. Um backup com favoritos, apelidos e widgets não
 * passa de algumas dezenas de KB; 5 MB é folga suficiente para não recusar um
 * arquivo legítimo e pouco para não engolir o vídeo que o seletor deixou escolher.
 */
private const val MAX_BACKUP_BYTES = 5 * 1024 * 1024

/** O tipo do arquivo e o coringa, para provedores que não classificam nada. */
private val BACKUP_MIME_TYPES = arrayOf(BackupFile.MIME, "*/*")

/**
 * Backup do app inteiro — não só da aparência, que tem a sua própria linha na
 * seção "Tema". Exportar e importar passam pelo SAF: o Cascata não escolhe pasta
 * nem pede permissão de armazenamento, quem decide onde o arquivo mora é o
 * seletor do sistema.
 *
 * O I/O fica aqui, e não no [BackupManager]: ele conhece os DataStores, e a
 * `Uri` e o `AppWidgetHost` são da UI.
 */
@Composable
internal fun BackupSection(
    backupManager: BackupManager,
    widgetPrefs: WidgetPrefs,
    widgetHost: WidgetHostManager,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportDone = stringResource(R.string.backup_export_done)
    val exportFailed = stringResource(R.string.backup_export_failed)
    val importFailed = stringResource(R.string.backup_import_failed)
    val restored = stringResource(R.string.backup_restored)
    val erased = stringResource(R.string.backup_erased)

    // O arquivo já lido e validado, esperando o "Substituir" do diálogo.
    var pending by remember { mutableStateOf<BackupPayload?>(null) }
    var includeWidgets by remember { mutableStateOf(true) }
    var confirmErase by remember { mutableStateOf(false) }

    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFile.MIME)
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val written = withContext(Dispatchers.IO) {
                    runCatching {
                        // Ler os DataStores e serializar também é trabalho de IO.
                        val text = backupManager.encode(backupManager.collect())
                        val stream = context.contentResolver.openOutputStream(uri)
                            ?: error("sem stream de escrita")
                        stream.use { it.write(text.toByteArray()) }
                    }
                }
                onMessage(if (written.isSuccess) exportDone else exportFailed)
            }
        }
    }

    val import = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    runCatching {
                        val stream = context.contentResolver.openInputStream(uri)
                            ?: error("sem stream de leitura")
                        stream.use { it.readLimited(MAX_BACKUP_BYTES) }
                    }.mapCatching { backupManager.decode(it).getOrThrow() }
                }
                decoded
                    .onSuccess {
                        includeWidgets = true
                        pending = it
                    }
                    // A falha já vem explicada ("não é um backup do Cascata",
                    // "backup de uma versão mais nova"): repeti-la é mais útil
                    // que um "não foi possível importar" genérico.
                    .onFailure { cause -> onMessage(cause.message ?: importFailed) }
            }
        }
    }

    SettingsSection(stringResource(R.string.section_backup)) {
        SettingRow(
            label = stringResource(R.string.backup_export),
            onClick = { export.launch("cascata.${BackupFile.EXTENSION}") },
        )
        SettingRow(
            label = stringResource(R.string.backup_import),
            onClick = { import.launch(BACKUP_MIME_TYPES) },
        )
        SettingRow(
            label = stringResource(R.string.backup_erase),
            onClick = { confirmErase = true },
        )
        Text(
            text = stringResource(R.string.backup_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SIDE_PADDING, vertical = 8.dp),
        )
    }

    pending?.let { payload ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.backup_replace_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.backup_replace_body))
                    Spacer(Modifier.height(16.dp))
                    IncludeWidgetsCheck(
                        checked = includeWidgets,
                        onCheckedChange = { includeWidgets = it },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val widgets = includeWidgets
                        pending = null
                        scope.launch {
                            restore(context, backupManager, widgetPrefs, widgetHost, payload, widgets)
                            onMessage(restored)
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_replace))
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text(stringResource(R.string.backup_erase)) },
            text = { Text(stringResource(R.string.backup_erase_body)) },
            confirmButton = {
                TextButton(
                    // Vermelho: é a única ação desta tela que não tem volta.
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    onClick = {
                        confirmErase = false
                        scope.launch {
                            // Os ids que sobraram do layout apagado voltam para
                            // o host, senão ficam alocados para sempre.
                            backupManager.resetAll().forEach(widgetHost::deleteId)
                            onMessage(erased)
                        }
                    },
                ) {
                    Text(stringResource(R.string.backup_erase))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmErase = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** O "Incluir widgets" do diálogo, com a ressalva embaixo. */
@Composable
private fun IncludeWidgetsCheck(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // A linha inteira marca a caixa, como as linhas de interruptor.
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = stringResource(R.string.backup_include_widgets),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.backup_include_widgets_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Grava o backup por cima do que existe.
 *
 * Duas coisas acontecem antes do [BackupManager.apply]: as chaves são adaptadas
 * aos perfis deste aparelho (o hash do `UserHandle` é local, ver
 * `remapUserHashes`) e, quando os widgets entram, os ids que estavam na home
 * são devolvidos ao host — o layout do arquivo toma o lugar do atual, e sem
 * isso os ids antigos ficariam alocados sem ninguém que os desenhe.
 */
private suspend fun restore(
    context: Context,
    backupManager: BackupManager,
    widgetPrefs: WidgetPrefs,
    widgetHost: WidgetHostManager,
    payload: BackupPayload,
    includeWidgets: Boolean,
) {
    val adapted = payload.remapUserHashes(
        knownHashes = profileHashes(context),
        primaryHash = Process.myUserHandle().hashCode(),
    )
    if (includeWidgets) {
        widgetPrefs.layout.first().allWidgetIds().forEach(widgetHost::deleteId)
    }
    backupManager.apply(adapted, includeWidgets)
}

/** Os perfis que existem agora: principal, trabalho, privado. */
private fun profileHashes(context: Context): Set<Int> {
    val manager = context.getSystemService(UserManager::class.java)
    val profiles = runCatching { manager?.userProfiles }.getOrNull()
        ?: listOf(Process.myUserHandle())
    return profiles.mapTo(HashSet()) { it.hashCode() }
}
