package app.cascata.launcher.data.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeFileTest {

    private val sample = ThemeSettings(
        darkMode = DarkMode.DARK,
        colorSource = ColorSource.ACCENT,
        accentArgb = 0xFFCC0044.toInt(),
        backgroundOpacity = 0.3f,
        density = Density.COMFORTABLE,
        fontScale = 1.2f,
        fontId = "outfit",
        iconPack = "com.exemplo.icones",
    )

    @Test
    fun `vai e volta sem perder nada`() {
        val decoded = ThemeFile.decode(ThemeFile.encode(sample))
        assertEquals(sample, decoded.getOrNull())
    }

    @Test
    fun `o envelope tem formato e versao`() {
        val text = ThemeFile.encode(sample)
        assertTrue(text, text.contains("\"format\": \"cascata-theme\""))
        assertTrue(text, text.contains("\"version\": 1"))
        // Pretty-print: o arquivo é para ser lido e editado à mão se preciso.
        assertTrue(text, text.contains("\n"))
    }

    @Test
    fun `outro formato e recusado`() {
        val text = """{"format":"outra-coisa","version":1,"theme":{}}"""
        assertTrue(ThemeFile.decode(text).isFailure)
    }

    @Test
    fun `versao futura e recusada`() {
        val text = """{"format":"cascata-theme","version":2,"theme":{}}"""
        assertTrue(ThemeFile.decode(text).isFailure)
    }

    @Test
    fun `json invalido e recusado sem lancar`() {
        assertTrue(ThemeFile.decode("não é json").isFailure)
        assertTrue(ThemeFile.decode("").isFailure)
    }

    @Test
    fun `campo desconhecido e ignorado`() {
        val text = """
            {"format":"cascata-theme","version":1,"futuro":42,
             "theme":{"fontId":"sora","brilhoDaLua":7}}
        """.trimIndent()
        val decoded = ThemeFile.decode(text).getOrThrow()
        assertEquals("sora", decoded.fontId)
        assertEquals(ThemeSettings.DEFAULT.density, decoded.density)
    }

    @Test
    fun `campos ausentes viram o padrao`() {
        val text = """{"format":"cascata-theme","version":1,"theme":{}}"""
        assertEquals(ThemeSettings.DEFAULT, ThemeFile.decode(text).getOrThrow())
    }

    @Test
    fun `tema ausente vira o padrao`() {
        val text = """{"format":"cascata-theme","version":1}"""
        assertEquals(ThemeSettings.DEFAULT, ThemeFile.decode(text).getOrThrow())
    }

    @Test
    fun `valores fora da faixa chegam corrigidos`() {
        val text = """
            {"format":"cascata-theme","version":1,
             "theme":{"fontScale":9.0,"backgroundOpacity":-1.0,"iconPack":""}}
        """.trimIndent()
        val decoded = ThemeFile.decode(text).getOrThrow()
        assertEquals(MAX_FONT_SCALE, decoded.fontScale, 0f)
        assertEquals(0f, decoded.backgroundOpacity, 0f)
        assertEquals(null, decoded.iconPack)
    }
}
