package app.cascata.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

private const val AMPLITUDE = 56f
private const val SIGMA = 64f

class IndexWaveTest {

    @Test
    fun `no dedo a letra anda a amplitude inteira`() {
        assertEquals(-AMPLITUDE, waveOffset(0f, AMPLITUDE, SIGMA), 0.001f)
    }

    @Test
    fun `longe do dedo o deslocamento tende a zero`() {
        assertTrue(abs(waveOffset(4 * SIGMA, AMPLITUDE, SIGMA)) < 0.01f)
        assertTrue(abs(waveOffset(-10 * SIGMA, AMPLITUDE, SIGMA)) < 0.01f)
    }

    @Test
    fun `a onda e simetrica em torno do dedo`() {
        listOf(8f, 30f, SIGMA, 150f).forEach { distance ->
            assertEquals(
                waveOffset(distance, AMPLITUDE, SIGMA),
                waveOffset(-distance, AMPLITUDE, SIGMA),
                0.0001f,
            )
        }
    }

    @Test
    fun `o deslocamento cai conforme a letra se afasta`() {
        val steps = listOf(0f, 16f, 32f, 64f, 128f).map { waveOffset(it, AMPLITUDE, SIGMA) }
        steps.zipWithNext { closer, farther -> assertTrue(closer < farther) }
    }

    @Test
    fun `a uma sigma de distancia sobra a fracao esperada da onda`() {
        // exp(-1) ≈ 0,3679: é o que define a largura da onda.
        assertEquals(-AMPLITUDE * 0.3679f, waveOffset(SIGMA, AMPLITUDE, SIGMA), 0.01f)
    }

    @Test
    fun `nunca passa da amplitude nem muda de sinal`() {
        listOf(-300f, -50f, 0f, 7f, 500f).forEach { distance ->
            val offset = waveOffset(distance, AMPLITUDE, SIGMA)
            assertTrue(offset <= 0f)
            assertTrue(offset >= -AMPLITUDE)
        }
    }

    @Test
    fun `sem largura ou sem amplitude nao ha onda`() {
        assertEquals(0f, waveOffset(0f, AMPLITUDE, 0f), 0f)
        assertEquals(0f, waveOffset(12f, AMPLITUDE, -8f), 0f)
        assertEquals(0f, waveOffset(0f, 0f, SIGMA), 0f)
    }
}

/**
 * O mapeamento entre o dedo e o alvo do índice alfabético: é ele que decide a
 * letra e que garante que a estrela (alvo 0) não roube uma letra nem sobre um
 * alvo fora da lista.
 */
class IndexTargetTest {

    @Test
    fun `sem medida ou sem letras nao ha alvo`() {
        assertEquals(-1, indexTargetAt(40f, 0, 5))
        assertEquals(-1, indexTargetAt(40f, 600, 0))
    }

    @Test
    fun `o topo da coluna e a estrela`() {
        // 5 letras + estrela = 6 fatias de 100 px numa coluna de 600.
        assertEquals(0, indexTargetAt(0f, 600, 5))
        assertEquals(0, indexTargetAt(99f, 600, 5))
        assertEquals(1, indexTargetAt(100f, 600, 5))
    }

    @Test
    fun `a ultima letra e alcancavel ate o fim da coluna`() {
        assertEquals(5, indexTargetAt(500f, 600, 5))
        assertEquals(5, indexTargetAt(599f, 600, 5))
        // O dedo chega ao pixel de baixo (e passa dele): continua na última letra.
        assertEquals(5, indexTargetAt(600f, 600, 5))
        assertEquals(5, indexTargetAt(900f, 600, 5))
    }

    @Test
    fun `dedo acima do topo fica na estrela`() {
        assertEquals(0, indexTargetAt(-50f, 600, 5))
    }

    @Test
    fun `todo alvo cabe na lista de letras`() {
        listOf(1, 2, 3, 26).forEach { letters ->
            (-200..1400 step 7).forEach { y ->
                val target = indexTargetAt(y.toFloat(), 1200, letters)
                assertTrue("y=$y letters=$letters", target in 0..letters)
            }
        }
    }

    @Test
    fun `com uma letra so a coluna e metade estrela e metade letra`() {
        assertEquals(0, indexTargetAt(99f, 200, 1))
        assertEquals(1, indexTargetAt(101f, 200, 1))
    }

    @Test
    fun `o centro do alvo cai no meio da fatia dele`() {
        assertEquals(50f, indexTargetCenter(0, 600, 5), 0.001f)
        assertEquals(150f, indexTargetCenter(1, 600, 5), 0.001f)
        assertEquals(550f, indexTargetCenter(5, 600, 5), 0.001f)
    }

    @Test
    fun `o centro do alvo e o meio da fatia que o toque devolve`() {
        val letters = 7
        val height = 1000
        (0..letters).forEach { target ->
            val center = indexTargetCenter(target, height, letters)
            assertEquals(target, indexTargetAt(center, height, letters))
        }
    }

    @Test
    fun `sem medida nao ha centro`() {
        assertEquals(0f, indexTargetCenter(3, 0, 5), 0f)
        assertEquals(0f, indexTargetCenter(0, 600, 0), 0f)
    }
}
