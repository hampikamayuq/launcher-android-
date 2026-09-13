package app.cascata.launcher.data.theme

import kotlinx.serialization.Serializable

/** Claro, escuro ou o que o sistema mandar — o padrão de um launcher é obedecer. */
enum class DarkMode { SYSTEM, LIGHT, DARK }

/** De onde vem a cor: Material You (ou a paleta própria abaixo do 12), o wallpaper, ou uma escolha manual. */
enum class ColorSource { SYSTEM, WALLPAPER, ACCENT }

/** Três presets de espaçamento da lista. */
enum class Density { COMPACT, DEFAULT, COMFORTABLE }

/** Limites da escala de fonte: abaixo disso não se lê, acima disso a lista vira duas linhas. */
const val MIN_FONT_SCALE = 0.85f
const val MAX_FONT_SCALE = 1.30f

/**
 * Tudo o que muda a aparência, num objeto só. É [Serializable] porque este mesmo
 * objeto é o conteúdo do arquivo `.cascata-theme` (ver [ThemeFile]).
 */
@Serializable
data class ThemeSettings(
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val colorSource: ColorSource = ColorSource.SYSTEM,
    val accentArgb: Int = 0xFF2E5AAC.toInt(),
    /** Alfa da superfície sobre o wallpaper, 0f..1f. */
    val backgroundOpacity: Float = 0.55f,
    val density: Density = Density.DEFAULT,
    val fontScale: Float = 1f,
    /** `"system"` | `"custom"` | um id de `Fonts.bundled`. */
    val fontId: String = "system",
    /** packageName do pacote de ícones, ou null para os ícones do sistema. */
    val iconPack: String? = null,
) {
    companion object {
        val DEFAULT = ThemeSettings()
    }
}

/**
 * Põe cada campo dentro do que a UI sabe desenhar. Aplicado na leitura das
 * preferências e na importação de arquivo — quem grava não precisa conferir.
 */
fun ThemeSettings.coerced(): ThemeSettings = copy(
    backgroundOpacity = backgroundOpacity.coerceIn(0f, 1f),
    fontScale = fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE),
    // Nome de pacote em branco é o mesmo que não ter pacote; normalizar aqui
    // evita que a UI tenha de tratar "" e null de formas diferentes.
    iconPack = iconPack?.trim()?.takeIf { it.isNotEmpty() },
)
