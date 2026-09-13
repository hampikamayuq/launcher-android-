package app.cascata.launcher.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import app.cascata.launcher.data.notifications.BadgeStyle

/** O desenho: o contador cabe dois dígitos, o ponto é só presença. */
private val COUNT_SIZE = 20.dp
private val DOT_SIZE = 8.dp

/** Área de toque mínima. O desenho é menor que isto de propósito. */
private val TOUCH_SIZE = 40.dp

/** Acima disto o número não cabe no círculo e vira "9+". */
private const val MAX_COUNT = 9

/**
 * A marca de que o app tem notificação, na própria linha da lista. O toque é
 * dela, não da linha: abrir o app continua sendo tocar no nome.
 */
@Composable
fun NotificationBadge(
    count: Int,
    style: BadgeStyle,
    appLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    val description = pluralStringResource(
        R.plurals.notification_badge_description,
        count,
        count,
        appLabel,
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(TOUCH_SIZE)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        when (style) {
            BadgeStyle.COUNT -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(COUNT_SIZE)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            ) {
                Text(
                    text = if (count > MAX_COUNT) {
                        stringResource(R.string.notification_badge_overflow)
                    } else {
                        stringResource(R.string.notification_badge_number, count)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            BadgeStyle.DOT -> Box(
                modifier = Modifier
                    .size(DOT_SIZE)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}
