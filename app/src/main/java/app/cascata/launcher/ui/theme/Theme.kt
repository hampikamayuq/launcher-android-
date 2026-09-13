package app.cascata.launcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cascata.launcher.data.theme.ColorSource
import app.cascata.launcher.data.theme.DarkMode
import app.cascata.launcher.data.theme.Density
import app.cascata.launcher.data.theme.SchemeColors
import app.cascata.launcher.data.theme.ThemeSettings
import app.cascata.launcher.data.theme.seedScheme
import java.io.File
import androidx.compose.ui.unit.Density as UiDensity

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
 * A densidade escolhida. Fica num local próprio porque quem precisa dela é a
 * lista — o [MaterialTheme] não tem onde guardar "altura de linha do launcher".
 */
val LocalLauncherDensity = staticCompositionLocalOf { Density.DEFAULT }

/** Alfa da superfície sobre o papel de parede, pelo mesmo motivo. */
val LocalBackgroundOpacity = staticCompositionLocalOf { ThemeSettings.DEFAULT.backgroundOpacity }

/** Espaço acima e abaixo do rótulo numa linha da lista. */
val Density.rowPadding: Dp
    get() = when (this) {
        Density.COMPACT -> 2.dp
        Density.DEFAULT -> 6.dp
        Density.COMFORTABLE -> 10.dp
    }

/** Lado do ícone na lista. Acompanha o espaçamento para a linha não ficar torta. */
val Density.iconSize: Dp
    get() = when (this) {
        Density.COMPACT -> 34.dp
        Density.DEFAULT -> 40.dp
        Density.COMFORTABLE -> 46.dp
    }

/**
 * O tema inteiro sai de [settings]: o que a tela de configurações grava chega
 * aqui pelo mesmo Flow que a home observa, e a aparência muda sem reiniciar.
 *
 * [customFont] e [wallpaperSeed] vêm de fora porque nenhum dos dois é uma
 * preferência: um é arquivo no disco, o outro é o papel de parede do sistema.
 */
@Composable
fun CascataTheme(
    settings: ThemeSettings = ThemeSettings.DEFAULT,
    customFont: File? = null,
    wallpaperSeed: Int? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = when (settings.darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }

    // Só a origem "sistema" não tem semente: as outras duas viram paleta calculada.
    val seed = when (settings.colorSource) {
        ColorSource.SYSTEM -> null
        ColorSource.WALLPAPER -> wallpaperSeed ?: settings.accentArgb
        ColorSource.ACCENT -> settings.accentArgb
    }

    val colors = when {
        // Derivar a paleta custa umas centenas de comparações de contraste; uma vez por semente basta.
        seed != null -> remember(seed, dark) { seedScheme(seed, dark).toColorScheme(dark) }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    val family = remember(settings.fontId, customFont) { Fonts.family(settings.fontId, customFont) }
    val typography = remember(family) {
        if (family == null) Typography() else Typography().withFamily(family)
    }

    MaterialTheme(colorScheme = colors, typography = typography) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            // A escala do usuário multiplica a do sistema em vez de substituí-la:
            // quem já aumentou a fonte nas configurações do Android não perde isso.
            LocalDensity provides UiDensity(base.density, base.fontScale * settings.fontScale),
            LocalLauncherDensity provides settings.density,
            LocalBackgroundOpacity provides settings.backgroundOpacity,
            content = content,
        )
    }
}

/**
 * Os campos que a semente sabe calcular; o resto fica no padrão do Material 3.
 * As duas chamadas são gêmeas porque `lightColorScheme` e `darkColorScheme` são
 * funções diferentes — com defaults diferentes para o que não passamos.
 */
private fun SchemeColors.toColorScheme(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(onPrimaryContainer),
        secondary = Color(secondary),
        onSecondary = Color(onSecondary),
        surface = Color(surface),
        onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant),
        onSurfaceVariant = Color(onSurfaceVariant),
        background = Color(background),
        onBackground = Color(onBackground),
        outline = Color(outline),
    )
} else {
    lightColorScheme(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(onPrimaryContainer),
        secondary = Color(secondary),
        onSecondary = Color(onSecondary),
        surface = Color(surface),
        onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant),
        onSurfaceVariant = Color(onSurfaceVariant),
        background = Color(background),
        onBackground = Color(onBackground),
        outline = Color(outline),
    )
}

/** A fonte escolhida em todos os estilos — a Typography não tem um "fontFamily" só. */
private fun Typography.withFamily(family: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)
