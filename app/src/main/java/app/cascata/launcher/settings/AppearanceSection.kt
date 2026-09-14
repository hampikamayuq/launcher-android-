package app.cascata.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import app.cascata.launcher.data.theme.ColorSource
import app.cascata.launcher.data.theme.DarkMode
import app.cascata.launcher.data.theme.Density
import app.cascata.launcher.data.theme.MAX_FONT_SCALE
import app.cascata.launcher.data.theme.MIN_FONT_SCALE
import app.cascata.launcher.data.theme.ThemeSettings
import app.cascata.launcher.data.theme.contrastRatio
import java.util.Locale
import kotlin.math.roundToInt

/** Doze cores de destaque espalhadas pelo círculo — uma fileira, sem seletor de matiz. */
private val ACCENT_SWATCHES = listOf(
    0xFFD32F2F, 0xFFE64A19, 0xFFF9A825, 0xFF7CB342,
    0xFF2E7D32, 0xFF00897B, 0xFF0288D1, 0xFF2E5AAC,
    0xFF5E35B1, 0xFF8E24AA, 0xFFC2185B, 0xFF6D4C41,
).map { it.toInt() }

private val SWATCH_SIZE = 44.dp

/** Passos da opacidade e do tamanho do texto: 5% e 0,05 — o que o rótulo mostra. */
private const val OPACITY_STEPS = 19
private const val FONT_SCALE_STEP = 0.05f

@Composable
internal fun AppearanceSection(settings: ThemeSettings, update: UpdateSettings) {
    SettingsSection(stringResource(R.string.section_appearance)) {
        SegmentedChoice(
            label = stringResource(R.string.dark_mode),
            options = listOf(
                DarkMode.SYSTEM to stringResource(R.string.dark_mode_system),
                DarkMode.LIGHT to stringResource(R.string.dark_mode_light),
                DarkMode.DARK to stringResource(R.string.dark_mode_dark),
            ),
            selected = settings.darkMode,
            onSelect = { mode -> update { it.copy(darkMode = mode) } },
        )

        SegmentedChoice(
            label = stringResource(R.string.colors),
            options = listOf(
                ColorSource.SYSTEM to stringResource(R.string.color_source_system),
                ColorSource.WALLPAPER to stringResource(R.string.color_source_wallpaper),
                ColorSource.ACCENT to stringResource(R.string.color_source_accent),
            ),
            selected = settings.colorSource,
            onSelect = { source -> update { it.copy(colorSource = source) } },
        )

        // A fileira de cores só faz sentido quando a cor é escolhida a dedo.
        if (settings.colorSource == ColorSource.ACCENT) {
            AccentSwatches(
                selected = settings.accentArgb,
                onSelect = { argb -> update { it.copy(accentArgb = argb) } },
            )
        }

        PercentSlider(
            label = stringResource(R.string.background_opacity),
            value = settings.backgroundOpacity,
            range = 0f..1f,
            steps = OPACITY_STEPS,
            onChange = { alpha -> update { it.copy(backgroundOpacity = alpha) } },
        )

        SegmentedChoice(
            label = stringResource(R.string.density),
            options = listOf(
                Density.COMPACT to stringResource(R.string.density_compact),
                Density.DEFAULT to stringResource(R.string.density_default),
                Density.COMFORTABLE to stringResource(R.string.density_comfortable),
            ),
            selected = settings.density,
            onSelect = { density -> update { it.copy(density = density) } },
        )

        PercentSlider(
            label = stringResource(R.string.font_scale),
            value = settings.fontScale,
            range = MIN_FONT_SCALE..MAX_FONT_SCALE,
            steps = ((MAX_FONT_SCALE - MIN_FONT_SCALE) / FONT_SCALE_STEP).roundToInt() - 1,
            onChange = { scale -> update { it.copy(fontScale = scale) } },
        )
    }
}

@Composable
private fun AccentSwatches(selected: Int, onSelect: (Int) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val custom = selected !in ACCENT_SWATCHES

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = SIDE_PADDING, vertical = 8.dp),
    ) {
        ACCENT_SWATCHES.forEachIndexed { index, argb ->
            val chosen = argb == selected
            Swatch(
                argb = argb,
                selected = chosen,
                // O "selecionada" saiu do nome: quem diz isso é o estado do nó,
                // e o TalkBack já o lê sozinho na fileira inteira.
                description = stringResource(R.string.color_swatch, index + 1),
                onClick = { onSelect(argb) },
            )
        }
        // A décima terceira amostra é a cor que não está na fileira: mostra a cor
        // em uso e abre o campo hexadecimal.
        Swatch(
            argb = selected,
            selected = custom,
            description = stringResource(R.string.color_custom),
            idle = Icons.Outlined.Edit,
            onClick = { editing = true },
        )
    }

    if (editing) {
        CustomColorDialog(
            initial = selected,
            onConfirm = {
                onSelect(it)
                editing = false
            },
            onCancel = { editing = false },
        )
    }
}

/**
 * Um círculo de cor. Selecionado ganha borda grossa **e** o check dentro — a
 * escolha nunca fica marcada só pela cor.
 */
@Composable
private fun Swatch(
    argb: Int,
    selected: Boolean,
    description: String,
    idle: ImageVector? = null,
    onClick: () -> Unit,
) {
    val selectedState = stringResource(R.string.state_selected)
    val unselectedState = stringResource(R.string.state_not_selected)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(SWATCH_SIZE)
            .clip(CircleShape)
            .background(Color(argb))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = CircleShape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = description
                stateDescription = if (selected) selectedState else unselectedState
            },
    ) {
        val mark = if (selected) Icons.Outlined.Check else idle
        if (mark != null) {
            Icon(imageVector = mark, contentDescription = null, tint = inkOn(argb))
        }
    }
}

/** Diálogo do hexadecimal. Salvar só liga quando o texto é um `#RRGGBB` de verdade. */
@Composable
private fun CustomColorDialog(initial: Int, onConfirm: (Int) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(hexOf(initial)) }
    val parsed = parseHexColor(text)

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.color_custom_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = parsed == null,
                supportingText = {
                    Text(
                        stringResource(
                            if (parsed == null) R.string.color_custom_invalid else R.string.color_custom_hint,
                        ),
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** `#RRGGBB` (com ou sem `#`) para ARGB opaco; null quando não é isso. */
private fun parseHexColor(text: String): Int? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6 || hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    return 0xFF000000.toInt() or hex.toInt(16)
}

private fun hexOf(argb: Int): String = String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)

/** Preto ou branco sobre a amostra — o que der mais contraste com ela. */
private fun inkOn(argb: Int): Color {
    val white = 0xFFFFFFFF.toInt()
    val black = 0xFF000000.toInt()
    return if (contrastRatio(white, argb) >= contrastRatio(black, argb)) Color.White else Color.Black
}
