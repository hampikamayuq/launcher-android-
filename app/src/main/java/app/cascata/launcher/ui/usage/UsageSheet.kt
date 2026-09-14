package app.cascata.launcher.ui.usage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import app.cascata.launcher.data.AppEntry
import app.cascata.launcher.data.IconSource
import app.cascata.launcher.data.usage.AppUsage
import app.cascata.launcher.ui.AppIcon

private val USAGE_ICON = 36.dp

/** Dez linhas é o que se lê de uma vez; o resto do dia é cauda. */
private const val USAGE_ROWS = 10

/**
 * O detalhe do dia: quem consumiu quanto, do maior para o menor, e o limite de
 * cada um. Tocar numa linha abre o diálogo do limite daquele app.
 *
 * O [AppEntry] vem do mapa por pacote; app que não está na lista do launcher
 * (serviço do sistema, app de outro perfil) aparece pelo nome do pacote, sem
 * ícone — melhor que sumir da conta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageSheet(
    usage: List<AppUsage>,
    apps: Map<String, AppEntry>,
    limits: Map<String, Int>,
    repository: IconSource,
    onSetLimit: (String, Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        UsageSheetContent(
            usage = usage,
            apps = apps,
            limits = limits,
            repository = repository,
            onEdit = { editing = it },
        )
    }

    editing?.let { packageName ->
        LimitDialog(
            appLabel = apps[packageName]?.label ?: packageName,
            minutes = limits[packageName],
            onConfirm = { minutes ->
                onSetLimit(packageName, minutes)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/**
 * O conteúdo da folha, sem a folha: é o que a prévia de screenshot renderiza —
 * o [ModalBottomSheet] vive numa janela própria e não cabe numa imagem estática.
 */
@Composable
internal fun UsageSheetContent(
    usage: List<AppUsage>,
    apps: Map<String, AppEntry>,
    limits: Map<String, Int>,
    repository: IconSource,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.usage_today),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .semantics { heading() }
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )

        usage.take(USAGE_ROWS).forEach { item ->
            UsageRow(
                item = item,
                entry = apps[item.packageName],
                limit = limits[item.packageName],
                repository = repository,
                onClick = { onEdit(item.packageName) },
            )
        }

        Text(
            text = stringResource(R.string.usage_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun UsageRow(
    item: AppUsage,
    entry: AppEntry?,
    limit: Int?,
    repository: IconSource,
    onClick: () -> Unit,
) {
    // "1 h 20 min" lido em voz alta vira "um h"; a linha inteira é anunciada
    // com o tempo por extenso, e o limite junto.
    val description = stringResource(
        R.string.usage_row_description,
        entry?.label ?: item.packageName,
        durationSpoken(item.totalMillis),
        limitText(limit),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        // Sem entrada não há ícone, mas o espaço dele fica: a lista não desalinha.
        if (entry != null) {
            AppIcon(entry = entry, repository = repository, size = USAGE_ICON)
        } else {
            Box(Modifier.size(USAGE_ICON))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry?.label ?: item.packageName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = limitText(limit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = durationText(item.totalMillis),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
