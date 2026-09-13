package app.cascata.launcher.data.glance

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import app.cascata.launcher.notifications.CascataNotificationListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * A faixa da sessão de mídia ativa. O [controller] vai junto porque é por ele
 * que os botões agem — e ele não é serializável nem persistido, como o resto.
 */
data class MediaState(
    val packageName: String,
    val title: String?,
    val artist: String?,
    val playing: Boolean,
    val controller: MediaController,
)

/**
 * Sessões de mídia do aparelho. O `MediaSessionManager` só as entrega a quem tem
 * acesso a notificações — é a mesma chave, e sem ela ele lança `SecurityException`.
 * Nesse caso o fluxo emite null e fica quieto: o card some, nada quebra.
 */
class MediaSource(private val context: Context) {

    private val component = ComponentName(context, CascataNotificationListener::class.java)

    fun current(): Flow<MediaState?> = callbackFlow {
        val manager = context.getSystemService(MediaSessionManager::class.java)
        if (manager == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        // Tudo o que mexe em `bound` roda neste handler: o listener de sessões e
        // o callback do controller já chegam nele, e a primeira leitura é postada.
        val handler = Handler(Looper.getMainLooper())
        var bound: MediaController? = null
        var callback: MediaController.Callback? = null

        fun unbind() {
            val controller = bound
            val current = callback
            if (controller != null && current != null) {
                runCatching { controller.unregisterCallback(current) }
            }
            bound = null
            callback = null
        }

        fun rebind() {
            val sessions = runCatching { manager.getActiveSessions(component) }.getOrNull().orEmpty()
            // A que está tocando manda; sem nenhuma tocando, a mais recente da lista.
            val chosen = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: sessions.firstOrNull()
            if (chosen != null && chosen.sessionToken == bound?.sessionToken) {
                trySend(bound?.toState())
                return
            }
            unbind()
            if (chosen != null) {
                val fresh = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        trySend(bound?.toState())
                    }

                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        // Pausar aqui pode fazer outra sessão virar a principal.
                        rebind()
                    }

                    override fun onSessionDestroyed() {
                        rebind()
                    }
                }
                runCatching { chosen.registerCallback(fresh, handler) }
                bound = chosen
                callback = fresh
            }
            trySend(bound?.toState())
        }

        val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { rebind() }
        val listening = runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsChanged, component, handler)
        }.isSuccess
        handler.post { rebind() }

        awaitClose {
            if (listening) runCatching { manager.removeOnActiveSessionsChangedListener(sessionsChanged) }
            handler.post { unbind() }
        }
    }.distinctUntilChanged()

    fun playPause(state: MediaState) {
        runCatching {
            if (state.playing) state.controller.transportControls.pause()
            else state.controller.transportControls.play()
        }
    }

    fun next(state: MediaState) {
        runCatching { state.controller.transportControls.skipToNext() }
    }

    fun previous(state: MediaState) {
        runCatching { state.controller.transportControls.skipToPrevious() }
    }
}

private fun MediaController.toState(): MediaState? = runCatching {
    val metadata = metadata
    MediaState(
        packageName = packageName,
        title = metadata?.text(MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
        artist = metadata?.text(
            MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
        ),
        playing = playbackState?.state == PlaybackState.STATE_PLAYING,
        controller = this,
    )
}.getOrNull()

/** Primeiro campo preenchido entre os que o app pode ter usado. */
private fun MediaMetadata.text(vararg keys: String): String? = keys
    .firstNotNullOfOrNull { getText(it)?.toString()?.trim()?.takeIf { value -> value.isNotEmpty() } }
