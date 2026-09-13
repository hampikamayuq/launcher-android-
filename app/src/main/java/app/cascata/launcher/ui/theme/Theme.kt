package app.cascata.launcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E5AAC),
    onPrimary = Color.White,
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF14181F),
    onSurfaceVariant = Color(0xFF4A5260),
    surfaceVariant = Color(0xFFDDE3ED),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9EC1FF),
    onPrimary = Color(0xFF0E1A2B),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFECF0F7),
    onSurfaceVariant = Color(0xFFB3BCCB),
    surfaceVariant = Color(0xFF2A303A),
)

/**
 * Material You quando o aparelho oferece (Android 12+), paleta própria caso
 * contrário — um launcher deve seguir o tema do sistema, não impor o seu.
 */
@Composable
fun CascataTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
