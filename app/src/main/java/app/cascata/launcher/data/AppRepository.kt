package app.cascata.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.text.Collator

/**
 * Fonte única da lista de apps. Lê via [LauncherApps] (todos os perfis do usuário)
 * e se re-emite sozinha quando algo é instalado, removido ou atualizado.
 */
class AppRepository(private val context: Context, scope: CoroutineScope) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    /** Ícones são caros: mantemos os mais recentes em memória, o resto recarrega sob demanda. */
    private val iconCache = LruCache<String, Drawable>(200)

    val apps: Flow<List<AppEntry>> = packageChanges()
        .conflate()
        .map { loadApps() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun loadApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        val profiles = userManager?.userProfiles ?: listOf(Process.myUserHandle())
        profiles.flatMap { user ->
            runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
                .map { info ->
                    val label = info.label?.toString().orEmpty().ifEmpty { info.componentName.packageName }
                    val normalized = normalizeLabel(label)
                    AppEntry(
                        component = info.componentName,
                        user = user,
                        label = label,
                        normalizedLabel = normalized,
                        section = sectionOf(normalized),
                    )
                }
        }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    /** Carrega (e memoriza) o ícone de um app. Chame fora da main thread. */
    suspend fun icon(entry: AppEntry): Drawable? = withContext(Dispatchers.IO) {
        iconCache[entry.key]?.let { return@withContext it }
        val info = runCatching {
            launcherApps.getActivityList(entry.component.packageName, entry.user)
                .firstOrNull { it.componentName == entry.component }
        }.getOrNull() ?: return@withContext null
        val density = context.resources.displayMetrics.densityDpi
        val drawable = runCatching { info.getBadgedIcon(density) }.getOrNull()
        drawable?.also { iconCache.put(entry.key, it) }
    }

    fun launch(entry: AppEntry, sourceBounds: android.graphics.Rect? = null) {
        runCatching { launcherApps.startMainActivity(entry.component, entry.user, sourceBounds, null) }
    }

    fun openAppInfo(entry: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(entry.component, entry.user, null, null) }
    }

    fun isSystemApp(component: ComponentName, user: UserHandle): Boolean = runCatching {
        val info = launcherApps.getApplicationInfo(component.packageName, 0, user)
        info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
    }.getOrDefault(false)

    /** Emite uma vez de saída e depois a cada mudança no conjunto de pacotes. */
    private fun packageChanges(): Flow<Unit> = callbackFlow {
        trySend(Unit)
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackageAdded(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackageChanged(packageName: String?, user: UserHandle?) { trySend(Unit) }
            override fun onPackagesAvailable(names: Array<out String>?, user: UserHandle?, replacing: Boolean) { trySend(Unit) }
            override fun onPackagesUnavailable(names: Array<out String>?, user: UserHandle?, replacing: Boolean) { trySend(Unit) }
        }
        launcherApps.registerCallback(callback)
        awaitClose { launcherApps.unregisterCallback(callback) }
    }
}
