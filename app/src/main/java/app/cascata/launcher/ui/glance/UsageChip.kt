package app.cascata.launcher.ui.glance

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import app.cascata.launcher.data.AppEntry
import app.cascata.launcher.data.usage.AppUsage
import app.cascata.launcher.data.usage.UsageSource
import app.cascata.launcher.ui.usage.durationSpoken
import app.cascata.launcher.ui.usage.durationText

/** O nome do app mais usado não pode empurrar o total para fora da tela. */
private val USAGE_APP_MAX = 110.dp

/**
 * O tempo de tela de hoje, somado, com o app que mais consumiu ao lado. Some
 * sozinho quando não há uso nenhum — a lista chega vazia com o recurso
 * desligado ou sem o acesso do sistema.
 *
 * Toque abre a folha de uso, onde ficam o detalhe por app e os limites.
 */
@Composable
internal fun UsageChip(
    source: UsageSource,
    usage: List<AppUsage>,
    apps: Map<String, AppEntry>,
    onClick: () -> Unit,
) {
    val top = usage.firstOrNull() ?: return
    val total = remember(usage) { source.totalMillis(usage) }
    val topLabel = apps[top.packageName]?.label ?: top.packageName

    GlanceChip(
        description = stringResource(
            R.string.usage_chip_description,
            durationSpoken(total),
            topLabel,
        ),
        onClick = onClick,
    ) {
        ChipIcon(painterResource(R.drawable.ic_hourglass))
        ChipText(durationText(total))
        // Menor que o total: o app é a explicação, o tempo é o número.
        Text(
            text = topLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = USAGE_APP_MAX),
        )
    }
}
