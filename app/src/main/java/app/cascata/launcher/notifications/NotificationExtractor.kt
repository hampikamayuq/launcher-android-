package app.cascata.launcher.notifications

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import app.cascata.launcher.data.notifications.AppNotification
import app.cascata.launcher.data.notifications.NotificationAction
import app.cascata.launcher.data.notifications.RawExtras
import app.cascata.launcher.data.notifications.displayText

/** O template que o sistema carimba nas notificações de player. */
private const val MEDIA_TEMPLATE = "android.app.Notification\$MediaStyle"

/**
 * Do `Notification` do sistema para o modelo da lista. Só aqui se mexe em
 * `Bundle`: a escolha do que mostrar é do `displayText`, que não sabe o que é
 * Android e por isso tem teste.
 *
 * Devolve null quando não sobrou nada para desenhar nem nada para tocar —
 * notificação de serviço em primeiro plano sem texto, por exemplo.
 */
fun StatusBarNotification.toAppNotification(importance: Int?): AppNotification? {
    val source = notification ?: return null
    val extras = source.extras ?: Bundle.EMPTY
    val message = lastMessageOf(source, extras)
    val conversationTitle = message?.conversationTitle
        ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)

    val shown = displayText(
        RawExtras(
            // BigTextStyle guarda o título dele à parte e ele é o mais completo.
            title = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
                ?: extras.getCharSequence(Notification.EXTRA_TITLE),
            text = extras.getCharSequence(Notification.EXTRA_TEXT),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.toList(),
            lastMessage = message?.text,
            lastMessageSender = message?.sender,
            conversationTitle = conversationTitle,
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
        )
    )

    val actions = source.actions?.map { it.toNotificationAction() }.orEmpty()
    if (shown.title == null && shown.text == null && actions.isEmpty()) return null

    return AppNotification(
        key = key,
        packageName = packageName,
        user = user,
        postTimeMillis = postTime,
        title = shown.title,
        text = shown.text,
        subText = shown.subText,
        isGroupSummary = source.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        groupKey = groupKey,
        actions = actions,
        contentIntent = source.contentIntent,
        isClearable = isClearable,
        isOngoing = isOngoing,
        importance = importance,
        conversationTitle = conversationTitle?.toString()?.trim()?.takeIf { it.isNotEmpty() },
        isMedia = extras.getString(Notification.EXTRA_TEMPLATE) == MEDIA_TEMPLATE ||
            source.category == Notification.CATEGORY_TRANSPORT,
    )
}

/** Ação com resposta direta é a que tem um `RemoteInput` de texto livre. */
private fun Notification.Action.toNotificationAction(): NotificationAction {
    val input = remoteInputs?.firstOrNull { it.allowFreeFormInput }
    return NotificationAction(
        title = title?.toString().orEmpty(),
        actionIntent = actionIntent,
        remoteInputKey = input?.resultKey,
        remoteInputLabel = input?.label?.toString(),
    )
}

private class LastMessage(
    val text: CharSequence?,
    val sender: CharSequence?,
    val conversationTitle: CharSequence?,
)

/**
 * A última mensagem de uma conversa. O caminho normal é o `MessagingStyle` — pelo
 * `NotificationCompat`, que é quem expõe a extração (a versão do framework é
 * interna) e já resolve `Person` desde o Android 8. O fallback existe para os
 * apps que montam `EXTRA_MESSAGES` na mão, sem estilo nenhum.
 */
private fun lastMessageOf(source: Notification, extras: Bundle): LastMessage? {
    val style = runCatching {
        NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(source)
    }.getOrNull()
    val last = style?.messages?.lastOrNull()
    if (last != null) {
        return LastMessage(last.text, last.person?.name, style.conversationTitle)
    }
    val bundle = extras.parcelableArray(Notification.EXTRA_MESSAGES)?.lastOrNull() as? Bundle
        ?: return null
    return LastMessage(
        text = bundle.getCharSequence("text"),
        sender = bundle.getCharSequence("sender"),
        conversationTitle = null,
    )
}

/** A versão sem classe foi depreciada no 33; a tipada só existe de lá para cá. */
@Suppress("DEPRECATION")
private fun Bundle.parcelableArray(key: String): Array<out Parcelable>? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArray(key, Bundle::class.java)
    } else {
        getParcelableArray(key)
    }
