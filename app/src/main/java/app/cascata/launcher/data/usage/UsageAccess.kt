package app.cascata.launcher.data.usage

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * `PACKAGE_USAGE_STATS` não é permissão de diálogo: é acesso especial, concedido
 * pelo usuário numa tela do sistema — como o de notificações. Por isso
 * `UsageSettings.enabled` é só a intenção; o que vale é [hasAccess], relido a
 * cada `onResume`.
 */
class UsageAccess(private val context: Context) {

    fun hasAccess(): Boolean = runCatching {
        val appOps = ContextCompat.getSystemService(context, AppOpsManager::class.java)
            ?: return@runCatching false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            // MODE_DEFAULT quer dizer "ninguém decidiu": quem responde é a permissão.
            AppOpsManager.MODE_DEFAULT -> ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.PACKAGE_USAGE_STATS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }.getOrDefault(false)

    /**
     * A tela do sistema onde o acesso é concedido. Do Android 11 em diante ela
     * aceita o pacote em `data` e já abre no Cascata; alguns aparelhos não
     * resolvem essa forma, e aí vai a lista inteira, que sempre existe.
     */
    fun settingsIntent(): Intent {
        val plain = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return plain
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        val resolves = runCatching { direct.resolveActivity(context.packageManager) != null }
            .getOrDefault(false)
        return if (resolves) direct else plain
    }
}
