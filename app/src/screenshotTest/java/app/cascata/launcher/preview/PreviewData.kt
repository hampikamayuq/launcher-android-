package app.cascata.launcher.preview

import android.content.ComponentName
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Process
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLocale
import app.cascata.launcher.Row
import app.cascata.launcher.data.AppEntry
import app.cascata.launcher.data.IconSource
import app.cascata.launcher.data.LoadedIcon
import app.cascata.launcher.data.appEntry
import app.cascata.launcher.data.notifications.AppNotification
import app.cascata.launcher.data.notifications.NotificationAction
import app.cascata.launcher.data.search.Contact
import app.cascata.launcher.data.usage.AppUsage
import java.math.BigDecimal
import java.util.Calendar
import java.util.Date

/**
 * Os dados das prévias de loja. Tudo aqui é fictício e genérico — nenhum nome de
 * marca, nenhum contato real — e vive só no source set `screenshotTest`: nada
 * disto entra em APK nenhum.
 *
 * O idioma não vem de `strings.xml` porque estes textos não são do app: são o
 * conteúdo de exemplo da foto. Cada `@Preview` fixa um locale, e é ele que
 * escolhe a lista abaixo.
 */
internal class Demo(
    /** Rótulos dos apps da gaveta, já na ordem em que aparecem. */
    val apps: List<String>,
    val favorites: List<String>,
    val searchQuery: String,
    val alarmChip: String,
    val calendarChip: String,
    val calendarTime: String,
    val weatherChip: String,
    val weatherPlace: String,
    val notificationApp: String,
    val notifications: List<DemoNotification>,
    val contacts: List<String>,
    val replyHint: String,
)

internal class DemoNotification(
    val title: String,
    val text: String,
    val minutesAgo: Int,
    val actions: List<String> = emptyList(),
    val reply: Boolean = false,
)

private val PT = Demo(
    apps = listOf(
        "Agenda", "Arquivos", "Bloco de Notas", "Calculadora", "Câmera",
        "Fotos", "Mapas", "Mensagens", "Música", "Navegador",
        "Podcasts", "Rádio", "Relógio", "Tarefas", "Vídeos",
    ),
    favorites = listOf("Mensagens", "Navegador", "Câmera", "Música", "Agenda"),
    searchQuery = "12*7",
    alarmChip = "06:30",
    calendarChip = "Consulta",
    calendarTime = "11:00",
    weatherChip = "24°",
    weatherPlace = "Centro",
    notificationApp = "Mensagens",
    notifications = listOf(
        DemoNotification(
            title = "Helena",
            text = "Consegue passar na padaria na volta?",
            minutesAgo = 3,
            reply = true,
        ),
        DemoNotification(
            title = "Grupo da casa",
            text = "Marcelo: a entrega chega hoje à tarde",
            minutesAgo = 18,
            actions = listOf("Marcar como lida"),
        ),
    ),
    contacts = listOf("Helena Prado", "Marcelo Dias"),
    replyHint = "Responder",
)

private val EN = Demo(
    apps = listOf(
        "Browser", "Calculator", "Calendar", "Camera", "Clock",
        "Files", "Maps", "Messages", "Music", "Notepad",
        "Photos", "Podcasts", "Radio", "Tasks", "Videos",
    ),
    favorites = listOf("Messages", "Browser", "Camera", "Music", "Calendar"),
    searchQuery = "12*7",
    alarmChip = "6:30 AM",
    calendarChip = "Check-up",
    calendarTime = "11:00 AM",
    weatherChip = "24°",
    weatherPlace = "Downtown",
    notificationApp = "Messages",
    notifications = listOf(
        DemoNotification(
            title = "Helen",
            text = "Can you stop by the bakery on your way back?",
            minutesAgo = 3,
            reply = true,
        ),
        DemoNotification(
            title = "House group",
            text = "Marcus: the delivery arrives this afternoon",
            minutesAgo = 18,
            actions = listOf("Mark as read"),
        ),
    ),
    contacts = listOf("Helen Prado", "Marcus Dias"),
    replyHint = "Reply",
)

private val ES = Demo(
    apps = listOf(
        "Agenda", "Archivos", "Bloc de Notas", "Calculadora", "Cámara",
        "Fotos", "Mapas", "Mensajes", "Música", "Navegador",
        "Pódcasts", "Radio", "Reloj", "Tareas", "Vídeos",
    ),
    favorites = listOf("Mensajes", "Navegador", "Cámara", "Música", "Agenda"),
    searchQuery = "12*7",
    alarmChip = "06:30",
    calendarChip = "Consulta",
    calendarTime = "11:00",
    weatherChip = "24°",
    weatherPlace = "Centro",
    notificationApp = "Mensajes",
    notifications = listOf(
        DemoNotification(
            title = "Elena",
            text = "¿Puedes pasar por la panadería a la vuelta?",
            minutesAgo = 3,
            reply = true,
        ),
        DemoNotification(
            title = "Grupo de casa",
            text = "Marcelo: el envío llega esta tarde",
            minutesAgo = 18,
            actions = listOf("Marcar como leída"),
        ),
    ),
    contacts = listOf("Elena Prado", "Marcelo Díaz"),
    replyHint = "Responder",
)

/** A lista que combina com o locale desta prévia. */
@Composable
internal fun demo(): Demo = when (LocalLocale.current.platformLocale.language) {
    "en" -> EN
    "es" -> ES
    else -> PT
}

/** Instante fixo das prévias: sem isto o relógio mudaria a cada renderização. */
internal val DEMO_NOW: Date = Calendar.getInstance()
    .apply { set(2026, Calendar.FEBRUARY, 12, 9, 41, 0); set(Calendar.MILLISECOND, 0) }
    .time

/** Nome de pacote fictício a partir do rótulo — é o que casa uso e notificações. */
internal fun demoPackage(label: String): String =
    "app.exemplo." + label.lowercase()
        .map { if (it in 'a'..'z' || it in '0'..'9') it else if (it == ' ') '.' else 'x' }
        .joinToString("")

internal fun demoEntry(label: String): AppEntry = appEntry(
    component = ComponentName(demoPackage(label), demoPackage(label) + ".Main"),
    user = Process.myUserHandle(),
    originalLabel = label,
)

/** As linhas da gaveta como a home as monta: cabeçalho de letra e apps. */
internal fun demoRows(apps: List<AppEntry>, favorites: Set<String>): List<Row<AppEntry>> {
    val rows = mutableListOf<Row<AppEntry>>()
    var last: Char? = null
    for (app in apps) {
        if (app.section != last) {
            rows += Row.Header(app.section)
            last = app.section
        }
        rows += Row.App(app, app.label in favorites)
    }
    return rows
}

/** As letras do índice lateral, na ordem das seções. */
internal fun demoLetters(apps: List<AppEntry>): List<Char> = apps.map { it.section }.distinct()

internal fun demoNotifications(demo: Demo): List<AppNotification> {
    val now = System.currentTimeMillis()
    return demo.notifications.mapIndexed { index, item ->
        AppNotification(
            key = "demo-$index",
            packageName = demoPackage(demo.notificationApp),
            user = Process.myUserHandle(),
            postTimeMillis = now - item.minutesAgo * 60_000L,
            title = item.title,
            text = item.text,
            subText = null,
            isGroupSummary = false,
            groupKey = null,
            actions = item.actions.map {
                NotificationAction(title = it, actionIntent = null, remoteInputKey = null, remoteInputLabel = null)
            } + if (item.reply) {
                listOf(
                    NotificationAction(
                        title = demo.replyHint,
                        actionIntent = null,
                        remoteInputKey = "reply",
                        remoteInputLabel = demo.replyHint,
                    ),
                )
            } else {
                emptyList()
            },
            contentIntent = null,
            isClearable = true,
            isOngoing = false,
            importance = null,
        )
    }
}

/** Uso do dia: os cinco primeiros da gaveta, em minutos decrescentes. */
internal fun demoUsage(demo: Demo): List<AppUsage> {
    val minutes = listOf(74L, 46L, 31L, 22L, 12L)
    return demo.favorites.take(minutes.size).mapIndexed { index, label ->
        AppUsage(
            packageName = demoPackage(label),
            totalMillis = minutes[index] * 60_000L,
            lastUsedMillis = 0L,
        )
    }
}

internal fun demoContacts(demo: Demo): List<Contact> = demo.contacts.mapIndexed { index, name ->
    Contact(
        id = index.toLong(),
        lookupKey = "demo-$index",
        name = name,
        phone = "+55 11 90000-000$index",
        photoUri = null,
    )
}

internal val DEMO_CALCULATION = app.cascata.launcher.data.search.CalculationResult(
    expression = "12*7",
    value = BigDecimal("84"),
    formatted = "84",
)

/** Doze cores, uma por app, sempre a mesma para o mesmo rótulo. */
private val ICON_COLORS = listOf(
    0xFF4C6FBF, 0xFF2F9E6E, 0xFFCC7A2B, 0xFF9A5BC4,
    0xFFC9525A, 0xFF2E8FA8, 0xFF7A8B3C, 0xFFB4526E,
    0xFF5B6BA8, 0xFF3F9E8C, 0xFFA9762E, 0xFF6E6EAA,
).map { it.toInt() }

/**
 * Ícones fictícios: um disco de duas tonalidades por app. Não há LauncherApps
 * aqui — é exatamente por isso que os composables pedem [IconSource] e não o
 * repositório inteiro.
 */
internal object DemoIcons : IconSource {

    override suspend fun icon(entry: AppEntry): LoadedIcon =
        LoadedIcon(disc(colorOf(entry.label)), fromPack = true)

    /** Nenhuma prévia mostra atalhos: eles exigiriam um `ShortcutInfo` de verdade. */
    override suspend fun shortcutIcon(shortcut: ShortcutInfo): Drawable? = null

    /**
     * A cor sai da posição do app na lista do idioma, não de um hash: assim dois
     * vizinhos nunca saem iguais, e o mesmo app tem a mesma cor em todas as fotos.
     */
    private val byLabel: Map<String, Int> = buildMap {
        listOf(PT, EN, ES).forEach { demo ->
            demo.apps.forEachIndexed { index, label -> put(label, ICON_COLORS[index % ICON_COLORS.size]) }
        }
    }

    private fun colorOf(label: String): Int = byLabel[label]
        ?: ICON_COLORS[(label.hashCode() and Int.MAX_VALUE) % ICON_COLORS.size]

    private fun disc(argb: Int): Drawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(lighten(argb), argb),
    ).apply {
        shape = GradientDrawable.OVAL
    }

    /** Clareia a cor para o alto do disco — o degradê dá volume ao círculo chapado. */
    private fun lighten(argb: Int): Int {
        fun channel(shift: Int): Int {
            val value = (argb shr shift) and 0xFF
            return (value + (255 - value) * 35 / 100) and 0xFF
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
