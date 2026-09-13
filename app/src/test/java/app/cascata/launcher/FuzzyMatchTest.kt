package app.cascata.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatchTest {

    @Test
    fun `distancia zero`() {
        assertTrue(editDistanceAtMostOne("telegram", "telegram"))
        assertTrue(editDistanceAtMostOne("", ""))
    }

    @Test
    fun `uma troca de letra`() {
        assertTrue(editDistanceAtMostOne("telegran", "telegram"))
        assertTrue(editDistanceAtMostOne("xelegram", "telegram"))
    }

    @Test
    fun `uma letra a mais ou a menos`() {
        assertTrue(editDistanceAtMostOne("telgram", "telegram"))
        assertTrue(editDistanceAtMostOne("telegram", "telgram"))
        assertTrue(editDistanceAtMostOne("telegramm", "telegram"))
        assertTrue(editDistanceAtMostOne("elegram", "telegram"))
    }

    @Test
    fun `duas letras trocadas de lugar`() {
        assertTrue(editDistanceAtMostOne("teelgram", "telegram"))
        assertTrue(editDistanceAtMostOne("bateira", "bateria"))
    }

    @Test
    fun `dois erros ja e demais`() {
        assertFalse(editDistanceAtMostOne("tlgram", "telegram"))
        assertFalse(editDistanceAtMostOne("telegran", "telegrom"))
        assertFalse(editDistanceAtMostOne("abc", "xyz"))
    }

    @Test
    fun `whatsap acha whatsapp`() {
        assertTrue(matchesQuery("whatsapp", "whatsap"))
        // É prefixo exato, então continua no topo dos resultados.
        assertEquals(0, queryRank("whatsapp", "whatsap"))
    }

    @Test
    fun `telgram acha telegram, mas depois dos casamentos exatos`() {
        assertTrue(matchesQuery("telegram", "telgram"))
        assertEquals(2, queryRank("telegram", "telgram"))
    }

    @Test
    fun `erro tambem vale em palavra interna`() {
        assertTrue(matchesQuery("banco do brasil", "brasl"))
        assertEquals(2, queryRank("banco do brasil", "brasl"))
    }

    @Test
    fun `xyz nao acha nada`() {
        assertFalse(matchesQuery("telegram", "xyz"))
        assertFalse(matchesQuery("whatsapp", "xyz"))
        assertFalse(matchesQuery("banco do brasil", "xyzw"))
    }

    @Test
    fun `prefixo curto continua exato`() {
        // Com menos de quatro letras não há tolerância: "tel" acha, "tal" não.
        assertTrue(matchesQuery("telegram", "tel"))
        assertFalse(matchesQuery("telegram", "tal"))
        assertFalse(matchesQuery("telegram", "ele"))
    }

    @Test
    fun `palavra curta demais nao ganha tolerancia`() {
        // O alvo tem menos de quatro letras: "gmal" não pode achar "gmi".
        assertFalse(matchesQuery("gmi", "gmal"))
    }

    @Test
    fun `a lista da home encontra o app com uma letra errada`() {
        val apps = listOf(
            FuzzyApp("t", "Telegram"),
            FuzzyApp("w", "WhatsApp"),
            FuzzyApp("s", "Signal"),
        )
        assertEquals(listOf("Telegram"), search(apps, "telgram"))
        assertEquals(listOf("WhatsApp"), search(apps, "whatsap"))
        assertEquals(emptyList<String>(), search(apps, "xyz"))
    }

    private data class FuzzyApp(val key: String, val label: String)

    private fun search(apps: List<FuzzyApp>, query: String): List<String> {
        val builder = HomeStateBuilder<FuzzyApp>(
            key = { it.key },
            label = { it.label },
            normalized = { it.label.lowercase() },
            section = { it.label.first() },
            withAlias = { app, _ -> app },
        )
        return builder.build(apps, emptyList(), emptySet(), emptyMap(), query)
            .rows.filterIsInstance<Row.App<FuzzyApp>>().map { it.entry.label }
    }
}
