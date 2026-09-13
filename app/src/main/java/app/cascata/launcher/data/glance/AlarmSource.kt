package app.cascata.launcher.data.glance

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** O próximo despertador do sistema, em epoch millis. */
data class NextAlarm(val triggerAtMillis: Long)

/**
 * Próximo alarme sem permissão nenhuma: o [AlarmManager] devolve o que o app de
 * relógio registrou e avisa por broadcast quando isso muda — nada de polling.
 */
class AlarmSource(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Emite o alarme atual ao assinar e a cada mudança; null quando não há nenhum. */
    fun next(): Flow<NextAlarm?> = callbackFlow {
        trySend(read())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(read())
            }
        }
        val filter = IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        // NOT_EXPORTED: é um broadcast do sistema, nenhum app precisa nos alcançar.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }.distinctUntilChanged()

    private fun read(): NextAlarm? = runCatching {
        alarmManager?.nextAlarmClock?.let { NextAlarm(it.triggerTime) }
    }.getOrNull()
}
