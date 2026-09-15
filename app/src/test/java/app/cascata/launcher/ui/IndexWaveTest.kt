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
