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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
import app.cascata.launcher.data.theme.wallpaperInk
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

/**
 * `true` quando o texto está desenhado direto sobre o papel de parede, e não
 * sobre uma superfície do tema. A UI usa isto para o que a cor sozinha não
 * resolve — o ripple de um toque, uma borda que só existe para separar do fundo.
 */
val LocalOnWallpaper = staticCompositionLocalOf { false }

/**
 * Abaixo desta opacidade a superfície já não cobre nada: o texto está, na
 * prática, sobre a foto do papel de parede, e é a tinta de wallpaper que vale.
 */
const val ON_WALLPAPER_MAX_OPACITY = 0.25f

/**
 * Alfa do véu que separa um controle do papel de parede — os chips do glance, o
 * campo de busca. Pouco o bastante para a foto continuar aparecendo, o bastante
 * para o texto não disputar com ela.
 */
const val WALLPAPER_VEIL_ALPHA = 0.35f

/**
 * O esquema e a tipografia de antes da tinta de wallpaper, guardados para o que
 * tem superfície própria. Nulos fora de um [CascataTheme] — aí vale o que o
 * [MaterialTheme] já diz.
 */
val LocalSurfaceScheme = staticCompositionLocalOf<ColorScheme?> { null }
val LocalSurfaceTypography = staticCompositionLocalOf<Typography?> { null }

/** Tinta escura de wallpaper — a mesma `onSurface` do tema claro. */
private val WallpaperDarkInk = Color(0xFF14181F)

/**
 * Luminância mínima da cor de destaque sobre um fundo desconhecido e com texto
 * claro por perto. Abaixo disso ela some no papel de parede escuro.
 */
private const val MIN_ACCENT_LUMINANCE = 0.35f

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
 * [customFont], [wallpaperSeed] e [wallpaperDarkText] vêm de fora porque
 * nenhum dos três é uma preferência: um é arquivo no disco, os outros dois são
 * o papel de parede do sistema.
 *
 * [overWallpaper] distingue a home das telas que têm fundo próprio e opaco
 * (configurações, seletor de widgets, boas-vindas): a opacidade do fundo é uma
 * preferência da home, e só lá ela quer dizer "o texto vai cair sobre a foto".
 */
@Composable
fun CascataTheme(
    settings: ThemeSettings = ThemeSettings.DEFAULT,
    customFont: File? = null,
    wallpaperSeed: Int? = null,
    wallpaperDarkText: Boolean? = null,
    overWallpaper: Boolean = true,
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

    // Sobre o papel de parede: sem superfície por baixo, as cores "on…" do
    // esquema (calculadas contra uma superfície que não está lá) não valem.
    val onWallpaper = overWallpaper && settings.backgroundOpacity < ON_WALLPAPER_MAX_OPACITY
    val darkInk = wallpaperInk(settings.wallpaperText, wallpaperDarkText)
    val scheme = if (onWallpaper) colors.onWallpaper(darkInk) else colors

    // O `context` é o que deixa [Fonts.family] carregar a fonte aqui, fora da
    // composição: recusada pelo aparelho, ela vira null e o texto sai na fonte do
    // sistema, em vez de a home morrer ao medir a primeira letra.
    val family = remember(settings.fontId, customFont, context) {
        runCatching { Fonts.family(settings.fontId, customFont, context) }.getOrNull()
    }
    // A tipografia da superfície é a mesma, sem a sombra: ela existe para
    // descolar o texto da foto, e dentro de uma folha opaca só suja as letras.
    val surfaceTypography = remember(family) {
        if (family == null) Typography() else Typography().withFamily(family)
    }
    val shadow = if (onWallpaper && settings.textShadow) wallpaperShadow(darkInk) else null
    val typography = remember(surfaceTypography, shadow) {
        if (shadow == null) surfaceTypography else surfaceTypography.withShadow(shadow)
    }

    MaterialTheme(colorScheme = scheme, typography = typography) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            // A escala do usuário multiplica a do sistema em vez de substituí-la:
            // quem já aumentou a fonte nas configurações do Android não perde isso.
            LocalDensity provides UiDensity(base.density, base.fontScale * settings.fontScale),
            LocalLauncherDensity provides settings.density,
            LocalBackgroundOpacity provides settings.backgroundOpacity,
            LocalOnWallpaper provides onWallpaper,
            // O par guardado para as folhas e diálogos, que têm superfície própria.
            LocalSurfaceScheme provides colors,
            LocalSurfaceTypography provides surfaceTypography,
            content = content,
        )
    }
}

/**
 * O tema de volta ao normal, para o que é desenhado sobre uma superfície opaca:
 * folhas de baixo, diálogos, qualquer janela própria da home.
 *
 * Sobre o papel de parede o tema troca `onSurface` por branco (ou pela tinta
 * escura) e põe sombra em toda a tipografia. Dentro de uma `ModalBottomSheet`,
 * que desenha o `surfaceContainer` do Material por baixo, isso daria texto
 * branco sobre superfície clara — ilegível. Aqui o esquema e a tipografia
 * originais voltam, e [LocalOnWallpaper] volta a ser falso.
 *
 * Envolve a folha inteira, não só o conteúdo: o container dela também se pinta
 * com o esquema. Fora da home é inofensivo — o esquema guardado é o mesmo que
 * já está valendo.
 */
@Composable
fun SurfaceTheme(content: @Composable () -> Unit) {
    val scheme = LocalSurfaceScheme.current ?: MaterialTheme.colorScheme
    val typography = LocalSurfaceTypography.current ?: MaterialTheme.typography
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(LocalOnWallpaper provides false, content = content)
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

/**
 * O esquema com as tintas de wallpaper no lugar das que dependiam da superfície.
 *
 * Mudam as tintas — `onSurface`, `onSurfaceVariant`, `onBackground` — que a home
 * usa em rótulo, cabeçalho de seção e texto secundário, e mudam `surface` e
 * `surfaceVariant`, que não pintam mais fundo nenhum: sobraram como véu
 * translúcido atrás de um chip ou do campo de busca ([WALLPAPER_VEIL_ALPHA]), e
 * um véu claro atrás de texto claro não separa coisa alguma. Por isso os dois
 * acompanham a tinta, e não o modo claro/escuro do tema.
 *
 * `primary` fica com a cor do usuário — trocá-la apagaria a escolha de cor de
 * destaque. O que se garante é o piso de contraste: com texto claro, uma
 * primária escura demais some no fundo, então entra a `primaryContainer` (que a
 * paleta já calcula clara no tema escuro) e, se nem ela subir de
 * [MIN_ACCENT_LUMINANCE], uma versão clareada da própria primária. Clarear é
 * preferível a cair no branco: o matiz escolhido continua reconhecível.
 */
private fun ColorScheme.onWallpaper(darkInk: Boolean): ColorScheme {
    val ink = if (darkInk) WallpaperDarkInk else Color.White
    // O texto secundário perde alfa em vez de ganhar cinza: o cinza é calculado
    // contra uma superfície, e aqui por baixo não há superfície nenhuma.
    val inkVariant = ink.copy(alpha = if (darkInk) 0.80f else 0.85f)
    val accent = if (darkInk) primary else primary.readableOnDarkBackdrop(primaryContainer)
    return copy(
        onSurface = ink,
        onSurfaceVariant = inkVariant,
        onBackground = ink,
        surface = if (darkInk) Color.White else WallpaperDarkInk,
        surfaceVariant = if (darkInk) LightColors.surfaceVariant else DarkColors.surfaceVariant,
        primary = accent,
        // A tinta de cima acompanha: se a primária teve de clarear para não
        // sumir no fundo, o `onPrimary` calculado para a cor de antes (branco,
        // quase sempre) já não contrasta com ela. Clara o bastante para passar
        // de [MIN_ACCENT_LUMINANCE], ela só aceita tinta escura por cima.
        onPrimary = if (accent == primary) onPrimary else WallpaperDarkInk,
    )
}

/** A primária, ou algo tão parecido quanto possível que não suma num fundo escuro. */
private fun Color.readableOnDarkBackdrop(container: Color): Color = when {
    luminance() >= MIN_ACCENT_LUMINANCE -> this
    container.luminance() >= MIN_ACCENT_LUMINANCE -> container
    else -> lightenTo(MIN_ACCENT_LUMINANCE)
}

/** Mistura com branco em passos pequenos até a luminância chegar a [target]. */
private fun Color.lightenTo(target: Float): Color {
    var mix = 0.1f
    while (mix < 1f) {
        val lighter = lerp(this, Color.White, mix)
        if (lighter.luminance() >= target) return lighter
        mix += 0.1f
    }
    return Color.White
}

/** A sombra que descola o texto da foto: escura atrás do texto claro, e vice-versa. */
private fun wallpaperShadow(darkInk: Boolean): Shadow = Shadow(
    color = if (darkInk) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.45f),
    offset = Offset(0f, 1.5f),
    blurRadius = 6f,
)

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

/** A mesma sombra em todos os estilos, pelo mesmo motivo do [withFamily]. */
private fun Typography.withShadow(shadow: Shadow): Typography = copy(
    displayLarge = displayLarge.copy(shadow = shadow),
    displayMedium = displayMedium.copy(shadow = shadow),
    displaySmall = displaySmall.copy(shadow = shadow),
    headlineLarge = headlineLarge.copy(shadow = shadow),
    headlineMedium = headlineMedium.copy(shadow = shadow),
    headlineSmall = headlineSmall.copy(shadow = shadow),
    titleLarge = titleLarge.copy(shadow = shadow),
    titleMedium = titleMedium.copy(shadow = shadow),
    titleSmall = titleSmall.copy(shadow = shadow),
    bodyLarge = bodyLarge.copy(shadow = shadow),
    bodyMedium = bodyMedium.copy(shadow = shadow),
    bodySmall = bodySmall.copy(shadow = shadow),
    labelLarge = labelLarge.copy(shadow = shadow),
    labelMedium = labelMedium.copy(shadow = shadow),
    labelSmall = labelSmall.copy(shadow = shadow),
)
