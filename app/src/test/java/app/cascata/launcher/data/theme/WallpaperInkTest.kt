package app.cascata.launcher.data.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperInkTest {

    @Test
    fun `a escolha do usuario ignora o palpite do sistema`() {
        for (hint in listOf(true, false, null)) {
            assertFalse("dica=$hint", wallpaperInk(WallpaperText.LIGHT, hint))
            assertTrue("dica=$hint", wallpaperInk(WallpaperText.DARK, hint))
        }
    }

    @Test
    fun `no automatico o sistema decide`() {
        assertTrue(wallpaperInk(WallpaperText.AUTO, true))
        assertFalse(wallpaperInk(WallpaperText.AUTO, false))
    }

    /** Sem resposta do sistema fica o texto claro, que é o que sobrevive a qualquer foto. */
    @Test
    fun `sem resposta do sistema o texto fica claro`() {
        assertFalse(wallpaperInk(WallpaperText.AUTO, null))
    }
}
