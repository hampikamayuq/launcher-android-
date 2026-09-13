package app.cascata.launcher.data

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.util.LruCache
import androidx.annotation.RequiresApi
import app.cascata.launcher.data.iconpack.IconPackRepository
import app.cascata.launcher.data.theme.ThemePrefs
import app.cascata.launcher.matchesQuery
import app.cascata.launcher.queryRank
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Collator

/** No máximo isso de atalhos por app: o menu de contexto não pode virar uma lista. */
private const val MAX_SHORTCUTS = 6

/** Um ícone pronto para desenhar, mais de onde ele veio. */
data class LoadedIcon(val drawable: Drawable, val fromPack: Boolean)

/**
 * Um atalho encontrado na busca, com o app dono dele quando dá para resolvê-lo.
 * O rótulo do app aqui é o do sistema, sem apelido: o índice é do repositório e
 * não enxerga o DataStore de apelidos.
 */
data class ShortcutMatch(val shortcut: ShortcutInfo, val app: AppEntry?)

/** O que o índice guarda de cada atalho: o resultado pronto e o rótulo normalizado. */
private class IndexedShortcut(val match: ShortcutMatch, val normalizedLabel: String)

/**
 * Fonte única da lista de apps. Lê via [LauncherApps] (todos os perfis do usuário)
 * e se re-emite sozinha quando algo é instalado, removido ou atualizado.
 */
class AppRepository(
    private val context: Context,
    scope: CoroutineScope,
    private val iconPacks: IconPackRepository,
    private val themePrefs: ThemePrefs,
) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    /** Ícones são caros: mantemos os mais recentes em memória, o resto recarrega sob demanda. */
    private val iconCache = LruCache<String, LoadedIcon>(200)

    /** Poucos atalhos ficam na tela de cada vez; um cache grande aqui seria desperdício. */
    private val shortcutIconCache = LruCache<String, Drawable>(32)

    val apps: Flow<List<AppEntry>> = packageChanges()
        .conflate()
        .map {
            // Instalar, remover ou atualizar um pacote muda também os atalhos
            // dele: o índice da busca cai junto e se refaz na próxima consulta.
            shortcutIndex = null
            loadApps()
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Trocar de pacote de ícones invalida *todos* os ícones de uma vez, e é
        // raro. Limpar o cache sai mais barato que carregar a chave com o nome do
        // pacote e guardar dois conjuntos de bitmaps na memória.
        scope.launch {
            themePrefs.settings
                .map { it.iconPack }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    iconCache.evictAll()
                    iconPacks.invalidate()
                }
        }
    }

    private suspend fun loadApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        val profiles = userManager?.userProfiles ?: listOf(Process.myUserHandle())
        profiles.flatMap { user ->
            val private = isPrivateProfile(user)
            runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
                .map { info ->
                    appEntry(
                        component = info.componentName,
                        user = user,
                        originalLabel = info.label?.toString().orEmpty()
                            .ifEmpty { info.componentName.packageName },
                        isPrivateProfile = private,
                    )
                }
        }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    /**
     * Perfil privado existe a partir do Android 15 e só aparece aqui quando está
     * desbloqueado — a permissão ACCESS_HIDDEN_PROFILES é que nos deixa vê-lo.
     */
    private fun isPrivateProfile(user: UserHandle): Boolean {
        if (Build.VERSION.SDK_INT < 35) return false
        return runCatching {
            launcherApps.getLauncherUserInfo(user)?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE
        }.getOrDefault(false)
    }

    /**
     * Carrega (e memoriza) o ícone de um app: primeiro o pacote de ícones ativo,
     * e o do sistema quando o pacote não tem esse app. Chame fora da main thread.
     *
     * O ícone do pacote não leva o badge de perfil de trabalho — quem tem o badge
     * é o drawable do sistema —, e é o preço de usar o pacote escolhido.
     */
    suspend fun icon(entry: AppEntry): LoadedIcon? = withContext(Dispatchers.IO) {
        iconCache[entry.key]?.let { return@withContext it }
        // Uma leitura por ícone: o DataStore mantém o valor em memória depois da
        // primeira, e assim não há corrida entre o cache e a preferência chegando.
        val pack = themePrefs.settings.first().iconPack
        val fromPack = pack?.let { runCatching { iconPacks.icon(it, entry.component) }.getOrNull() }
        if (fromPack != null) {
            return@withContext LoadedIcon(fromPack, fromPack = true)
                .also { iconCache.put(entry.key, it) }
        }
        val info = runCatching {
            launcherApps.getActivityList(entry.component.packageName, entry.user)
                .firstOrNull { it.componentName == entry.component }
        }.getOrNull() ?: return@withContext null
        val density = context.resources.displayMetrics.densityDpi
        val drawable = runCatching { info.getBadgedIcon(density) }.getOrNull()
            ?: return@withContext null
        LoadedIcon(drawable, fromPack = false).also { iconCache.put(entry.key, it) }
    }

    fun launch(entry: AppEntry, sourceBounds: android.graphics.Rect? = null) {
        runCatching { launcherApps.startMainActivity(entry.component, entry.user, sourceBounds, null) }
    }

    fun openAppInfo(entry: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(entry.component, entry.user, null, null) }
    }

    /** Atalhos só são visíveis para o launcher padrão; sem isso, a lista vem vazia. */
    fun hasShortcutHostPermission(): Boolean =
        runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    /** Atalhos declarados no manifesto, dinâmicos e fixados, na ordem de rank do app. */
    suspend fun shortcuts(entry: AppEntry): List<ShortcutInfo> = withContext(Dispatchers.IO) {
        if (!hasShortcutHostPermission()) return@withContext emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(entry.component.packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        runCatching { launcherApps.getShortcuts(query, entry.user) }
            .getOrNull()
            .orEmpty()
            .sortedBy { it.rank }
            .take(MAX_SHORTCUTS)
    }

    /**
     * Índice de todos os atalhos visíveis, de todos os perfis. Carregado na
     * primeira busca e reusado: uma consulta por perfil custa bem menos que uma
     * por pacote, e a lista só muda quando um pacote muda.
     */
    @Volatile
    private var shortcutIndex: List<IndexedShortcut>? = null

    /** Duas teclas seguidas não podem disparar duas cargas do índice. */
    private val shortcutIndexLock = Mutex()

    /**
     * Atalhos que casam com o termo, para a busca. Sem permissão de host não há
     * nem consulta — e o índice é descartado, para que virar launcher padrão
     * (false -> true) o reconstrua em vez de devolver o vazio de antes.
     */
    suspend fun searchShortcuts(needle: String, limit: Int = 8): List<ShortcutMatch> {
        val n = normalizeLabel(needle)
        if (n.isEmpty()) return emptyList()
        if (!hasShortcutHostPermission()) {
            shortcutIndex = null
            return emptyList()
        }
        return shortcutIndexOrLoad()
            .filter { matchesQuery(it.normalizedLabel, n) }
            .sortedWith(compareBy({ queryRank(it.normalizedLabel, n) }, { it.normalizedLabel }))
            .take(limit)
            .map { it.match }
    }

    private suspend fun shortcutIndexOrLoad(): List<IndexedShortcut> = shortcutIndexLock.withLock {
        shortcutIndex ?: loadShortcutIndex().also { shortcutIndex = it }
    }

    private suspend fun loadShortcutIndex(): List<IndexedShortcut> = withContext(Dispatchers.IO) {
        // Sem setPackage: uma consulta por perfil traz tudo o que o sistema deixa
        // ver, em vez de uma consulta por app instalado.
        val query = LauncherApps.ShortcutQuery()
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        val profiles = userManager?.userProfiles ?: listOf(Process.myUserHandle())
        val owners = HashMap<String, AppEntry?>()
        profiles.flatMap { user ->
            runCatching { launcherApps.getShortcuts(query, user) }.getOrNull().orEmpty()
                .mapNotNull { shortcut ->
                    val label = (shortcut.shortLabel ?: shortcut.longLabel)?.toString().orEmpty()
                    if (label.isBlank()) return@mapNotNull null
                    val ownerKey = appKeyOf(shortcut.`package`, user.hashCode())
                    if (!owners.containsKey(ownerKey)) {
                        owners[ownerKey] = mainEntry(shortcut.`package`, user)
                    }
                    IndexedShortcut(
                        match = ShortcutMatch(shortcut, owners[ownerKey]),
                        normalizedLabel = normalizeLabel(label),
                    )
                }
        }
    }

    /** A entrada do app dono do atalho, para a UI mostrar de quem ele é. */
    private fun mainEntry(packageName: String, user: UserHandle): AppEntry? = runCatching {
        launcherApps.getActivityList(packageName, user).firstOrNull()?.let { info ->
            appEntry(
                component = info.componentName,
                user = user,
                originalLabel = info.label?.toString().orEmpty().ifEmpty { packageName },
                isPrivateProfile = isPrivateProfile(user),
            )
        }
    }.getOrNull()

    /** Ícone de um atalho, na densidade da tela. Chame fora da main thread. */
    suspend fun shortcutIcon(shortcut: ShortcutInfo): Drawable? = withContext(Dispatchers.IO) {
        val cacheKey = "${shortcut.`package`}#${shortcut.id}"
        shortcutIconCache[cacheKey]?.let { return@withContext it }
        val density = context.resources.displayMetrics.densityDpi
        val drawable = runCatching {
            launcherApps.getShortcutIconDrawable(shortcut, density)
        }.getOrNull()
        drawable?.also { shortcutIconCache.put(cacheKey, it) }
    }

    fun startShortcut(shortcut: ShortcutInfo) {
        runCatching { launcherApps.startShortcut(shortcut, null, null) }
    }

    /**
     * Desinstalar é um intent para o sistema — quem confirma é o usuário, e o app
     * continua sem pedir nenhuma permissão de instalação.
     */
    fun uninstall(entry: AppEntry) {
        runCatching {
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${entry.component.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Intent.EXTRA_USER, entry.user)
            context.startActivity(intent)
        }
    }

    fun isSystemApp(component: ComponentName, user: UserHandle): Boolean = runCatching {
        val info = launcherApps.getApplicationInfo(component.packageName, 0, user)
        info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
    }.getOrDefault(false)

    /** Somos a home do sistema? Atalhos e (mais tarde) widgets dependem disso. */
    fun isDefaultLauncher(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            roleManager()?.isRoleHeld(RoleManager.ROLE_HOME) == true
        } else {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = context.packageManager
                .resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            resolved?.activityInfo?.packageName == context.packageName
        }
    }.getOrDefault(false)

    /**
     * O diálogo do sistema para virar a home. Do Android 10 em diante é um pedido
     * de papel (um toque); antes disso só dá para levar às configurações de home.
     */
    fun requestDefaultLauncherIntent(): Intent? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = roleManager()
            if (roles != null &&
                roles.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roles.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                return@runCatching roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        Intent(Settings.ACTION_HOME_SETTINGS)
    }.getOrNull()

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun roleManager(): RoleManager? = context.getSystemService(RoleManager::class.java)

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
