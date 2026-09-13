package app.cascata.launcher.data.widgets

import kotlinx.serialization.Serializable

/**
 * Id do nosso host de widgets. É gravado pelo sistema junto com os ids alocados,
 * e volta em `EXTRA_HOST_ID` na restauração de backup — por isso é constante e
 * nunca muda entre versões.
 */
const val WIDGET_HOST_ID = 1024

/** Uma "célula" de altura. A largura é sempre a da tela: a home é uma coluna só. */
const val CELL_HEIGHT_DP = 72

const val MIN_CELLS = 1
const val MAX_CELLS = 8

/**
 * Um widget colocado na home. Guarda o mínimo para recriá-lo: o id que o host
 * alocou, o provedor achatado (`ComponentName.flattenToString()`) e o hash do
 * perfil, que distingue o mesmo provedor no perfil pessoal e no de trabalho.
 */
@Serializable
data class PlacedWidget(
    val appWidgetId: Int,
    val provider: String,
    val userHash: Int = 0,
)

/**
 * Uma faixa da home. Vários widgets no mesmo slot formam uma pilha — só o de
 * [activeIndex] aparece, os outros ficam a um swipe de distância.
 */
@Serializable
data class WidgetSlot(
    val id: Int,
    val heightCells: Int = 2,
    val widgets: List<PlacedWidget> = emptyList(),
    val activeIndex: Int = 0,
)

/**
 * O layout inteiro. [nextSlotId] só cresce: reaproveitar id de slot apagado faria
 * a UI confundir um slot novo com o que acabou de sair da tela.
 */
@Serializable
data class WidgetLayout(
    val slots: List<WidgetSlot> = emptyList(),
    val nextSlotId: Int = 1,
) {
    companion object {
        val EMPTY = WidgetLayout()
    }
}

/**
 * Acrescenta [widget]. Com [slotId] nulo (ou de um slot que não existe mais) abre
 * um slot novo no fim; com um slot válido, empilha nele e deixa o recém-chegado
 * visível — foi o que o usuário acabou de escolher.
 *
 * Ao empilhar, a altura do slot só cresce: o widget novo pode precisar de mais
 * células que os que já estavam ali, e encolher cortaria os antigos.
 */
fun WidgetLayout.addWidget(slotId: Int?, widget: PlacedWidget, heightCells: Int): WidgetLayout {
    val cells = heightCells.coerceIn(MIN_CELLS, MAX_CELLS)
    // O mesmo id não pode viver em dois lugares: adicionar de novo é mover.
    val base = if (slotOf(widget.appWidgetId) != null) removeWidget(widget.appWidgetId) else this
    val target = slotId?.let { id -> base.slots.firstOrNull { it.id == id } }
    if (target == null) {
        val slot = WidgetSlot(id = base.nextSlotId, heightCells = cells, widgets = listOf(widget))
        return base.copy(slots = base.slots + slot, nextSlotId = base.nextSlotId + 1)
    }
    val widgets = target.widgets + widget
    return base.replace(
        target.copy(
            heightCells = maxOf(target.heightCells, cells),
            widgets = widgets,
            activeIndex = widgets.lastIndex,
        ),
    )
}

/** Tira o widget de onde estiver. Slot que fica vazio some; o `activeIndex` é corrigido. */
fun WidgetLayout.removeWidget(appWidgetId: Int): WidgetLayout {
    val slots = slots.mapNotNull { slot ->
        if (slot.widgets.none { it.appWidgetId == appWidgetId }) return@mapNotNull slot
        val widgets = slot.widgets.filterNot { it.appWidgetId == appWidgetId }
        if (widgets.isEmpty()) null else slot.copy(widgets = widgets, activeIndex = slot.activeIndex.coerceIn(0, widgets.lastIndex))
    }
    return copy(slots = slots)
}

/** Nova altura do slot, dentro dos limites. */
fun WidgetLayout.resizeSlot(slotId: Int, cells: Int): WidgetLayout {
    val slot = slots.firstOrNull { it.id == slotId } ?: return this
    return replace(slot.copy(heightCells = cells.coerceIn(MIN_CELLS, MAX_CELLS)))
}

/** Sobe (−1) ou desce (+1) o slot. Nas bordas não faz nada em vez de dar a volta. */
fun WidgetLayout.moveSlot(slotId: Int, delta: Int): WidgetLayout {
    val from = slots.indexOfFirst { it.id == slotId }
    if (from < 0) return this
    val to = from + delta
    if (to < 0 || to > slots.lastIndex) return this
    val reordered = slots.toMutableList()
    reordered.add(to, reordered.removeAt(from))
    return copy(slots = reordered)
}

/** Qual da pilha aparece. Índice fora da faixa é aparado, não rejeitado. */
fun WidgetLayout.setActive(slotId: Int, index: Int): WidgetLayout {
    val slot = slots.firstOrNull { it.id == slotId } ?: return this
    if (slot.widgets.isEmpty()) return this
    return replace(slot.copy(activeIndex = index.coerceIn(0, slot.widgets.lastIndex)))
}

/**
 * Troca os ids antigos pelos que o sistema alocou depois de um restore. Id que
 * não aparece em [old] não foi restaurado (o app do widget não voltou, por
 * exemplo) e sai do layout — deixá-lo daria uma moldura vazia para sempre.
 */
fun WidgetLayout.remapIds(old: IntArray, new: IntArray): WidgetLayout {
    val map = HashMap<Int, Int>(old.size)
    for (i in 0 until minOf(old.size, new.size)) map[old[i]] = new[i]
    val slots = slots.mapNotNull { slot ->
        val widgets = slot.widgets.mapNotNull { widget ->
            map[widget.appWidgetId]?.let { widget.copy(appWidgetId = it) }
        }
        if (widgets.isEmpty()) null else slot.copy(widgets = widgets, activeIndex = slot.activeIndex.coerceIn(0, widgets.lastIndex))
    }
    return copy(slots = slots)
}

/** Todos os ids em uso, na ordem da tela. É o que o host precisa saber para limpar o resto. */
fun WidgetLayout.allWidgetIds(): List<Int> = slots.flatMap { slot -> slot.widgets.map { it.appWidgetId } }

/** Em que slot está o widget, se está em algum. */
fun WidgetLayout.slotOf(appWidgetId: Int): WidgetSlot? =
    slots.firstOrNull { slot -> slot.widgets.any { it.appWidgetId == appWidgetId } }

/**
 * Quantas células cobrem [minHeightPx]. Arredonda para cima: meia célula a menos
 * corta o widget, meia a mais só sobra espaço.
 */
fun cellsFor(minHeightPx: Int, cellPx: Int): Int {
    if (cellPx <= 0) return MIN_CELLS
    val cells = (minHeightPx + cellPx - 1) / cellPx
    return cells.coerceIn(MIN_CELLS, MAX_CELLS)
}

/** Troca um slot pelo de mesmo id, na mesma posição. */
private fun WidgetLayout.replace(slot: WidgetSlot): WidgetLayout =
    copy(slots = slots.map { if (it.id == slot.id) slot else it })
