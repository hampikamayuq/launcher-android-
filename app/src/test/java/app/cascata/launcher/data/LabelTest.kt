package app.cascata.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LabelTest {

    @Test
    fun `normaliza acentos e caixa`() {
        assertEquals("onibus", normalizeLabel("Ônibus"))
        assertEquals("acao", normalizeLabel("  Ação  "))
        assertEquals("cafe da manha", normalizeLabel("Café da Manhã"))
    }

    @Test
    fun `secao usa a primeira letra`() {
        assertEquals('O', sectionOf(normalizeLabel("Ônibus")))
        assertEquals('W', sectionOf(normalizeLabel("WhatsApp")))
    }

    @Test
    fun `nao-letras caem na secao cerquilha`() {
        assertEquals('#', sectionOf(normalizeLabel("1Password")))
        assertEquals('#', sectionOf(normalizeLabel("微信")))
        assertEquals('#', sectionOf(normalizeLabel("")))
    }
}
