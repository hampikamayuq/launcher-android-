package app.cascata.launcher.data.search

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import app.cascata.launcher.R
import app.cascata.launcher.data.normalizeLabel
import app.cascata.launcher.matchesQuery
import app.cascata.launcher.queryRank
import java.util.concurrent.ConcurrentHashMap

/**
 * Uma tela das configurações do sistema. O rótulo é recurso (traduz junto com o
 * resto); as [keywords] são sinônimos sem acento, casados como o rótulo.
 */
data class SettingEntry(
    val id: String,
    @param:StringRes val labelRes: Int,
    val keywords: String,
    val action: String,
)

/** A entrada com o rótulo já lido: deixa o filtro puro, testável sem Context. */
data class LabeledSetting(val entry: SettingEntry, val label: String)

/** Rótulo casado vem antes de palavra-chave casada — o nome é o que o usuário vê. */
private const val KEYWORD_RANK = 3

/**
 * Filtro puro: casa no rótulo normalizado ou em qualquer palavra-chave, com a
 * mesma regra (e a mesma tolerância a erro) da lista de apps.
 */
fun filterSettings(
    items: List<LabeledSetting>,
    needle: String,
    limit: Int = 4,
): List<SettingEntry> {
    val n = normalizeLabel(needle)
    if (n.isEmpty()) return emptyList()
    return items
        .mapNotNull { item ->
            val label = normalizeLabel(item.label)
            val rank = when {
                matchesQuery(label, n) -> queryRank(label, n)
                item.entry.keywords.split(' ').any { it.isNotEmpty() && matchesQuery(it, n) } -> KEYWORD_RANK
                else -> return@mapNotNull null
            }
            Triple(rank, label, item.entry)
        }
        .sortedWith(compareBy({ it.first }, { it.second }))
        .take(limit)
        .map { it.third }
}

/**
 * Índice estático das telas do sistema. Todas as constantes usadas existem desde
 * a API 26 (a menor que o app aceita), então não há nada a proteger por versão —
 * o que varia é o aparelho *ter* a tela, e disso cuida o `resolveActivity`.
 */
object SystemSettingsIndex {

    val entries: List<SettingEntry> = listOf(
        SettingEntry("wifi", R.string.setting_wifi, "wifi wi fi rede sem fio wireless internet", Settings.ACTION_WIFI_SETTINGS),
        SettingEntry("bluetooth", R.string.setting_bluetooth, "bluetooth fone pareamento parear", Settings.ACTION_BLUETOOTH_SETTINGS),
        SettingEntry("display", R.string.setting_display, "tela display brilho rotacao suspensao papel de parede", Settings.ACTION_DISPLAY_SETTINGS),
        SettingEntry("sound", R.string.setting_sound, "som audio volume toque vibracao silencioso", Settings.ACTION_SOUND_SETTINGS),
        SettingEntry("battery", R.string.setting_battery, "bateria energia carga consumo economia", Intent.ACTION_POWER_USAGE_SUMMARY),
        SettingEntry("apps", R.string.setting_apps, "apps aplicativos programas gerenciar desinstalar", Settings.ACTION_APPLICATION_SETTINGS),
        SettingEntry("storage", R.string.setting_storage, "armazenamento espaco memoria disco cartao", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        SettingEntry("datetime", R.string.setting_datetime, "data hora fuso horario relogio", Settings.ACTION_DATE_SETTINGS),
        SettingEntry("languages", R.string.setting_languages, "idioma idiomas lingua teclado entrada", Settings.ACTION_LOCALE_SETTINGS),
        SettingEntry("accessibility", R.string.setting_accessibility, "acessibilidade talkback leitor de tela legendas", Settings.ACTION_ACCESSIBILITY_SETTINGS),
        SettingEntry("security", R.string.setting_security, "seguranca bloqueio senha pin digital biometria", Settings.ACTION_SECURITY_SETTINGS),
        SettingEntry("location", R.string.setting_location, "localizacao gps local mapa", Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        // Não há constante pública para a tela geral de notificações; esta ação é
        // a que o AOSP usa há anos, e o resolveActivity tira a entrada de quem não a tiver.
        SettingEntry("notifications", R.string.setting_notifications, "notificacoes avisos alertas nao perturbe", "android.settings.NOTIFICATION_SETTINGS"),
        SettingEntry("mobile", R.string.setting_mobile_network, "rede movel dados celular chip operadora roaming", Settings.ACTION_DATA_ROAMING_SETTINGS),
        SettingEntry("airplane", R.string.setting_airplane, "modo aviao voo offline", Settings.ACTION_AIRPLANE_MODE_SETTINGS),
        SettingEntry("accounts", R.string.setting_accounts, "contas conta sincronizacao login email", Settings.ACTION_SYNC_SETTINGS),
        SettingEntry("about", R.string.setting_about, "sobre telefone aparelho versao android imei", Settings.ACTION_DEVICE_INFO_SETTINGS),
        SettingEntry("home", R.string.setting_home, "tela inicial launcher home padrao", Settings.ACTION_HOME_SETTINGS),
    )

    /**
     * Resolver um intent custa uma ida ao PackageManager; o resultado não muda
     * enquanto o processo vive, então fica em cache por ação.
     */
    private val resolvable = ConcurrentHashMap<String, Boolean>()

    /** Só devolve telas que este aparelho realmente tem. */
    fun search(context: Context, needle: String, limit: Int = 4): List<SettingEntry> {
        val available = entries
            .filter { resolves(context, it.action) }
            .map { LabeledSetting(it, context.getString(it.labelRes)) }
        return filterSettings(available, needle, limit)
    }

    private fun resolves(context: Context, action: String): Boolean =
        resolvable.getOrPut(action) {
            runCatching {
                Intent(action).resolveActivity(context.packageManager) != null
            }.getOrDefault(false)
        }
}
