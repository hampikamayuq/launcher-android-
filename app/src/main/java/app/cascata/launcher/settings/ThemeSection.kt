package app.cascata.launcher.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import app.cascata.launcher.data.theme.ThemeFile
import app.cascata.launcher.data.theme.ThemePrefs
import app.cascata.launcher.data.theme.ThemeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

/** Um tema tem poucos KB; ler mais que isso já diz que o arquivo é outro. */
private const val MAX_THEME_BYTES = 64 * 1024

/** Tipos aceitos na importação: o do arquivo e o coringa, para provedores que não classificam. */
private val THEME_MIME_TYPES = arrayOf(ThemeFile.MIME, "*/*")

@Composable
internal fun WallpaperSection() {
    val context = LocalContext.current
    val label = stringResource(R.string.wallpaper_change)

    SettingsSection(stringResource(R.string.section_wallpaper)) {
        SettingRow(
            label = label,
            onClick = {
                // O chooser é quem lista os apps de papel de parede; o launcher
                // não tem galeria própria e não precisa de uma.
                val intent = Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), label)
                runCatching { context.startActivity(intent) }
            },
        )
    }
}

@Composable
internal fun ThemeSection(
    settings: ThemeSettings,
    themePrefs: ThemePrefs,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportFailed = stringResource(R.string.theme_export_failed)
    val importFailed = stringResource(R.string.theme_import_failed)
    var confirmReset by remember { mutableStateOf(false) }

    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ThemeFile.MIME)
    ) { uri ->
        if (uri != null) {
            val text = ThemeFile.encode(settings)
            scope.launch {
                val written = withContext(Dispatchers.IO) {
                    runCatching {
                        val stream = context.contentResolver.openOutputStream(uri)
                            ?: error("sem stream de escrita")
                        stream.use { it.write(text.toByteArray()) }
                    }
                }
                if (written.isFailure) onMessage(exportFailed)
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
                        stream.use { it.readLimited(MAX_THEME_BYTES) }
                    }.mapCatching { ThemeFile.decode(it).getOrThrow() }
                }
                decoded
                    .onSuccess { themePrefs.replace(it) }
                    .onFailure { onMessage(importFailed) }
            }
        }
    }

    SettingsSection(stringResource(R.string.section_theme)) {
        SettingRow(
            label = stringResource(R.string.theme_export),
            onClick = { export.launch("cascata.${ThemeFile.EXTENSION}") },
        )
        SettingRow(
            label = stringResource(R.string.theme_import),
            onClick = { import.launch(THEME_MIME_TYPES) },
        )
        SettingRow(
            label = stringResource(R.string.theme_reset),
            onClick = { confirmReset = true },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.theme_reset)) },
            text = { Text(stringResource(R.string.theme_reset_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { themePrefs.reset() }
                        confirmReset = false
                    },
                ) {
                    Text(stringResource(R.string.action_restore))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
internal fun AboutSection() {
    val context = LocalContext.current
    var licenses by remember { mutableStateOf(false) }

    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
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

/**
 * Lê no máximo [max] bytes como UTF-8 e recusa o que passar disso: o seletor de
 * arquivos deixa escolher qualquer coisa, inclusive um vídeo.
 */
private fun InputStream.readLimited(max: Int): String {
    val buffer = ByteArray(max + 1)
    var total = 0
    while (total <= max) {
        val read = read(buffer, total, buffer.size - total)
        if (read <= 0) break
        total += read
    }
    require(total <= max) { "arquivo grande demais para um tema" }
    return String(buffer, 0, total, Charsets.UTF_8)
}
