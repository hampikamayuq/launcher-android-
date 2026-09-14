package app.cascata.launcher.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import kotlin.math.roundToInt

/** Margem lateral comum a tudo na tela — a mesma da home. */
internal val SIDE_PADDING = 24.dp

/** Um bloco da tela: título e o que vem debaixo dele. */
@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            // Título de seção: o leitor de tela salta de seção em seção por ele.
            modifier = Modifier
                .semantics { heading() }
                .padding(horizontal = SIDE_PADDING, vertical = 8.dp),
        )
        content()
    }
}

/**
 * Linha tocável: rótulo e, quando existe, o valor atual embaixo. Desligada
 * ([enabled] false) ela mostra o estado sem prometer que tocá-la faz algo, como
 * a [SwitchRow].
 */
@Composable
internal fun SettingRow(
    label: String,
    value: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = SIDE_PADDING, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Linha com interruptor. [supporting] é o lugar do recado curto — "permissão
 * negada", "só na edição completa" — e [action] o do botão que resolve, quando
 * existe um. Linha desligada ([enabled] false) mostra o estado sem prometer que
 * tocá-la faz algo.
 */
@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    supporting: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SIDE_PADDING, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // A linha inteira é o interruptor: o alvo de toque passa a ser o
            // rótulo junto com ele, e o leitor de tela anuncia "ativado" /
            // "desativado" uma vez só, no lugar de um nó solto ao lado.
            modifier = Modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // Sem callback próprio: quem recebe o toque é a linha inteira, e
            // um segundo nó tocável só faria o TalkBack repetir o mesmo estado.
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
        action?.invoke()
    }
}

/**
 * Uma opção de lista com rádio. [family] existe para a fonte se mostrar na
 * própria fonte — nos outros usos fica null e o estilo decide.
 */
@Composable
internal fun ChoiceRow(
    label: String,
    selected: Boolean,
    family: FontFamily? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = SIDE_PADDING, vertical = 6.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            fontFamily = family,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Escolha única em botões segmentados. O par é `valor to rótulo` para que a
 * ordem da tela e a do enum nunca precisem coincidir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SegmentedChoice(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SIDE_PADDING, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        val selectedState = stringResource(R.string.state_selected)
        val unselectedState = stringResource(R.string.state_not_selected)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, text) ->
                val chosen = value == selected
                SegmentedButton(
                    selected = chosen,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    // O check desenhado não é falado: o estado vai por extenso.
                    modifier = Modifier.semantics {
                        stateDescription = if (chosen) selectedState else unselectedState
                    },
                    // O botão selecionado ganha o ícone de check por padrão — a
                    // diferença não fica só na cor de fundo. Duas linhas porque
                    // "Papel de parede" não cabe num terço da tela de um celular.
                    label = {
                        Text(
                            text = text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    },
                )
            }
        }
    }
}

/**
 * Slider que mostra o valor em porcentagem.
 *
 * O polegar anda pelo estado local para acompanhar o dedo; o valor gravado volta
 * pelo DataStore um instante depois e só assume quando vem de fora (importar um
 * tema, restaurar padrões). [steps] mantém a gravação em passos contados, em vez
 * de uma por quadro de arraste.
 */
@Composable
internal fun PercentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    var live by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { live = value }

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
                text = stringResource(R.string.percent, (live * 100).roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Slider(
            value = live,
            onValueChange = { novo ->
                if (novo != live) {
                    live = novo
                    onChange(novo)
                }
            },
            valueRange = range,
            steps = steps,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

/**
 * Depois de "não perguntar de novo" o diálogo não volta — o único caminho é a
 * tela do app nas configurações do sistema. Vale para toda permissão negada
 * nesta tela, não só para a dos cards.
 */
@Composable
internal fun AppSettingsButton() {
    val context = LocalContext.current
    TextButton(
        onClick = {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        },
    ) {
        Text(stringResource(R.string.glance_open_app_settings))
    }
}
