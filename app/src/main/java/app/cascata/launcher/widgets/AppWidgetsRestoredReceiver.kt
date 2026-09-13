package app.cascata.launcher.widgets

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.cascata.launcher.CascataApp
import app.cascata.launcher.data.widgets.WIDGET_HOST_ID
import app.cascata.launcher.data.widgets.remapIds
import kotlinx.coroutines.launch

/**
 * Depois de um restore de backup, os ids de widget que gravamos não valem mais: o
 * sistema alocou outros e avisa aqui, um array com os antigos e outro com os
 * novos, na mesma ordem. Sem este remapeamento a home volta com molduras vazias.
 */
class AppWidgetsRestoredReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AppWidgetManager.ACTION_APPWIDGET_HOST_RESTORED) return
        // A mesma ação chega para todo host do app; só o nosso interessa.
        if (intent.getIntExtra(AppWidgetManager.EXTRA_HOST_ID, -1) != WIDGET_HOST_ID) return

        val old = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_OLD_IDS) ?: return
        val new = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS) ?: return
        if (old.isEmpty() || old.size != new.size) return

        val app = context.applicationContext as? CascataApp ?: return
        // DataStore é suspenso e o receiver não pode bloquear: goAsync segura o
        // processo vivo até a gravação terminar.
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.widgetPrefs.update { it.remapIds(old, new) }
            } finally {
                pending.finish()
            }
        }
    }
}
