package app.cascata.launcher.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Uso de hoje, por app — lido do sistema a cada consulta.
 *
 * **Nada de uso é persistido.** O plano falava num banco Room local excluído do
 * backup; a regra mais forte do projeto é não guardar o que não precisa ser
 * guardado, e o Android já mantém esse histórico (`UsageStatsManager`) — basta
 * consultá-lo. O Cascata grava só as preferências do recurso: limites por app,
 * segundos de pausa e se o card aparece (`UsagePrefs`). Sem banco, sem tabela a
 * migrar, sem arquivo a excluir do `cloud-backup`, e desligar o recurso não
 * deixa rastro para apagar.
 */
class UsageSource(
    private val context: Context,
    private val access: UsageAccess,
) {

    /**
     * O gate de abertura consulta a cada toque em app com limite; sem isto,
     * cada toque viraria uma varredura de eventos do dia. Um minuto é curto
     * o bastante para o limite não passar despercebido.
     */
    private val cacheMillis = 60_000L

    @Volatile private var lastResult: List<AppUsage>? = null
    @Volatile private var lastAt: Long = 0L

    /** Vazio sem acesso — e sem consultar nada. */
    suspend fun today(): List<AppUsage> {
        if (!access.hasAccess()) {
            invalidate()
            return emptyList()
        }
        val now = System.currentTimeMillis()
        lastResult?.takeIf { now - lastAt in 0 until cacheMillis }?.let { return it }

        val start = startOfToday(now, ZoneId.systemDefault())
        val usage = withContext(Dispatchers.IO) { query(start, now) }
        lastResult = usage
        lastAt = now
        return usage
    }

    /** Depois de ligar/desligar o recurso ou de voltar de outro app. */
    fun invalidate() {
        lastResult = null
        lastAt = 0L
    }

    /** Soma do dia, para o card não repetir a conta em cada recomposição. */
    fun totalMillis(list: List<AppUsage>): Long = list.sumOf { it.totalMillis }

    private fun query(start: Long, now: Long): List<AppUsage> {
        val manager = ContextCompat.getSystemService(context, UsageStatsManager::class.java)
            ?: return emptyList()
        val events = runCatching { readEvents(manager, start, now) }.getOrDefault(emptyList())
        return aggregateForeground(events, start, now)
    }

    private fun readEvents(manager: UsageStatsManager, start: Long, now: Long): List<UsageEvent> {
        val raw = manager.queryEvents(start, now)
        val out = ArrayList<UsageEvent>()
        val event = UsageEvents.Event()
        while (raw.hasNextEvent()) {
            raw.getNextEvent(event)
            val packageName = event.packageName ?: continue
            // O launcher em primeiro plano não é "uso do aparelho": é o caminho.
            if (packageName == context.packageName) continue
            val kind = kindOf(event.eventType)
            if (kind == UsageEventKind.OTHER) continue
            out += UsageEvent(packageName, kind, event.timeStamp)
        }
        return out
    }
}

/**
 * O Android 10 renomeou os eventos de tela para `ACTIVITY_*` e acrescentou o
 * STOPPED; abaixo dele só existem os `MOVE_TO_*`.
 */
private fun kindOf(eventType: Int): UsageEventKind {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return when (eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventKind.RESUMED
            UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> UsageEventKind.PAUSED
            else -> UsageEventKind.OTHER
        }
    }
    @Suppress("DEPRECATION")
    return when (eventType) {
        UsageEvents.Event.MOVE_TO_FOREGROUND -> UsageEventKind.RESUMED
        UsageEvents.Event.MOVE_TO_BACKGROUND -> UsageEventKind.PAUSED
        else -> UsageEventKind.OTHER
    }
}
