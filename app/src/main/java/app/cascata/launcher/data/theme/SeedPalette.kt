package app.cascata.launcher.data.theme

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * As cores que a UI precisa, derivadas de uma semente só. Nomes iguais aos do
 * Material 3 porque é para lá que vão (`lightColorScheme`/`darkColorScheme`).
 */
data class SchemeColors(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val onSecondary: Int,
    val surface: Int,
    val onSurface: Int,
    val surfaceVariant: Int,
    val onSurfaceVariant: Int,
    val background: Int,
    val onBackground: Int,
    val outline: Int,
)

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

/** Contraste mínimo de texto pela WCAG AA. */
private const val MIN_TEXT = 4.5

/** Contraste mínimo de um traço fino (borda, divisor) pela WCAG AA. */
private const val MIN_OUTLINE = 3.0

/** Tinta da semente nas superfícies: o suficiente para não parecer cinza de graça. */
private const val SURFACE_SAT = 0.08f

/** Passo e teto da busca de luminosidade. 0,004 × 300 cobre a faixa 0..1 inteira. */
private const val STEP = 0.004f
private const val STEPS = 300

/**
 * Esquema completo a partir de uma cor. Tudo é matemática sobre Int ARGB — nada
 * de `android.graphics` — para caber num teste de JVM.
 *
 * O espaço usado é HSL: barato, invertível e com um eixo (L) que move o contraste
 * na direção certa. Não é perceptualmente uniforme como OKLCH, mas aqui o que
 * decide as cores é o contraste medido (WCAG), não a distância percebida — então
 * a não uniformidade do HSL não chega ao resultado.
 */
fun seedScheme(seedArgb: Int, dark: Boolean): SchemeColors {
    val seed = argbToHsl(seedArgb)
    val hue = seed.h
    val sat = seed.s

    // Superfícies quase neutras, com um fio da semente dentro.
    val neutral = min(sat, SURFACE_SAT)
    val variantSat = min(sat, SURFACE_SAT * 1.5f)
    val background = hsl(hue, neutral, if (dark) 0.05f else 0.99f)
    val surface = hsl(hue, neutral, if (dark) 0.07f else 0.97f)
    val surfaceVariant = hsl(hue, variantSat, if (dark) 0.16f else 0.90f)

    // A semente sobe (escuro) ou desce (claro) até destacar da superfície.
    val primary = fitContrast(hue, sat, seed.l, surface, MIN_TEXT, lighten = dark)
    val primaryContainer = hsl(hue, sat * 0.55f, if (dark) 0.24f else 0.88f)

    // Secundária: mesma família, um passo no círculo de cor e menos saturação.
    val secondaryHue = (hue + 32f) % 360f
    val secondary = fitContrast(secondaryHue, sat * 0.5f, seed.l, surface, MIN_TEXT, lighten = dark)

    return SchemeColors(
        primary = primary,
        onPrimary = bestOf(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = fitContrast(
            hue, min(sat, 0.9f), if (dark) 0.90f else 0.15f, primaryContainer, MIN_TEXT, lighten = dark,
        ),
        secondary = secondary,
        onSecondary = bestOf(secondary),
        surface = surface,
        onSurface = fitContrast(
            hue, variantSat, if (dark) 0.92f else 0.12f, surface, MIN_TEXT, lighten = dark,
        ),
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = fitContrast(
            hue, variantSat, if (dark) 0.80f else 0.30f, surfaceVariant, MIN_TEXT, lighten = dark,
        ),
        background = background,
        onBackground = fitContrast(
            hue, variantSat, if (dark) 0.92f else 0.12f, background, MIN_TEXT, lighten = dark,
        ),
        // Traço: cinza tintado, só o contraste de borda.
        outline = fitContrast(
            hue, min(sat, 0.15f), if (dark) 0.55f else 0.45f, surface, MIN_OUTLINE, lighten = dark,
        ),
    )
}

/** Contraste WCAG entre duas cores opacas: 1,0 (iguais) a 21,0 (preto e branco). */
fun contrastRatio(a: Int, b: Int): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

/**
 * Move a luminosidade de passo em passo até bater [minRatio] contra [background].
 * Se a cor chegar ao extremo sem conseguir, devolve preto ou branco — o que
 * contrastar mais —, porque texto ilegível é pior que texto sem a cor certa.
 */
private fun fitContrast(
    h: Float,
    s: Float,
    startL: Float,
    background: Int,
    minRatio: Double,
    lighten: Boolean,
): Int {
    var l = startL.coerceIn(0f, 1f)
    var color = hsl(h, s, l)
    var steps = 0
    while (contrastRatio(color, background) < minRatio && steps < STEPS) {
        if (l <= 0f && !lighten) break
        if (l >= 1f && lighten) break
        l = (l + if (lighten) STEP else -STEP).coerceIn(0f, 1f)
        color = hsl(h, s, l)
        steps++
    }
    if (contrastRatio(color, background) >= minRatio) return color
    return bestOf(background)
}

/** Preto ou branco, o que contrastar mais com [background]. */
private fun bestOf(background: Int): Int =
    if (contrastRatio(WHITE, background) >= contrastRatio(BLACK, background)) WHITE else BLACK

private fun relativeLuminance(argb: Int): Double =
    0.2126 * linear(argb ushr 16 and 0xFF) +
        0.7152 * linear(argb ushr 8 and 0xFF) +
        0.0722 * linear(argb and 0xFF)

private fun linear(channel: Int): Double {
    val c = channel / 255.0
    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

private data class Hsl(val h: Float, val s: Float, val l: Float)

private fun argbToHsl(argb: Int): Hsl {
    val r = (argb ushr 16 and 0xFF) / 255f
    val g = (argb ushr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC
    val l = (maxC + minC) / 2f
    if (delta == 0f) return Hsl(0f, 0f, l)
    val s = delta / (1f - abs(2f * l - 1f))
    val h = when (maxC) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return Hsl(if (h < 0f) h + 360f else h, s.coerceIn(0f, 1f), l)
}

/** HSL -> ARGB opaco. [h] em graus, [s] e [l] em 0..1. */
private fun hsl(h: Float, s: Float, l: Float): Int {
    val hue = ((h % 360f) + 360f) % 360f
    val sat = s.coerceIn(0f, 1f)
    val lum = l.coerceIn(0f, 1f)
    val c = (1f - abs(2f * lum - 1f)) * sat
    val x = c * (1f - abs((hue / 60f) % 2f - 1f))
    val m = lum - c / 2f
    val (r, g, b) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return (0xFF shl 24) or (byte(r + m) shl 16) or (byte(g + m) shl 8) or byte(b + m)
}

private fun byte(v: Float): Int = (v * 255f).roundToInt().coerceIn(0, 255)
