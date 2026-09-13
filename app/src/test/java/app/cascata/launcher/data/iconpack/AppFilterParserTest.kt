package app.cascata.launcher.data.iconpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterParserTest {

    private fun parse(vararg items: Pair<String, String>) = parseAppFilterEntries(items.asSequence())

    @Test
    fun `tira o envelope ComponentInfo`() {
        val map = parse("ComponentInfo{com.exemplo/com.exemplo.Main}" to "exemplo")
        assertEquals(mapOf("com.exemplo/com.exemplo.Main" to "exemplo"), map)
    }

    @Test
    fun `aceita componente ja cru`() {
        val map = parse("com.exemplo/com.exemplo.Main" to "exemplo")
        assertEquals(mapOf("com.exemplo/com.exemplo.Main" to "exemplo"), map)
    }

    @Test
    fun `espacos em volta nao atrapalham`() {
        val map = parse("  ComponentInfo{com.exemplo/com.exemplo.Main}  " to "  exemplo  ")
        assertEquals(mapOf("com.exemplo/com.exemplo.Main" to "exemplo"), map)
    }

    @Test
    fun `classe relativa ganha o pacote`() {
        val map = parse("ComponentInfo{com.exemplo/.Main}" to "exemplo")
        assertEquals(mapOf("com.exemplo/com.exemplo.Main" to "exemplo"), map)
    }

    @Test
    fun `a primeira ocorrencia vence`() {
        val map = parse(
            "com.exemplo/com.exemplo.Main" to "primeiro",
            "ComponentInfo{com.exemplo/com.exemplo.Main}" to "segundo",
        )
        assertEquals(mapOf("com.exemplo/com.exemplo.Main" to "primeiro"), map)
    }

    @Test
    fun `malformados sao descartados`() {
        val map = parse(
            "" to "a",
            "   " to "a",
            "com.exemplo" to "a",
            "/com.exemplo.Main" to "a",
            "com.exemplo/" to "a",
            "ComponentInfo{com.exemplo/com.exemplo.Main" to "a",
            "com.exemplo/com.exemplo.Main}" to "a",
            "com.a/com.a.Main/extra" to "a",
            "com.exemplo/com.exemplo Main" to "a",
            "com.exemplo/com.exemplo.Main" to "   ",
        )
        assertTrue(map.toString(), map.isEmpty())
    }

    @Test
    fun `entradas boas sobrevivem ao lixo em volta`() {
        val map = parse(
            "quebrado" to "x",
            "ComponentInfo{com.a/com.a.A}" to "a",
            "" to "y",
            "ComponentInfo{com.b/com.b.B}" to "b",
        )
        assertEquals(mapOf("com.a/com.a.A" to "a", "com.b/com.b.B" to "b"), map)
    }

    @Test
    fun `a ordem de insercao e preservada`() {
        val map = parse(
            "com.c/com.c.C" to "c",
            "com.a/com.a.A" to "a",
        )
        assertEquals(listOf("com.c/com.c.C", "com.a/com.a.A"), map.keys.toList())
    }

    @Test
    fun `normalizacao sozinha`() {
        assertEquals("com.a/com.a.Main", normalizeComponent("ComponentInfo{com.a/com.a.Main}"))
        assertEquals("com.a/com.a.Main", normalizeComponent("com.a/.Main"))
        assertNull(normalizeComponent("ComponentInfo{}"))
        assertNull(normalizeComponent("nada"))
    }
}
