package app.cascata.launcher.data.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSettingsTest {

    @Test
    fun `o padrao ja esta dentro dos limites`() {
        assertEquals(ThemeSettings.DEFAULT, ThemeSettings.DEFAULT.coerced())
    }

    @Test
    fun `opacidade fica entre zero e um`() {
        assertEquals(0f, ThemeSettings(backgroundOpacity = -3f).coerced().backgroundOpacity, 0f)
        assertEquals(1f, ThemeSettings(backgroundOpacity = 9f).coerced().backgroundOpacity, 0f)
        assertEquals(0.42f, ThemeSettings(backgroundOpacity = 0.42f).coerced().backgroundOpacity, 0f)
    }

    @Test
    fun `escala de fonte fica entre 0_85 e 1_30`() {
        assertEquals(MIN_FONT_SCALE, ThemeSettings(fontScale = 0.1f).coerced().fontScale, 0f)
        assertEquals(MAX_FONT_SCALE, ThemeSettings(fontScale = 4f).coerced().fontScale, 0f)
        assertEquals(1.15f, ThemeSettings(fontScale = 1.15f).coerced().fontScale, 0f)
    }

    @Test
    fun `pacote de icones em branco vira ausencia`() {
        assertNull(ThemeSettings(iconPack = "   ").coerced().iconPack)
        assertNull(ThemeSettings(iconPack = "").coerced().iconPack)
        assertEquals("com.pack", ThemeSettings(iconPack = " com.pack ").coerced().iconPack)
    }

    @Test
    fun `o resto passa intacto`() {
        val settings = ThemeSettings(
            darkMode = DarkMode.DARK,
            colorSource = ColorSource.WALLPAPER,
            accentArgb = 0xFF00FF00.toInt(),
            density = Density.COMPACT,
            fontId = "sora",
        )
        val coerced = settings.coerced()
        assertEquals(DarkMode.DARK, coerced.darkMode)
        assertEquals(ColorSource.WALLPAPER, coerced.colorSource)
        assertEquals(0xFF00FF00.toInt(), coerced.accentArgb)
        assertEquals(Density.COMPACT, coerced.density)
        assertEquals("sora", coerced.fontId)
    }

    @Test
    fun `o relogio comeca no estilo basico`() {
        assertEquals(ClockStyle.BASIC, ThemeSettings.DEFAULT.clockStyle)
        assertEquals(
            ClockStyle.TWO_LINE,
            ThemeSettings(clockStyle = ClockStyle.TWO_LINE).coerced().clockStyle,
        )
    }

    /**
     * Os padrões da Fase 10: a home nasce sobre o papel de parede, na Nunito,
     * com favoritos em lista, índice em onda, sombra no texto e sem campo de
     * busca fixo. A UI da home conta com estes valores.
     */
    @Test
    fun `os padroes da fase 10`() {
        val padrao = ThemeSettings.DEFAULT
        assertEquals(0f, padrao.backgroundOpacity, 0f)
        assertEquals("nunito", padrao.fontId)
        assertEquals(FavoritesStyle.LIST, padrao.favoritesStyle)
        assertEquals(IndexStyle.WAVE, padrao.indexStyle)
        assertEquals(WallpaperText.AUTO, padrao.wallpaperText)
        assertTrue(padrao.textShadow)
        assertFalse(padrao.searchBarVisible)
    }

    @Test
    fun `os campos novos passam intactos pelo coerced`() {
        val settings = ThemeSettings(
            favoritesStyle = FavoritesStyle.ROW,
            indexStyle = IndexStyle.STRAIGHT,
            wallpaperText = WallpaperText.DARK,
            textShadow = false,
            searchBarVisible = true,
        ).coerced()
        assertEquals(FavoritesStyle.ROW, settings.favoritesStyle)
        assertEquals(IndexStyle.STRAIGHT, settings.indexStyle)
        assertEquals(WallpaperText.DARK, settings.wallpaperText)
        assertFalse(settings.textShadow)
        assertTrue(settings.searchBarVisible)
    }

    @Test
    fun `o objeto padrao e sempre o mesmo`() {
        assertSame(ThemeSettings.DEFAULT, ThemeSettings.DEFAULT)
        assertEquals(ThemeSettings(), ThemeSettings.DEFAULT)
    }
}
