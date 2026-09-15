package app.cascata.launcher

import app.cascata.launcher.data.normalizeLabel
import app.cascata.launcher.data.sectionOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Um app só com as strings que a montagem enxerga — sem ComponentName nem UserHandle. */
private data class FakeApp(
    val key: String,
    val original: String,
    val label: String = original,
) {
    val normalized: String get() = normalizeLabel(label)
    val section: Char get() = sectionOf(normalized)
}

private fun fakeApp(key: String, label: String) = FakeApp(key, label)

private fun builder() = HomeStateBuilder<FakeApp>(
    key = { it.key },
    label = { it.label },
    normalized = { it.normalized },
    section = { it.section },
    withAlias = { app, alias ->
        app.copy(label = alias?.trim()?.takeIf { it.isNotEmpty() } ?: app.original)
    },
)

private fun HomeList<FakeApp>.labels(): List<String> = rows.map {
    when (it) {
        is Row.Header -> it.letter.toString()
        is Row.App -> it.entry.label
    }
}

private fun HomeList<FakeApp>.appLabels(): List<String> =
    rows.filterIsInstance<Row.App<FakeApp>>().map { it.entry.label }

/** O mesmo rótulo por linha de [HomeList.labels], para um pedaço solto da lista. */
private fun List<Row<FakeApp>>.labels(): List<String> = map {
    when (it) {
        is Row.Header -> it.letter.toString()
        is Row.App -> it.entry.label
    }
}

class HomeStateBuilderTest {

    private val apps = listOf(
        fakeApp("a", "Agenda"),
        fakeApp("b", "Banco do Brasil"),
        fakeApp("c", "Câmera"),
        fakeApp("n", "1Password"),
    )

    @Test
    fun `secoes vem na ordem, com o cerquilha do jeito que o collator ordenar`() {
        val list = builder().build(apps, emptyList(), emptySet(), emptyMap(), "")
        assertEquals(
            listOf("#", "1Password", "A", "Agenda", "B", "Banco do Brasil", "C", "Câmera"),
            list.labels(),
        )
        assertEquals(0, list.sectionIndex['#'])
        assertEquals(2, list.sectionIndex['A'])
        assertEquals(listOf('#', 'A', 'B', 'C'), list.sectionIndex.keys.toList())
    }

    @Test
    fun `o indice aponta para a linha do cabecalho`() {
        val list = builder().build(apps, emptyList(), emptySet(), emptyMap(), "")
        for ((letter, index) in list.sectionIndex) {
            assertEquals(Row.Header(letter), list.rows[index])
        }
    }

    @Test
    fun `apelido muda o app de lugar e de secao`() {
        val list = builder().build(apps, emptyList(), emptySet(), mapOf("c" to "Zoom"), "")
        assertEquals(listOf("1Password", "Agenda", "Banco do Brasil", "Zoom"), list.appLabels())
        assertEquals(listOf('#', 'A', 'B', 'Z'), list.sectionIndex.keys.toList())
        assertNull(list.sectionIndex['C'])
    }

    @Test
    fun `apelido em branco volta ao rotulo do sistema`() {
        val list = builder().build(apps, emptyList(), emptySet(), mapOf("c" to "   "), "")
        assertTrue("Câmera" in list.appLabels())
    }

    @Test
    fun `oculto sai das linhas`() {
        val list = builder().build(apps, emptyList(), setOf("b"), emptyMap(), "")
        assertEquals(listOf("1Password", "Agenda", "Câmera"), list.appLabels())
        assertEquals(listOf("Banco do Brasil"), list.hidden.map { it.label })
        assertNull(list.sectionIndex['B'])
    }

    @Test
    fun `oculto tambem nao aparece na busca`() {
        val list = builder().build(apps, emptyList(), setOf("b"), emptyMap(), "banco")
        assertEquals(emptyList<String>(), list.appLabels())
    }

    @Test
    fun `oculto sai dos favoritos sem perder a preferencia`() {
        val list = builder().build(apps, listOf("b", "a"), setOf("b"), emptyMap(), "")
        assertEquals(listOf("Agenda"), list.favorites.map { it.label })
    }

    @Test
    fun `favoritos respeitam a ordem persistida`() {
        val list = builder().build(apps, listOf("c", "a", "b"), emptySet(), emptyMap(), "")
        assertEquals(listOf("Câmera", "Agenda", "Banco do Brasil"), list.favorites.map { it.label })
        assertTrue(list.rows.filterIsInstance<Row.App<FakeApp>>().first { it.entry.key == "c" }.favorite)
    }

    @Test
    fun `favorito de app que sumiu e ignorado`() {
        val list = builder().build(apps, listOf("fantasma", "a"), emptySet(), emptyMap(), "")
        assertEquals(listOf("Agenda"), list.favorites.map { it.label })
    }

    @Test
    fun `busca casa prefixo de palavra e ignora acento`() {
        val list = builder().build(apps, emptyList(), emptySet(), emptyMap(), "bra")
        assertEquals(listOf("Banco do Brasil"), list.appLabels())
        assertEquals(listOf("Câmera"), builder().build(apps, emptyList(), emptySet(), emptyMap(), "ca").appLabels())
    }

    @Test
    fun `busca nao casa no meio de uma palavra`() {
        val list = builder().build(apps, emptyList(), emptySet(), emptyMap(), "gen")
        assertEquals(emptyList<String>(), list.appLabels())
    }

    @Test
    fun `prefixo do rotulo inteiro vem antes de prefixo de palavra interna`() {
        val busca = listOf(fakeApp("1", "Banco do Brasil"), fakeApp("2", "Brasil Paralelo"))
        val list = builder().build(busca, emptyList(), emptySet(), emptyMap(), "bra")
        assertEquals(listOf("Brasil Paralelo", "Banco do Brasil"), list.appLabels())
        assertEquals(0, queryRank("brasil paralelo", "bra"))
        assertEquals(1, queryRank("banco do brasil", "bra"))
    }

    @Test
    fun `a busca nao monta secoes`() {
        val list = builder().build(apps, emptyList(), emptySet(), emptyMap(), "a")
        assertEquals(emptyMap<Char, Int>(), list.sectionIndex)
        assertTrue(list.rows.none { it is Row.Header })
    }

    @Test
    fun `as linhas de uma letra do meio vao do cabecalho ao proximo`() {
        val list = builder().build(apps, emptyList(), emptySet(), emptyMap(), "")
        assertEquals(listOf("B", "Banco do Brasil"), list.rowsForLetter('B').labels())
        assertEquals(Row.Header('B'), list.rowsForLetter('B').first())
    }

    @Test
    fun `a ultima letra vai ate o fim da lista`() {
        val list = builder().build(apps, emptyList(), emptySet(), emptyMap(), "")
        assertEquals(listOf("C", "Câmera"), list.rowsForLetter('C').labels())
    }

    @Test
    fun `o cerquilha e uma secao como as outras`() {
        val list = builder().build(apps, emptyList(), emptySet(), emptyMap(), "")
        assertEquals(listOf("#", "1Password"), list.rowsForLetter('#').labels())
    }

    @Test
    fun `uma secao com varios apps traz todos eles`() {
        val muitos = apps + listOf(fakeApp("b2", "Bluetooth"), fakeApp("b3", "Boletos"))
        val list = builder().build(muitos, emptyList(), emptySet(), emptyMap(), "")
        assertEquals(
            listOf("B", "Banco do Brasil", "Bluetooth", "Boletos"),
            list.rowsForLetter('B').labels(),
        )
    }

    @Test
    fun `letra que nao existe devolve lista vazia`() {
        val list = builder().build(apps, emptyList(), emptySet(), emptyMap(), "")
        assertEquals(emptyList<Row<FakeApp>>(), list.rowsForLetter('Z'))
        // Na busca não há seção nenhuma, então nem a letra que existia responde.
        val busca = builder().build(apps, emptyList(), emptySet(), emptyMap(), "ban")
        assertEquals(emptyList<Row<FakeApp>>(), busca.rowsForLetter('B'))
    }

    @Test
    fun `mover favorito respeita os limites`() {
        val keys = listOf("a", "b", "c")
        assertEquals(listOf("b", "c", "a"), moveItem(keys, 0, 2))
        assertEquals(listOf("c", "a", "b"), moveItem(keys, 2, 0))
        assertEquals(keys, moveItem(keys, 1, 1))
        assertNull(moveItem(keys, 0, 3))
        assertNull(moveItem(keys, -1, 0))
    }
}
