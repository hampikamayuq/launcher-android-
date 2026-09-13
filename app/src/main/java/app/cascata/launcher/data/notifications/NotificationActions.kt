package app.cascata.launcher.data.notifications

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import androidx.core.os.bundleOf

/**
 * Abre a notificação: o `contentIntent` dela, que é o que o app quer que
 * aconteça. Intent cancelado (o app morreu, a notificação envelheceu) cai no
 * plano B — abrir o app pelo [LauncherApps], como um toque na lista faria.
 */
fun open(notification: AppNotification, context: Context): Boolean {
    val intent = notification.contentIntent
    if (intent != null && runCatching { intent.send() }.isSuccess) return true
    return runCatching {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
        val component = launcherApps
            .getActivityList(notification.packageName, notification.user)
            .firstOrNull()
            ?.componentName ?: return false
        launcherApps.startMainActivity(component, notification.user, null, null)
        true
    }.getOrDefault(false)
}

/** Dispara uma ação ("Arquivar", "Soneca"). Quem mostra o resultado é o app. */
fun fire(action: NotificationAction): Boolean {
    val intent = action.actionIntent ?: return false
    return runCatching { intent.send() }.isSuccess
}

/**
 * Resposta direta: o texto vai num `Bundle` sob a chave que o próprio app
 * escolheu, preenchendo o `RemoteInput` que veio com a ação. `send` com Context
 * é obrigatório aqui — é por ele que o intent de preenchimento viaja.
 */
fun reply(action: NotificationAction, text: String, context: Context): Boolean {
    val intent = action.actionIntent ?: return false
    val key = action.remoteInputKey ?: return false
    return runCatching {
        val fill = Intent()
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(key).build()),
            fill,
            bundleOf(key to text),
        )
        intent.send(context, 0, fill)
        true
    }.getOrDefault(false)
}
