package app.cascata.launcher.data.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
    fun `o objeto padrao e sempre o mesmo`() {
        assertSame(ThemeSettings.DEFAULT, ThemeSettings.DEFAULT)
        assertEquals(ThemeSettings(), ThemeSettings.DEFAULT)
    }
}
