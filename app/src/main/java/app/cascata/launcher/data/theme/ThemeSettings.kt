package app.cascata.launcher.data.theme

import kotlinx.serialization.Serializable

/** Claro, escuro ou o que o sistema mandar — o padrão de um launcher é obedecer. */
enum class DarkMode { SYSTEM, LIGHT, DARK }

/** De onde vem a cor: Material You (ou a paleta própria abaixo do 12), o wallpaper, ou uma escolha manual. */
enum class ColorSource { SYSTEM, WALLPAPER, ACCENT }

/** Três presets de espaçamento da lista. */
enum class Density { COMPACT, DEFAULT, COMFORTABLE }

/** Os quatro desenhos do relógio do topo. [BASIC] é o que existia antes da Fase 3. */
enum class ClockStyle { BASIC, BIG, TWO_LINE, ANALOG }

/** Favoritos empilhados numa coluna ([LIST]) ou lado a lado numa fileira ([ROW]). */
enum class FavoritesStyle { LIST, ROW }

/** O índice alfabético da lateral: em onda ([WAVE]) ou numa coluna reta ([STRAIGHT]). */
enum class IndexStyle { WAVE, STRAIGHT }

/**
 * Cor do texto quando ele fica direto sobre o papel de parede. [AUTO] pergunta
 * ao sistema (ver `WallpaperColorsSource.supportsDarkText`); as outras duas são
 * a palavra final do usuário, para quando o palpite do sistema erra.
 */
enum class WallpaperText { AUTO, LIGHT, DARK }

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
    /**
     * Alfa da superfície sobre o wallpaper, 0f..1f. Zero por padrão desde a
     * Fase 10: a home nasce direto sobre o papel de parede, sem véu no meio.
     */
    val backgroundOpacity: Float = 0f,
    val density: Density = Density.DEFAULT,
    val fontScale: Float = 1f,
    /** `"system"` | `"custom"` | um id de `Fonts.bundled`. */
    val fontId: String = "nunito",
    /** packageName do pacote de ícones, ou null para os ícones do sistema. */
    val iconPack: String? = null,
    /**
     * Campo novo da Fase 3, no fim e com default: o [ThemeFile] continua na
     * versão 1 e um `.cascata-theme` escrito pela v0.3 segue válido — sem o
     * campo, o relógio volta a ser o [ClockStyle.BASIC].
     */
    val clockStyle: ClockStyle = ClockStyle.BASIC,
    /**
     * Campos novos da Fase 10, no fim e com default, pela mesma razão do
     * [clockStyle]: o [ThemeFile] continua na versão 1, e um `.cascata-theme`
     * (ou um `.cascata-backup`) escrito antes desta fase segue válido — o que
     * faltar no arquivo entra com o padrão de agora.
     */
    val favoritesStyle: FavoritesStyle = FavoritesStyle.LIST,
    val indexStyle: IndexStyle = IndexStyle.WAVE,
    val wallpaperText: WallpaperText = WallpaperText.AUTO,
    /** Sombra atrás do texto que fica sobre o papel de parede. */
    val textShadow: Boolean = true,
    /** Campo de busca fixo na home; desligado, ele só aparece no gesto ou na busca. */
    val searchBarVisible: Boolean = false,
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
