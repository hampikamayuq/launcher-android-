package app.cascata.launcher.ui.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import app.cascata.launcher.data.notifications.AppNotification
import app.cascata.launcher.data.notifications.NotificationAction

/** Quanto do `surfaceVariant` fica: o bloco se separa da lista sem virar caixa opaca. */
private const val BLOCK_ALPHA = 0.6f

private val BLOCK_SHAPE = RoundedCornerShape(12.dp)

/** Botões de ícone dentro do bloco: menores que os 48.dp do padrão, que empilhariam. */
private val COMPACT_BUTTON = 36.dp

private val COMPACT_ICON = 18.dp

/** Até onde o texto de uma notificação se estica antes de cortar. */
private const val MAX_TEXT_LINES = 3

/**
 * O bloco que aparece embaixo da linha do app expandido. Mora num item próprio
 * da `LazyColumn` (ver `HomeScreen`), e não dentro da linha, para a lista medir e
 * reciclar o bloco como qualquer outro item.
 *
 * A saída precisa de um item que ainda exista: por isso [expanded] e
 * [onCollapsed] são separados — a home só tira o item da lista quando a animação
 * de recolher termina e este bloco avisa.
 */
@Composable
fun NotificationInline(
    appLabel: String,
    notifications: List<AppNotification>,
    expanded: Boolean,
    onOpen: (AppNotification) -> Unit,
    onDismiss: (String) -> Unit,
    onDismissAll: () -> Unit,
    onFireAction: (NotificationAction) -> Unit,
    onReply: (NotificationAction, String) -> Boolean,
    onCollapse: () -> Unit,
    onCollapsed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A transição é a mesma da AnimatedVisibility: ela obedece à escala de
    // animação do sistema, inclusive quando está desligada (aí é instantânea).
    val transition = remember { MutableTransitionState(false) }
    transition.targetState = expanded

    if (transition.isIdle && !transition.currentState && !transition.targetState) {
        LaunchedEffect(Unit) { onCollapsed() }
    }

    AnimatedVisibility(
        visibleState = transition,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        NotificationBlock(
            appLabel = appLabel,
            notifications = notifications,
            onOpen = onOpen,
            onDismiss = onDismiss,
            onDismissAll = onDismissAll,
            onFireAction = onFireAction,
            onReply = onReply,
            onCollapse = onCollapse,
        )
    }
}

/**
 * O bloco em si, já aberto — sem a animação que o [NotificationInline] põe por
 * fora. Separado porque a prévia de screenshot renderiza um quadro só: dentro da
 * [AnimatedVisibility] ela pegaria sempre a altura zero do começo da animação.
 */
@Composable
internal fun NotificationBlock(
    appLabel: String,
    notifications: List<AppNotification>,
    onOpen: (AppNotification) -> Unit,
    onDismiss: (String) -> Unit,
    onDismissAll: () -> Unit,
    onFireAction: (NotificationAction) -> Unit,
    onReply: (NotificationAction, String) -> Boolean,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(BLOCK_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = BLOCK_ALPHA))
            .padding(vertical = 4.dp),
    ) {
        // A hora relativa é calculada uma vez por abertura: o bloco vive
        // segundos, e um relógio por notificação custaria mais que vale.
        val now = remember(notifications) { System.currentTimeMillis() }

        notifications.forEach { notification ->
            NotificationItem(
                notification = notification,
                appLabel = appLabel,
                nowMillis = now,
                onOpen = {
                    onOpen(notification)
                    onCollapse()
                },
                onDismiss = { onDismiss(notification.key) },
                onFireAction = onFireAction,
                onReply = { action, text ->
                    onReply(action, text).also { sent -> if (sent) onCollapse() }
                },
            )
        }

        // Sem nenhuma dispensável (mídia, downloads, "rodando em segundo
        // plano"), o botão não teria o que dispensar.
        if (notifications.any { it.isClearable }) {
            TextButton(
                onClick = onDismissAll,
                modifier = Modifier.align(Alignment.End).padding(end = 4.dp),
            ) {
                Text(stringResource(R.string.notification_dismiss_all))
            }
        }
    }
}

@Composable
private fun NotificationItem(
    notification: AppNotification,
    appLabel: String,
    nowMillis: Long,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onFireAction: (NotificationAction) -> Unit,
    onReply: (NotificationAction, String) -> Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            // Uma parada só do leitor de tela por notificação: título, hora e
            // texto são uma coisa. Os botões, que têm ação própria, continuam
            // sendo nós separados.
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    // Abre a notificação no app: é botão, e o leitor de tela
                    // precisa dizer isso antes de o usuário tocar.
                    .clickable(role = Role.Button, onClick = onOpen)
                    .padding(vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // Notificação sem título é comum (downloads, mídia): o
                        // nome do app diz de quem ela é.
                        text = notification.title ?: appLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = relativeLabel(notification.postTimeMillis, nowMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                notification.text?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = MAX_TEXT_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                notification.subText?.let { sub ->
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Contínua não se dispensa: o sistema recusaria o cancelamento.
            if (notification.isClearable) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(COMPACT_BUTTON)) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.notification_dismiss),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(COMPACT_ICON),
                    )
                }
            }
        }

        val direct = notification.actions.filter { it.remoteInputKey == null }
        if (direct.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                direct.forEach { action ->
                    TextButton(onClick = { onFireAction(action) }) {
                        Text(text = action.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        notification.actions
            .filter { it.remoteInputKey != null }
            .forEach { action -> ReplyField(action = action, onReply = onReply) }
    }
}

/**
 * Resposta direta. O rótulo do campo é o que o próprio app escolheu
 * ("Responder", "Mensagem"); quando ele não manda nenhum, usamos o nosso.
 */
@Composable
private fun ReplyField(
    action: NotificationAction,
    onReply: (NotificationAction, String) -> Boolean,
) {
    var text by remember(action) { mutableStateOf("") }
    val label = action.remoteInputLabel?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.notification_reply_hint)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            textStyle = MaterialTheme.typography.bodySmall,
            placeholder = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            modifier = Modifier
                .weight(1f)
                // O campo vazio não tem rótulo visível: sem isto o leitor de
                // tela anuncia só "caixa de edição".
                .semantics { contentDescription = label },
        )
        IconButton(
            // Enviar em branco fecharia a notificação sem mandar nada.
            onClick = { if (text.isNotBlank() && onReply(action, text)) text = "" },
            modifier = Modifier.size(COMPACT_BUTTON),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Send,
                contentDescription = stringResource(R.string.notification_reply_send),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(COMPACT_ICON),
            )
        }
    }
}

/** O caso puro de [relativeTime] virando a frase curta que cabe ao lado do título. */
@Composable
private fun relativeLabel(postTimeMillis: Long, nowMillis: Long): String =
    when (val elapsed = relativeTime(postTimeMillis, nowMillis)) {
        RelativeTime.Now -> stringResource(R.string.notification_time_now)
        is RelativeTime.Minutes -> stringResource(R.string.notification_time_minutes, elapsed.value)
        is RelativeTime.Hours -> stringResource(R.string.notification_time_hours, elapsed.value)
        RelativeTime.Yesterday -> stringResource(R.string.notification_time_yesterday)
        is RelativeTime.Days -> stringResource(R.string.notification_time_days, elapsed.value)
    }
