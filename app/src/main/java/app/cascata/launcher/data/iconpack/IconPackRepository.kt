package app.cascata.launcher.data.iconpack

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.ConcurrentHashMap

/** Um pacote de ícones instalado. */
data class IconPackInfo(val packageName: String, val label: String)

/**
 * As três ações que os pacotes de ícones declaram há anos. É convenção pública
 * do formato aberto (`appfilter.xml`), não código de ninguém — um pacote costuma
 * declarar as três, daí a deduplicação por pacote.
 */
private val ICON_PACK_ACTIONS = listOf(
    "org.adw.launcher.THEMES",
    "com.novalauncher.THEME",
    "com.gau.go.launcherex.theme",
)

/**
 * Pacotes de ícones no formato aberto. O `appfilter.xml` de cada pacote é lido
 * uma vez e fica em memória: são milhares de linhas e a lista de apps pede ícone
 * item a item enquanto rola.
 */
class IconPackRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    /** pacote -> ("pacote/classe" -> nome do drawable). Mapa vazio = pacote sem appfilter legível. */
    private val filters = ConcurrentHashMap<String, Map<String, String>>()

    /** pacote -> Resources dele. Abrir os recursos de outro app não é barato. */
    private val resources = ConcurrentHashMap<String, Resources>()

    /** Instalados, sem repetição e em ordem de rótulo — é assim que vão para a tela. */
    fun installedPacks(): List<IconPackInfo> {
        val found = LinkedHashMap<String, IconPackInfo>()
        for (action in ICON_PACK_ACTIONS) {
            val resolved = runCatching { queryActivities(Intent(action)) }.getOrDefault(emptyList())
            for (info in resolved) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (found.containsKey(pkg)) continue
                val label = runCatching { info.loadLabel(packageManager).toString() }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: pkg
                found[pkg] = IconPackInfo(pkg, label)
            }
        }
        return found.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /** O ícone que [packageName] dá a [component], ou null se o pacote não cobre esse app. */
    suspend fun icon(packageName: String, component: ComponentName): Drawable? =
        withContext(Dispatchers.IO) {
            val name = filterOf(packageName)["${component.packageName}/${component.className}"]
                ?: return@withContext null
            drawableNamed(packageName, name)
        }

    /** Esquece appfilter e recursos: o pacote mudou, foi removido ou o usuário trocou de pacote. */
    fun invalidate() {
        filters.clear()
        resources.clear()
    }

    private fun filterOf(packageName: String): Map<String, String> =
        filters.getOrPut(packageName) { loadFilter(packageName) }

    private fun loadFilter(packageName: String): Map<String, String> {
        val res = resourcesOf(packageName) ?: return emptyMap()
        val items = readItems(res, packageName) ?: return emptyMap()
        return parseAppFilterEntries(items.asSequence())
    }

    /**
     * O appfilter vem como recurso XML (o comum) ou como asset solto (pacotes
     * antigos). Os dois dão o mesmo `XmlPullParser`, então só a origem muda.
     */
    private fun readItems(res: Resources, packageName: String): List<Pair<String, String>>? {
        @Suppress("DiscouragedApi") // Não há R do outro app: só o nome do recurso resolve.
        val xmlId = runCatching { res.getIdentifier("appfilter", "xml", packageName) }.getOrDefault(0)
        if (xmlId != 0) {
            val parser: XmlResourceParser = runCatching { res.getXml(xmlId) }.getOrNull() ?: return null
            return try {
                runCatching { readItems(parser) }.getOrNull()
            } finally {
                parser.close()
            }
        }
        return runCatching {
            res.assets.open("appfilter.xml").use { stream ->
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(stream, null)
                readItems(parser)
            }
        }.getOrNull()
    }

    private fun readItems(parser: XmlPullParser): List<Pair<String, String>> {
        val items = ArrayList<Pair<String, String>>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (component != null && drawable != null) items += component to drawable
            }
            event = parser.next()
        }
        return items
    }

    private fun drawableNamed(packageName: String, name: String): Drawable? {
        val res = resourcesOf(packageName) ?: return null
        @Suppress("DiscouragedApi") // Idem: o appfilter aponta o drawable pelo nome.
        val id = runCatching { res.getIdentifier(name, "drawable", packageName) }.getOrDefault(0)
        if (id == 0) return null
        return runCatching { ResourcesCompat.getDrawable(res, id, null) }.getOrNull()
    }

    private fun resourcesOf(packageName: String): Resources? {
        resources[packageName]?.let { return it }
        val res = runCatching { packageManager.getResourcesForApplication(packageName) }.getOrNull()
        return res?.also { resources[packageName] = it }
    }

    private fun queryActivities(intent: Intent) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
}
