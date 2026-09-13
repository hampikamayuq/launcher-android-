package app.cascata.launcher.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rótulos fixos: o filtro é puro e não depende de recursos nem de Context. */
private val ITEMS = listOf(
    LabeledSetting(entry("wifi", "wifi wi fi rede sem fio wireless internet"), "Wi-Fi"),
    LabeledSetting(entry("bluetooth", "bluetooth fone pareamento"), "Bluetooth"),
    LabeledSetting(entry("display", "tela brilho rotacao"), "Tela"),
    LabeledSetting(entry("battery", "bateria energia carga"), "Bateria"),
    LabeledSetting(entry("home", "tela inicial launcher home padrao"), "Tela inicial padrão"),
)

private fun entry(id: String, keywords: String) =
    SettingEntry(id = id, labelRes = 0, keywords = keywords, action = "android.settings.$id")

private fun ids(needle: String, limit: Int = 4) =
    filterSettings(ITEMS, needle, limit).map { it.id }

class SystemSettingsIndexTest {

    @Test
    fun `casa pelo rotulo, sem acento`() {
        assertEquals(listOf("bluetooth"), ids("blue"))
        assertEquals(listOf("battery"), ids("bateria"))
    }

    @Test
    fun `rotulo inteiro vem antes de palavra interna, e ambos antes de palavra-chave`() {
        // "Tela" casa no rótulo todo; "Tela inicial padrão" também começa com
        // "tela", então desempata pelo rótulo normalizado.
        assertEquals(listOf("display", "home"), ids("tela"))
        // "wireless" só existe nas palavras-chave do Wi-Fi.
        assertEquals(listOf("wifi"), ids("wireless"))
    }

    @Test
    fun `casa por sinonimo sem acento`() {
        assertEquals(listOf("wifi"), ids("internet"))
        assertEquals(listOf("home"), ids("launcher"))
        assertEquals(listOf("battery"), ids("energia"))
    }

    @Test
    fun `tolera uma letra errada, como a lista de apps`() {
        assertEquals(listOf("bluetooth"), ids("bluetoth"))
        assertEquals(listOf("battery"), ids("bateira"))
    }

    @Test
    fun `query em branco e termo sem casamento devolvem vazio`() {
        assertTrue(ids("").isEmpty())
        assertTrue(ids("   ").isEmpty())
        assertTrue(ids("xyz").isEmpty())
    }

    @Test
    fun `o limite corta a lista`() {
        assertEquals(1, ids("tela", limit = 1).size)
    }

    @Test
    fun `o indice tem as dezoito telas, com id e acao unicos`() {
        val entries = SystemSettingsIndex.entries
        assertEquals(18, entries.size)
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        assertEquals(entries.size, entries.map { it.action }.toSet().size)
        // Palavra-chave com acento nunca casaria: o termo da busca vem sem acento.
        assertTrue(entries.all { it.keywords == it.keywords.lowercase() })
        assertTrue(entries.none { it.keywords.any { c -> c.code > 127 } })
    }
}
