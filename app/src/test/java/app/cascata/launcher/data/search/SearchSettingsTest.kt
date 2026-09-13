package app.cascata.launcher.data.search

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSettingsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `so contatos comecam desligados`() {
        val default = SearchSettings.DEFAULT
        assertTrue(default.showShortcuts)
        assertTrue(default.showCalculator)
        assertTrue(default.showSettings)
        assertTrue(default.showWeb)
        // Contatos é o único que pede permissão: liga só quando o usuário quer.
        assertFalse(default.showContacts)
        assertEquals(SearchEngine.DUCKDUCKGO, default.engine)
    }

    @Test
    fun `o objeto padrao e sempre o mesmo`() {
        assertSame(SearchSettings.DEFAULT, SearchSettings.DEFAULT)
        assertEquals(SearchSettings(), SearchSettings.DEFAULT)
    }

    @Test
    fun `vai e volta pelo json`() {
        val settings = SearchSettings(
            engine = SearchEngine.STARTPAGE,
            showContacts = true,
            showWeb = false,
        )
        val text = json.encodeToString(settings)
        assertEquals(settings, json.decodeFromString<SearchSettings>(text))
    }

    @Test
    fun `campos ausentes viram o padrao`() {
        assertEquals(SearchSettings.DEFAULT, json.decodeFromString<SearchSettings>("{}"))
    }

    @Test
    fun `o motor e gravado pelo nome`() {
        assertTrue(json.encodeToString(SearchSettings(engine = SearchEngine.BING)).contains("\"BING\""))
    }
}
