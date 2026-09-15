package app.cascata.launcher.data.widgets

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun widget(id: Int) = PlacedWidget(id, "com.exemplo/.Relogio")

    @Test
    fun `comeca vazio`() {
        assertSame(WidgetLayout.EMPTY, WidgetLayout.EMPTY)
        assertEquals(WidgetLayout(), WidgetLayout.EMPTY)
        assertTrue(WidgetLayout.EMPTY.slots.isEmpty())
        assertEquals(1, WidgetLayout.EMPTY.nextSlotId)
    }

    @Test
    fun `slot nulo abre slot novo no fim`() {
        val layout = WidgetLayout.EMPTY
            .addWidget(null, widget(10), heightCells = 3)
            .addWidget(null, widget(11), heightCells = 2)

        assertEquals(listOf(1, 2), layout.slots.map { it.id })
        assertEquals(3, layout.slots[0].heightCells)
        assertEquals(listOf(10), layout.slots[0].widgets.map { it.appWidgetId })
        assertEquals(listOf(11), layout.slots[1].widgets.map { it.appWidgetId })
        // O id nunca é reaproveitado, mesmo que o slot 1 saia depois.
        assertEquals(3, layout.nextSlotId)
    }

    @Test
    fun `slot existente empilha e mostra o novo`() {
        val layout = WidgetLayout.EMPTY
            .addWidget(null, widget(10), heightCells = 2)
            .addWidget(1, widget(11), heightCells = 4)

        assertEquals(1, layout.slots.size)
        assertEquals(listOf(10, 11), layout.slots[0].widgets.map { it.appWidgetId })
        assertEquals(1, layout.slots[0].activeIndex)
        // A altura só cresce: encolher cortaria o widget que já estava ali.
        assertEquals(4, layout.slots[0].heightCells)
        assertEquals(2, layout.nextSlotId)
    }

    @Test
    fun `altura pedida e aparada nos limites`() {
        val baixo = WidgetLayout.EMPTY.addWidget(null, widget(10), heightCells = 0)
        val alto = WidgetLayout.EMPTY.addWidget(null, widget(10), heightCells = 99)
        assertEquals(MIN_CELLS, baixo.slots[0].heightCells)
        assertEquals(MAX_CELLS, alto.slots[0].heightCells)
    }

    @Test
    fun `slot que nao existe vira slot novo`() {
        val layout = WidgetLayout.EMPTY.addWidget(77, widget(10), heightCells = 2)
        assertEquals(listOf(1), layout.slots.map { it.id })
    }

    @Test
    fun `o mesmo id nao fica em dois lugares`() {
        val layout = WidgetLayout.EMPTY
            .addWidget(null, widget(10), heightCells = 2)
            .addWidget(null, widget(11), heightCells = 2)
            .addWidget(2, widget(10), heightCells = 2)

        assertEquals(listOf(10, 11), layout.allWidgetIds().sorted())
        assertEquals(listOf(11, 10), layout.slots.single().widgets.map { it.appWidgetId })
    }

    @Test
    fun `remover deixando o slot vazio apaga o slot`() {
        val layout = WidgetLayout.EMPTY
            .addWidget(null, widget(10), heightCells = 2)
            .addWidget(null, widget(11), heightCells = 2)
            .removeWidget(10)

        assertEquals(listOf(2), layout.slots.map { it.id })
        assertEquals(listOf(11), layout.allWidgetIds())
    }

    @Test
    fun `remover corrige o activeIndex`() {
        val layout = WidgetLayout.EMPTY
            .addWidget(null, widget(10), heightCells = 2)
            .addWidget(1, widget(11), heightCells = 2)
            .addWidget(1, widget(12), heightCells = 2)

        assertEquals(2, layout.slots[0].activeIndex)
        val menor = layout.removeWidget(12)
        assertEquals(1, menor.slots[0].activeIndex)
        assertEquals(listOf(10, 11), menor.slots[0].widgets.map { it.appWidgetId })
    }

    @Test
    fun `remover id que nao existe nao muda nada`() {
        val layout = WidgetLayout.EMPTY.addWidget(null, widget(10), heightCells = 2)
        assertEquals(layout, layout.removeWidget(999))
    }

    @Test
    fun `resize respeita os limites`() {
        val layout = WidgetLayout.EMPTY.addWidget(null, widget(10), heightCells = 2)
        assertEquals(MIN_CELLS, layout.resizeSlot(1, -5).slots[0].heightCells)
        assertEquals(MAX_CELLS, layout.resizeSlot(1, 100).slots[0].heightCells)
        assertEquals(5, layout.resizeSlot(1, 5).slots[0].heightCells)
        assertEquals(layout, layout.resizeSlot(77, 5))
    }

    @Test
    fun `mover troca de posicao e para nas bordas`() {
        val layout = WidgetLayout.EMPTY
            .addWidget(null, widget(10), heightCells = 2)
            .addWidget(null, widget(11), heightCells = 2)
            .addWidget(null, widget(12), heightCells = 2)

        assertEquals(listOf(2, 1, 3), layout.moveSlot(1, 1).slots.map { it.id })
        assertEquals(listOf(1, 3, 2), layout.moveSlot(3, -1).slots.map { it.id })
        // Nas bordas devolve o mesmo layout em vez de dar a volta.
        assertEquals(layout, layout.moveSlot(1, -1))
        assertEquals(layout, layout.moveSlot(3, 1))
        assertEquals(layout, layout.moveSlot(77, 1))
    }

    @Test
    fun `setActive apara o indice`() {
        val layout = WidgetLayout.EMPTY
            .addWidget(null, widget(10), heightCells = 2)
            .addWidget(1, widget(11), heightCells = 2)

        assertEquals(0, layout.setActive(1, -3).slots[0].activeIndex)
        assertEquals(1, layout.setActive(1, 9).slots[0].activeIndex)
        assertEquals(0, layout.setActive(1, 0).slots[0].activeIndex)
        assertEquals(layout, layout.setActive(77, 0))
    }

    @Test
    fun `remap troca os ids e descarta o que nao voltou`() {
        val layout = WidgetLayout.EMPTY
            .addWidget(null, widget(10), heightCells = 2)
            .addWidget(1, widget(11), heightCells = 2)
            .addWidget(null, widget(12), heightCells = 2)

        // O 12 não aparece entre os antigos: o sistema não o restaurou.
        val remapped = layout.remapIds(intArrayOf(10, 11), intArrayOf(70, 71))

        assertEquals(listOf(70, 71), remapped.allWidgetIds())
        assertEquals(listOf(1), remapped.slots.map { it.id })
        assertEquals(1, remapped.slots[0].activeIndex)
    }

    @Test
    fun `remap que perde o widget ativo corrige o indice`() {
        val layout = WidgetLayout.EMPTY
            .addWidget(null, widget(10), heightCells = 2)
            .addWidget(1, widget(11), heightCells = 2)

        val remapped = layout.remapIds(intArrayOf(10), intArrayOf(70))
        assertEquals(listOf(70), remapped.allWidgetIds())
        assertEquals(0, remapped.slots[0].activeIndex)
    }

    @Test
    fun `remap sem nada restaurado esvazia o layout`() {
        val layout = WidgetLayout.EMPTY.addWidget(null, widget(10), heightCells = 2)
        val remapped = layout.remapIds(intArrayOf(), intArrayOf())
        assertTrue(remapped.slots.isEmpty())
        // O contador de slots não volta atrás.
        assertEquals(2, remapped.nextSlotId)
    }

    @Test
    fun `slotOf acha o dono do widget`() {
        val layout = WidgetLayout.EMPTY
            .addWidget(null, widget(10), heightCells = 2)
            .addWidget(null, widget(11), heightCells = 2)

        assertEquals(2, layout.slotOf(11)?.id)
        assertNull(layout.slotOf(999))
    }

    @Test
    fun `allWidgetIds segue a ordem da tela`() {
        val layout = WidgetLayout.EMPTY
            .addWidget(null, widget(10), heightCells = 2)
            .addWidget(null, widget(11), heightCells = 2)
            .addWidget(1, widget(12), heightCells = 2)

        assertEquals(listOf(10, 12, 11), layout.allWidgetIds())
    }

    /**
     * Aparelho sem `FEATURE_APP_WIDGETS` não tem host: a faixa inteira some da
     * home em vez de virar uma fileira de molduras vazias — e o layout gravado
     * continua no disco, para voltar se o host voltar.
     */
    @Test
    fun `sem host a home nao desenha slot nenhum`() {
        val layout = WidgetLayout.EMPTY
            .addWidget(slotId = null, widget = widget(1), heightCells = 2)
            .addWidget(slotId = null, widget = widget(2), heightCells = 3)

        assertEquals(2, layout.slots.size)
        assertTrue(visibleSlots(layout, hostAvailable = false).isEmpty())
        assertEquals(layout.slots, visibleSlots(layout, hostAvailable = true))
    }

    @Test
    fun `com host e sem widget a faixa continua vazia`() {
        assertTrue(visibleSlots(WidgetLayout.EMPTY, hostAvailable = true).isEmpty())
        assertTrue(visibleSlots(WidgetLayout.EMPTY, hostAvailable = false).isEmpty())
    }

    @Test
    fun `cellsFor arredonda para cima`() {
        assertEquals(1, cellsFor(minHeightPx = 1, cellPx = 100))
        assertEquals(1, cellsFor(minHeightPx = 100, cellPx = 100))
        assertEquals(2, cellsFor(minHeightPx = 101, cellPx = 100))
        assertEquals(3, cellsFor(minHeightPx = 250, cellPx = 100))
    }

    @Test
    fun `cellsFor respeita os limites e o px invalido`() {
        assertEquals(MIN_CELLS, cellsFor(minHeightPx = 0, cellPx = 100))
        assertEquals(MIN_CELLS, cellsFor(minHeightPx = -10, cellPx = 100))
        assertEquals(MAX_CELLS, cellsFor(minHeightPx = 10_000, cellPx = 100))
        // Densidade estranha não pode virar divisão por zero.
        assertEquals(MIN_CELLS, cellsFor(minHeightPx = 500, cellPx = 0))
    }

    @Test
    fun `vai e volta pelo json`() {
        val layout = WidgetLayout.EMPTY
            .addWidget(null, PlacedWidget(10, "com.a/.W", userHash = 42), heightCells = 3)
            .addWidget(1, PlacedWidget(11, "com.b/.W"), heightCells = 2)
            .addWidget(null, PlacedWidget(12, "com.c/.W"), heightCells = 1)

        val text = json.encodeToString(layout)
        assertEquals(layout, json.decodeFromString<WidgetLayout>(text))
    }

    @Test
    fun `campo desconhecido e ignorado e o ausente vira padrao`() {
        val text = """
            {"slots":[{"id":7,"widgets":[{"appWidgetId":3,"provider":"com.a/.W","cor":"azul"}]}],
             "nextSlotId":8,"formatoNovo":true}
        """.trimIndent()

        val layout = json.decodeFromString<WidgetLayout>(text)
        val slot = layout.slots.single()
        assertEquals(7, slot.id)
        assertEquals(2, slot.heightCells)
        assertEquals(0, slot.activeIndex)
        assertEquals(0, slot.widgets.single().userHash)
        assertEquals(8, layout.nextSlotId)
    }

    @Test
    fun `json vazio da o layout vazio`() {
        assertEquals(WidgetLayout.EMPTY, json.decodeFromString<WidgetLayout>("{}"))
    }
}
