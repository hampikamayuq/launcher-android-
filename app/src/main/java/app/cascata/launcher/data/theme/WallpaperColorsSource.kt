package app.cascata.launcher.data.theme

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * A cor dominante do papel de parede, quando o aparelho conta. Tudo aqui vai em
 * `runCatching`: em alguns aparelhos (e em perfis sem wallpaper próprio) essas
 * chamadas lançam, e nenhuma delas vale derrubar a tela inicial.
 */
class WallpaperColorsSource(context: Context) {

    private val manager: WallpaperManager? =
        runCatching { WallpaperManager.getInstance(context) }.getOrNull()

    /** Cor primária do wallpaper do sistema, ou null abaixo do Android 8.1 e quando não há. */
    fun primaryArgb(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        return readPrimary()
    }

    /** Emite a cada troca de papel de parede; nada abaixo do Android 8.1. */
    fun changes(): Flow<Unit> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) colorChanges() else emptyFlow()

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun readPrimary(): Int? = runCatching {
        manager?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
    }.getOrNull()

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun colorChanges(): Flow<Unit> = callbackFlow {
        // O listener é chamado na thread do Handler; a main serve, o trabalho é trySend.
        val listener = WallpaperManager.OnColorsChangedListener { _, _ -> trySend(Unit) }
        val wallpapers = manager
        val registered = wallpapers != null && runCatching {
            wallpapers.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        }.isSuccess
        // awaitClose é obrigatório mesmo quando não deu para registrar: sem ele o
        // callbackFlow lança em vez de simplesmente não emitir nada.
        awaitClose {
            if (registered) runCatching { wallpapers?.removeOnColorsChangedListener(listener) }
        }
    }
}
