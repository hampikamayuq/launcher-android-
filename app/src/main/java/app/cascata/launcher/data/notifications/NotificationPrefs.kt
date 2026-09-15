package app.cascata.launcher.data.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.cascata.launcher.crash.catchEmitting
import app.cascata.launcher.crash.degraded
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/** Como o app marca que tem notificação: o número delas ou só um ponto. */
enum class BadgeStyle { COUNT, DOT }

/**
 * Preferências do recurso — e só elas. Nenhum campo aqui descreve uma
 * notificação: [mutedPackages] guarda nomes de pacote, que o launcher já
 * enxerga por ser launcher.
 */
@Serializable
data class NotificationSettings(
    /** A intenção do usuário. O acesso de verdade é do sistema — ver `NotificationAccess`. */
    val enabled: Boolean = false,
    val badgeStyle: BadgeStyle = BadgeStyle.COUNT,
    val expandInline: Boolean = true,
    val mutedPackages: Set<String> = emptySet(),
    val showMedia: Boolean = false,
) {
    companion object {
        val DEFAULT = NotificationSettings()
    }
}

private val ENABLED = booleanPreferencesKey("enabled")
private val BADGE_STYLE = stringPreferencesKey("badge_style")
private val EXPAND_INLINE = booleanPreferencesKey("expand_inline")
private val MUTED_PACKAGES = stringSetPreferencesKey("muted_packages")
private val SHOW_MEDIA = booleanPreferencesKey("show_media")

/** Tag dos avisos deste arquivo. */
private const val TAG = "CascataPrefs"

/** Arquivo próprio, como o dos cards: silenciar um app não mexe em mais nada. */
private val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(name = "cascata_notifications")

class NotificationPrefs(private val context: Context) {

    // Um DataStore pode lançar ao ler: arquivo corrompido por um desligamento no
    // meio da escrita (IOException) ou uma chave gravada com outro tipo por uma
    // versão anterior (ClassCastException). Quem coleta é a composição — sem
    // isto, uma preferência ilegível derruba a tela; com isto, ela volta ao
    // padrão. O segundo `catch` cobre a leitura das chaves, que é onde o tipo
    // errado aparece.
    val settings: Flow<NotificationSettings> = context.notificationDataStore.data
        .catch { error ->
            degraded(TAG, "preferências de notificação ilegíveis", error)
            emit(emptyPreferences())
        }
        .map { it.toSettings() }
        .distinctUntilChanged()
        .catchEmitting(TAG, "preferências de notificação ilegíveis", NotificationSettings.DEFAULT)

    /** Lê, transforma e grava numa transação só: dois toques seguidos não se perdem. */
    suspend fun update(transform: (NotificationSettings) -> NotificationSettings) {
        context.notificationDataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    suspend fun setMuted(packageName: String, muted: Boolean) {
        update { current ->
            val next = if (muted) current.mutedPackages + packageName else current.mutedPackages - packageName
            current.copy(mutedPackages = next)
        }
    }

    /** Grava tudo de uma vez, sem olhar o que havia — é o que a restauração precisa. */
    suspend fun replace(settings: NotificationSettings) {
        context.notificationDataStore.edit { prefs -> prefs.write(settings) }
    }

    /** Limpa as chaves: a leitura volta a devolver os defaults (recurso desligado). */
    suspend fun reset() {
        context.notificationDataStore.edit { it.clear() }
    }
}

private fun Preferences.toSettings(): NotificationSettings = NotificationSettings(
    enabled = this[ENABLED] ?: NotificationSettings.DEFAULT.enabled,
    badgeStyle = enumOr(this[BADGE_STYLE], NotificationSettings.DEFAULT.badgeStyle),
    expandInline = this[EXPAND_INLINE] ?: NotificationSettings.DEFAULT.expandInline,
    mutedPackages = this[MUTED_PACKAGES] ?: NotificationSettings.DEFAULT.mutedPackages,
    showMedia = this[SHOW_MEDIA] ?: NotificationSettings.DEFAULT.showMedia,
)

private fun MutablePreferences.write(settings: NotificationSettings) {
    this[ENABLED] = settings.enabled
    this[BADGE_STYLE] = settings.badgeStyle.name
    this[EXPAND_INLINE] = settings.expandInline
    this[MUTED_PACKAGES] = settings.mutedPackages
    this[SHOW_MEDIA] = settings.showMedia
}

/** Enum gravado pelo `name`; valor desconhecido cai no default. */
private inline fun <reified T : Enum<T>> enumOr(raw: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: default
