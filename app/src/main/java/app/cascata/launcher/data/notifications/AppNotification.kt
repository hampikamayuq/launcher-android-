package app.cascata.launcher.data.notifications

import android.app.PendingIntent
import android.os.UserHandle
import app.cascata.launcher.data.appKeyOf

/**
 * Uma ação da notificação. [remoteInputKey] só vem preenchido quando a ação
 * aceita texto livre — é o que separa "Responder" de "Marcar como lida".
 */
data class NotificationAction(
    val title: String,
    val actionIntent: PendingIntent?,
    val remoteInputKey: String?,
    val remoteInputLabel: String?,
)

/**
 * Uma notificação como a lista precisa dela: texto já escolhido, ações já
 * achatadas. Vive em memória enquanto estiver na barra do sistema e some com
 * ela — ver o aviso no topo de [NotificationStore].
 *
 * `PendingIntent` e `UserHandle` são os únicos tipos de Android que sobraram:
 * não dá para disparar a ação nem casar o perfil sem eles.
 */
data class AppNotification(
    val key: String,
    override val packageName: String,
    val user: UserHandle,
    override val postTimeMillis: Long,
    val title: String?,
    val text: String?,
    val subText: String?,
    override val isGroupSummary: Boolean,
    override val groupKey: String?,
    val actions: List<NotificationAction>,
    val contentIntent: PendingIntent?,
    val isClearable: Boolean,
    val isOngoing: Boolean,
    val importance: Int?,
    val conversationTitle: String? = null,
    /** MediaStyle ou categoria de transporte: a UI mostra (ou esconde) diferente. */
    override val isMedia: Boolean = false,
) : NotificationFacts {
    /** Mesma fórmula do `AppEntry.appKey`: é por ela que a lista casa os dois. */
    override val appKey: String get() = appKeyOf(packageName, user.hashCode())
}
