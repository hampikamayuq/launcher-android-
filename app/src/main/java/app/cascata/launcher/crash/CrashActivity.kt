package app.cascata.launcher.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R

/**
 * A tela que aparece no lugar do "o app fechou".
 *
 * Roda no processo `:crash`, separado do principal (ver `android:process` no
 * manifesto): o processo que quebrou já morreu quando esta janela sobe, e nada
 * do [app.cascata.launcher.CascataApp] está inicializado aqui.
 *
 * Por isso a tela é deliberadamente burra: nada de DataStore, `ThemePrefs`,
 * `CascataTheme`, fonte importada ou qualquer objeto do app — só [MaterialTheme]
 * com a tipografia padrão do Compose. Toda dependência que ela tivesse seria uma
 * chance nova de a tela de erro também quebrar, e aí não sobraria nada na tela.
 */
class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // O extra é o caminho normal; o arquivo é a rede de segurança, e também
        // é por onde as configurações abrem esta tela sem uma falha nova.
        val report = runCatching { intent?.getStringExtra(CrashReporter.EXTRA_REPORT) }.getOrNull()
            ?: CrashReporter.lastReport(this)
            ?: ""

        setContent {
            MaterialTheme {
                CrashScreen(
                    report = report,
                    onClose = { finishAndRemoveTask() },
                )
            }
        }
    }
}

@Composable
private fun CrashScreen(report: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val copied = stringResource(R.string.crash_copied)
    val shareTitle = stringResource(R.string.crash_share)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.crash_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.crash_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            // O bloco do rastro: rola nos dois eixos (linhas de stack trace são
            // largas) e é selecionável, para quem quiser copiar só um pedaço.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            ) {
                SelectionContainer {
                    Text(
                        text = report,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = {
                    if (copyToClipboard(context, report)) {
                        runCatching { Toast.makeText(context, copied, Toast.LENGTH_SHORT).show() }
                    }
                }) {
                    Text(stringResource(R.string.crash_copy))
                }
                TextButton(onClick = { share(context, report, shareTitle) }) {
                    Text(stringResource(R.string.crash_share))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.crash_close))
                }
            }
        }
    }
}

/**
 * Área de transferência do sistema, e não a do Compose: `LocalClipboardManager`
 * depende do resto da plataforma de composição estar de pé, e esta tela não
 * quer depender de nada além do que já desenha.
 */
private fun copyToClipboard(context: Context, report: String): Boolean = runCatching {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText("Cascata", report))
    true
}.getOrDefault(false)

/** Mandar para quem mantém o app: qualquer coisa que aceite texto serve. */
private fun share(context: Context, report: String, title: String) {
    runCatching {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, report)
        context.startActivity(Intent.createChooser(send, title))
    }
}
