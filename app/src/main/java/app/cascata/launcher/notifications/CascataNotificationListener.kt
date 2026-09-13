package app.cascata.launcher.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.cascata.launcher.data.notifications.NotificationStore

/**
 * A ponte entre a barra de status e o [NotificationStore]. A classe só é
 * instanciada depois que o usuário concede o acesso na tela do sistema: até lá o
 * Android nunca faz bind neste serviço, e o recurso fica inerte sem custo.
 *
 * Nada do que passa por aqui é gravado — ver o aviso no topo do store.
 */
class CascataNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        // O store não conhece o serviço; dispensar é um cancelamento no sistema.
        NotificationStore.connect { keys -> runCatching { cancelNotifications(keys) } }
        publishAll()
    }

    override fun onListenerDisconnected() {
        NotificationStore.disconnect()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        val posted = sbn ?: return
        // As nossas (se um dia houver) não voltam para a nossa lista.
        if (posted.packageName == packageName) return
        val mapped = posted.toAppNotification(rankingMap.importanceOf(posted.key))
        if (mapped != null) NotificationStore.posted(mapped) else NotificationStore.removed(posted.key)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        NotificationStore.removed(sbn?.key ?: return)
    }

    /** Ao conectar, a barra inteira: o que já estava lá não gera callback. */
    private fun publishAll() {
        val ranking = runCatching { currentRanking }.getOrNull()
        val active = runCatching { activeNotifications }.getOrNull() ?: emptyArray()
        NotificationStore.publish(
            active
                .filter { it.packageName != packageName }
                .mapNotNull { it.toAppNotification(ranking.importanceOf(it.key)) }
        )
    }

    /** Importância do canal, quando o ranking atual conhece a chave. */
    private fun RankingMap?.importanceOf(key: String): Int? {
        val map = this ?: return null
        val ranking = Ranking()
        val found = runCatching { map.getRanking(key, ranking) }.getOrDefault(false)
        return if (found) ranking.importance else null
    }
}
