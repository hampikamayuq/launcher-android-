package app.cascata.launcher.data.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedPaletteTest {

    private val seeds = mapOf(
        "vermelho" to 0xFFD32F2F.toInt(),
        "verde" to 0xFF2E7D32.toInt(),
        "azul" to 0xFF2E5AAC.toInt(),
        "amarelo" to 0xFFFFEB3B.toInt(),
        "cinza" to 0xFF808080.toInt(),
        "preto" to 0xFF000000.toInt(),
        "branco" to 0xFFFFFFFF.toInt(),
        "magenta" to 0xFFFF00FF.toInt(),
    )

    @Test
    fun `texto sobre a superficie passa de 4_5`() {
        forEachScheme { name, dark, scheme ->
            assertContrast("$name/$dark onSurface", scheme.onSurface, scheme.surface, 4.5)
            assertContrast("$name/$dark onBackground", scheme.onBackground, scheme.background, 4.5)
            assertContrast(
                "$name/$dark onSurfaceVariant", scheme.onSurfaceVariant, scheme.surfaceVariant, 4.5,
            )
        }
    }

    @Test
    fun `a cor primaria se destaca da superficie e aceita seu texto`() {
        forEachScheme { name, dark, scheme ->
            assertContrast("$name/$dark primary", scheme.primary, scheme.surface, 4.5)
            assertContrast("$name/$dark onPrimary", scheme.onPrimary, scheme.primary, 4.5)
            assertContrast("$name/$dark secondary", scheme.secondary, scheme.surface, 4.5)
            assertContrast("$name/$dark onSecondary", scheme.onSecondary, scheme.secondary, 4.5)
            assertContrast(
                "$name/$dark onPrimaryContainer",
                scheme.onPrimaryContainer,
                scheme.primaryContainer,
                4.5,
            )
        }
    }

    @Test
    fun `o traco passa do contraste de borda`() {
        forEachScheme { name, dark, scheme ->
            assertContrast("$name/$dark outline", scheme.outline, scheme.surface, 3.0)
        }
    }

    @Test
    fun `toda cor e opaca`() {
        forEachScheme { name, dark, scheme ->
            val all = listOf(
                scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.onPrimaryContainer,
                scheme.secondary, scheme.onSecondary, scheme.surface, scheme.onSurface,
                scheme.surfaceVariant, scheme.onSurfaceVariant, scheme.background,
                scheme.onBackground, scheme.outline,
            )
            all.forEach { assertEquals("$name/$dark alfa", 0xFF, it ushr 24 and 0xFF) }
        }
    }

    @Test
    fun `o escuro e mais escuro que o claro`() {
        seeds.forEach { (name, seed) ->
            val light = seedScheme(seed, dark = false)
            val dark = seedScheme(seed, dark = true)
            assertTrue(
                "$name: superfície escura deveria ser mais escura",
                luma(dark.surface) < luma(light.surface),
            )
        }
    }

    @Test
    fun `a mesma semente da sempre o mesmo esquema`() {
        seeds.forEach { (_, seed) ->
            assertEquals(seedScheme(seed, dark = false), seedScheme(seed, dark = false))
            assertEquals(seedScheme(seed, dark = true), seedScheme(seed, dark = true))
        }
    }

    @Test
    fun `o contraste segue a WCAG nos extremos`() {
        assertEquals(21.0, contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.05)
        assertEquals(1.0, contrastRatio(0xFF123456.toInt(), 0xFF123456.toInt()), 0.001)
        // A razão não depende da ordem dos argumentos.
        assertEquals(
            contrastRatio(0xFF2E5AAC.toInt(), 0xFFFFFFFF.toInt()),
            contrastRatio(0xFFFFFFFF.toInt(), 0xFF2E5AAC.toInt()),
            0.0001,
        )
    }

    private fun forEachScheme(check: (String, String, SchemeColors) -> Unit) {
        seeds.forEach { (name, seed) ->
            check(name, "claro", seedScheme(seed, dark = false))
            check(name, "escuro", seedScheme(seed, dark = true))
        }
    }

    private fun assertContrast(what: String, a: Int, b: Int, min: Double) {
        val ratio = contrastRatio(a, b)
        assertTrue("$what: contraste %.2f < $min".format(ratio), ratio >= min)
    }

    /** Só para ordenar claro e escuro; não é a luminância da WCAG. */
    private fun luma(argb: Int): Int =
        (argb ushr 16 and 0xFF) + (argb ushr 8 and 0xFF) + (argb and 0xFF)
}
