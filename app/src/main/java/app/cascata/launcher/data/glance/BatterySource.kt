package app.cascata.launcher.data.glance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Carga em porcentagem (0..100) e se está carregando (tomada ou USB). */
data class BatteryState(val percent: Int, val charging: Boolean)

/**
 * Bateria sem permissão e sem polling: ACTION_BATTERY_CHANGED é sticky, então o
 * próprio `registerReceiver` já devolve o estado atual, e o sistema re-emite a
 * cada mudança de nível ou de tomada.
 */
class BatterySource(private val context: Context) {

    fun state(): Flow<BatteryState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.toState()?.let { trySend(it) }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = runCatching {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.getOrNull()
        sticky?.toState()?.let { trySend(it) }
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }.distinctUntilChanged()
}

/** O intent traz nível e escala separados porque nem todo aparelho usa escala 100. */
private fun Intent.toState(): BatteryState? {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryState(percent = level * 100 / scale, charging = charging)
}
