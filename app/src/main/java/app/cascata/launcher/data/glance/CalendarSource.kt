package app.cascata.launcher.data.glance

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import app.cascata.launcher.crash.catchQuietly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val TAG = "CascataGlance"

private val PROJECTION = arrayOf(
    CalendarContract.Instances.EVENT_ID,
    CalendarContract.Instances.TITLE,
    CalendarContract.Instances.BEGIN,
    CalendarContract.Instances.END,
    CalendarContract.Instances.ALL_DAY,
    CalendarContract.Instances.DISPLAY_COLOR,
    // Fallback: em algumas agendas o evento não tem cor própria e só a agenda tem.
    CalendarContract.Instances.CALENDAR_COLOR,
)

/**
 * Próximos eventos da agenda do aparelho. Nada é consultado sem READ_CALENDAR, e
 * a permissão não é pedida aqui — quem pede é a UI, no momento em que o card é
 * ligado. Sem permissão o fluxo emite lista vazia e encerra.
 */
class CalendarSource(private val context: Context) {

    fun hasPermission(): Boolean = runCatching {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * Emite ao assinar e a cada mudança na agenda (um [ContentObserver], sem
     * polling). Como a permissão é lida uma vez, ao concedê-la a UI reassina o
     * fluxo — é o que acontece naturalmente ao ligar o card.
     */
    fun upcoming(windowHours: Int = 24, limit: Int = 3): Flow<List<CalendarEvent>> {
        if (!hasPermission()) return flowOf(emptyList())
        return changes()
            .conflate()
            .map { query(windowHours, limit) }
            .distinctUntilChanged()
            // A consulta já devolve vazio ao falhar; isto cobre o resto do
            // caminho — registrar o observador, ordenar, o que for.
            .catchQuietly(TAG, "a agenda não respondeu")
    }

    private suspend fun query(windowHours: Int, limit: Int): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val end = now + windowHours.coerceAtLeast(0) * 60L * 60L * 1000L
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .also { ContentUris.appendId(it, now) }
                .also { ContentUris.appendId(it, end) }
                .build()
            val raw = runCatching {
                context.contentResolver.query(
                    uri,
                    PROJECTION,
                    null,
                    null,
                    "${CalendarContract.Instances.BEGIN} ASC",
                )?.use { cursor -> cursor.readEvents() }.orEmpty()
            }.getOrElse {
                // Permissão revogada com o app aberto lança SecurityException aqui;
                // provedores de agenda de alguns aparelhos lançam outras coisas.
                // Nenhuma delas vale derrubar a tela inicial: o card fica vazio.
                emptyList()
            }
            runCatching { upcomingEvents(raw, now, limit) }.getOrDefault(emptyList())
        }

    /** Emite uma vez de saída e depois a cada notificação do provedor de agenda. */
    private fun changes(): Flow<Unit> = callbackFlow {
        // A primeira emissão é o que faz a consulta acontecer mesmo sem
        // observador registrado: sem atualização automática, mas com dado.
        trySend(Unit)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) { trySend(Unit) }
        }
        val registered = runCatching {
            context.contentResolver.registerContentObserver(
                CalendarContract.CONTENT_URI,
                true,
                observer,
            )
        }.isSuccess
        awaitClose {
            if (registered) runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }
}

private fun android.database.Cursor.readEvents(): List<CalendarEvent> {
    val events = ArrayList<CalendarEvent>(count)
    while (moveToNext()) {
        val displayColor = if (isNull(5)) 0 else getInt(5)
        val calendarColor = if (isNull(6)) 0 else getInt(6)
        events += CalendarEvent(
            id = getLong(0),
            title = if (isNull(1)) "" else getString(1),
            beginMillis = getLong(2),
            endMillis = getLong(3),
            allDay = getInt(4) != 0,
            color = (if (displayColor != 0) displayColor else calendarColor).takeIf { it != 0 },
        )
    }
    return events
}
