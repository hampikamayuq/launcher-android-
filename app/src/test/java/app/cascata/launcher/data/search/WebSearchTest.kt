package app.cascata.launcher.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchTest {

    @Test
    fun `espaco vira mais`() {
        assertEquals(
            "https://duckduckgo.com/?q=receita+de+pao",
            buildSearchUrl(SearchEngine.DUCKDUCKGO, "receita de pao"),
        )
    }

    @Test
    fun `acento vira percentual`() {
        assertEquals(
            "https://www.ecosia.org/search?q=p%C3%A3o+de+queijo",
            buildSearchUrl(SearchEngine.ECOSIA, "pão de queijo"),
        )
    }

    @Test
    fun `e comercial nao quebra a query string`() {
        assertEquals(
            "https://search.brave.com/search?q=a+%26+b",
            buildSearchUrl(SearchEngine.BRAVE, "a & b"),
        )
    }

    @Test
    fun `todo motor tem template https com um so marcador`() {
        for (engine in SearchEngine.entries) {
            assertTrue(engine.label, engine.template.startsWith("https://"))
            assertEquals(engine.label, 1, engine.template.split("%s").size - 1)
            assertTrue(engine.label, engine.label.isNotBlank())
        }
    }

    @Test
    fun `o padrao e o duckduckgo`() {
        assertEquals(SearchEngine.DUCKDUCKGO, SearchSettings.DEFAULT.engine)
    }
}
