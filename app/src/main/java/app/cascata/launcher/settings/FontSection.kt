package app.cascata.launcher.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import app.cascata.launcher.data.theme.FontStore
import app.cascata.launcher.data.theme.ThemeSettings
import app.cascata.launcher.ui.theme.Fonts
import kotlinx.coroutines.launch
import java.io.File

/**
 * Os tipos que um seletor de arquivos costuma anunciar para uma fonte. O
 * `octet-stream` entra porque muito provedor não sabe o tipo de um `.ttf` — o
 * conteúdo é conferido na importação, então abrir demais aqui não é risco.
 */
private val FONT_MIME_TYPES = arrayOf(
    "font/ttf",
    "font/otf",
    "application/x-font-ttf",
    "application/octet-stream",
)

@Composable
internal fun FontSection(
    settings: ThemeSettings,
    customFont: File?,
    fontStore: FontStore,
    update: UpdateSettings,
    onCustomFontChanged: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.font_import_failed)
    // A família do arquivo importado é criada uma vez por arquivo, não por quadro.
    val customFamily = remember(customFont) { Fonts.family(Fonts.CUSTOM_ID, customFont) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                fontStore.importCustom(uri)
                    .onSuccess {
                        onCustomFontChanged()
                        update { it.copy(fontId = Fonts.CUSTOM_ID) }
                    }
                    .onFailure { onMessage(failed) }
            }
        }
    }

    SettingsSection(stringResource(R.string.section_font)) {
        ChoiceRow(
            label = stringResource(R.string.font_system),
            selected = settings.fontId == Fonts.SYSTEM_ID,
            onClick = { update { it.copy(fontId = Fonts.SYSTEM_ID) } },
        )

        // Cada fonte se apresenta: o rótulo é desenhado na própria família.
        Fonts.bundled.forEach { font ->
            ChoiceRow(
                label = font.label,
                selected = settings.fontId == font.id,
                family = font.family,
                onClick = { update { it.copy(fontId = font.id) } },
            )
        }

        ChoiceRow(
            label = stringResource(R.string.font_custom),
            selected = settings.fontId == Fonts.CUSTOM_ID,
            family = customFamily,
            // Sem arquivo ainda, tocar é escolher um; com arquivo, é voltar a usá-lo.
            onClick = {
                if (customFont == null) {
                    picker.launch(FONT_MIME_TYPES)
                } else {
                    update { it.copy(fontId = Fonts.CUSTOM_ID) }
                }
            },
        )

        if (customFont != null) {
            Row(modifier = Modifier.padding(horizontal = SIDE_PADDING - 12.dp)) {
                TextButton(onClick = { picker.launch(FONT_MIME_TYPES) }) {
                    Text(stringResource(R.string.font_custom_replace))
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            fontStore.removeCustom()
                            onCustomFontChanged()
                            // Sem arquivo, "personalizada" não desenha nada: volta ao sistema.
                            update {
                                if (it.fontId == Fonts.CUSTOM_ID) {
                                    it.copy(fontId = Fonts.SYSTEM_ID)
                                } else {
                                    it
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.font_custom_remove))
                }
            }
        }
    }
}
