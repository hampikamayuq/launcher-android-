package app.cascata.launcher.data.notifications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import app.cascata.launcher.notifications.CascataNotificationListener

/**
 * O acesso a notificações não é uma permissão que se peça num diálogo: quem
 * concede é o usuário, numa tela do sistema. Por isso `NotificationSettings.enabled`
 * é só a intenção — o que vale é [hasListenerAccess], relido a cada `onResume`.
 */
class NotificationAccess(private val context: Context) {

    fun hasListenerAccess(): Boolean = runCatching {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }.getOrDefault(false)

    /**
     * Do Android 11 em diante dá para abrir direto a chave do nosso serviço; antes
     * disso, só a lista inteira, e o usuário procura o Cascata nela.
     */
    fun listenerSettingsIntent(): Intent {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        val component = ComponentName(context, CascataNotificationListener::class.java)
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
    }
}
