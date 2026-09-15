package app.cascata.launcher.settings

import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import app.cascata.launcher.crash.CrashActivity
import app.cascata.launcher.crash.CrashReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AboutSection(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    var licenses by remember { mutableStateOf(false) }
    val cleared = stringResource(R.string.crash_cleared)

    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    /**
     * O relatório é um arquivo, não uma preferência: nada avisa quando ele
     * aparece ou some. Relemos quando o contador muda — depois de apagar — e a
     * leitura sai da main thread porque é disco.
     */
    var reload by remember { mutableIntStateOf(0) }
    val report by produceState<String?>(null, reload) {
        value = withContext(Dispatchers.IO) { CrashReporter.lastReport(context) }
    }

    SettingsSection(stringResource(R.string.section_about)) {
        Text(
            text = stringResource(R.string.about_version, version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SIDE_PADDING, vertical = 12.dp),
        )
        SettingRow(
            label = stringResource(R.string.about_licenses),
            onClick = { licenses = true },
        )

        // Só aparece quando existe relatório — o caso normal é não existir, e
        // uma linha permanente prometendo "última falha" só assustaria.
        val saved = report
        if (saved != null) {
            SettingRow(
                label = stringResource(R.string.crash_last_report),
                // A primeira linha do relatório já diz versão, edição e aparelho.
                value = saved.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() },
                onClick = {
                    // Outro processo (`:crash`): tarefa própria, como quando é a
                    // própria falha que abre a tela.
                    val intent = Intent(context, CrashActivity::class.java)
                        .putExtra(CrashReporter.EXTRA_REPORT, saved)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                },
            )
            Row(modifier = Modifier.padding(horizontal = SIDE_PADDING - 12.dp)) {
                TextButton(
                    onClick = {
                        CrashReporter.clear(context)
                        reload++
                        onMessage(cleared)
                    },
                ) {
                    Text(stringResource(R.string.crash_clear))
                }
            }
        }
    }

    if (licenses) {
        AlertDialog(
            onDismissRequest = { licenses = false },
            title = { Text(stringResource(R.string.about_licenses)) },
            text = { Text(stringResource(R.string.about_licenses_body)) },
            confirmButton = {
                TextButton(onClick = { licenses = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}
