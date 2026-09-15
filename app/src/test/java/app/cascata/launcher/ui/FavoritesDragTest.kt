package app.cascata.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * O passo do arraste dos favoritos em lista: quantas linhas o dedo andou, e o
 * que acontece nas pontas e antes de a linha ter sido medida.
 */
class FavoritesDragTest {

    /** Uma linha medida: 100 px de altura mais 2 de espaçamento. */
    private val slot = 102f

    @Test
    fun `menos de meia linha nao muda de lugar`() {
        assertEquals(2, favoriteDropIndex(0f, slot, 2, 5))
        assertEquals(2, favoriteDropIndex(50f, slot, 2, 5))
        assertEquals(2, favoriteDropIndex(-50f, slot, 2, 5))
    }

    @Test
    fun `passada a meia linha o favorito troca de lugar`() {
        assertEquals(3, favoriteDropIndex(60f, slot, 2, 5))
        assertEquals(1, favoriteDropIndex(-60f, slot, 2, 5))
    }

    @Test
    fun `o passo e o tamanho da linha`() {
        assertEquals(4, favoriteDropIndex(2 * slot, slot, 2, 5))
        assertEquals(0, favoriteDropIndex(-2 * slot, slot, 2, 5))
    }

    @Test
    fun `nas pontas o alvo para na lista`() {
        assertEquals(5, favoriteDropIndex(40 * slot, slot, 2, 5))
        assertEquals(0, favoriteDropIndex(-40 * slot, slot, 2, 5))
    }

    @Test
    fun `linha ainda nao medida nao move nada`() {
        assertEquals(2, favoriteDropIndex(500f, 0f, 2, 5))
        assertEquals(2, favoriteDropIndex(500f, -3f, 2, 5))
    }

    @Test
    fun `sem favorito na mao ou sem lista o indice fica onde esta`() {
        assertEquals(-1, favoriteDropIndex(500f, slot, -1, 5))
        assertEquals(0, favoriteDropIndex(500f, slot, 0, -1))
    }

    @Test
    fun `o alvo nunca sai da lista`() {
        (-30..30).forEach { steps ->
            val to = favoriteDropIndex(steps * slot, slot, 1, 3)
            assert(to in 0..3) { "steps=$steps devolveu $to" }
        }
    }
}
