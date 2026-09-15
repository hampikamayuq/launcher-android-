package app.cascata.launcher.crash

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Quem coleta os Flows do app é a composição da home, e uma exceção que chega lá
 * derruba o processo — o aparelho fica sem tela inicial. Estes dois operadores
 * são a última rede antes disso.
 */
class SafeFlowTest {

    private fun falhando(quantos: Int) = flow {
        repeat(quantos) { emit(it) }
        throw IllegalStateException("o provedor recusou")
    }

    @Test
    fun `catchQuietly encerra o fluxo em vez de propagar`() = runBlocking {
        assertEquals(listOf(0, 1, 2), falhando(3).catchQuietly("Teste", "fonte").toList())
    }

    @Test
    fun `catchQuietly com falha na primeira emissao nao devolve nada`() = runBlocking {
        assertEquals(emptyList<Int>(), falhando(0).catchQuietly("Teste", "fonte").toList())
    }

    @Test
    fun `catchEmitting devolve o padrao no lugar da excecao`() = runBlocking {
        assertEquals(listOf(0, 1, -1), falhando(2).catchEmitting("Teste", "prefs", -1).toList())
    }

    @Test
    fun `fluxo que nao falha passa intacto pelos dois`() = runBlocking {
        val valores = flow { emit(7); emit(8) }
        assertEquals(listOf(7, 8), valores.catchQuietly("Teste", "fonte").toList())
        assertEquals(listOf(7, 8), valores.catchEmitting("Teste", "prefs", -1).toList())
    }
}
