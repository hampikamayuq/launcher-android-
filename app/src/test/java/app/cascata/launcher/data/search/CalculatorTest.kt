package app.cascata.launcher.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CalculatorTest {

    private val ptBR = Locale.forLanguageTag("pt-BR")
    private val enUS = Locale.US

    @Test
    fun `soma simples`() {
        assertTrue(looksLikeExpression("2+2"))
        assertEquals("4", evaluate("2+2", enUS)?.formatted)
    }

    @Test
    fun `porcentagem de um numero`() {
        assertTrue(looksLikeExpression("15% de 200"))
        assertEquals("30", evaluate("15% de 200", enUS)?.formatted)
    }

    @Test
    fun `porcentagem sozinha e pos-fixa`() {
        assertEquals("0.15", evaluate("15%", enUS)?.formatted)
    }

    @Test
    fun `virgula decimal no locale que usa virgula`() {
        assertTrue(looksLikeExpression("3,5*2"))
        val result = evaluate("3,5*2", ptBR)
        assertEquals("7", result?.formatted)
        // O separador do resultado também é o do locale.
        assertEquals("1,75", evaluate("3,5/2", ptBR)?.formatted)
    }

    @Test
    fun `raiz quadrada e uma funcao conhecida`() {
        assertTrue(looksLikeExpression("sqrt(16)"))
        assertEquals("4", evaluate("sqrt(16)", enUS)?.formatted)
    }

    @Test
    fun `potencia com circunflexo`() {
        assertTrue(looksLikeExpression("2^10"))
        assertEquals("1024", evaluate("2^10", enUS)?.formatted)
    }

    @Test
    fun `divisao por zero nao tem resultado`() {
        assertTrue(looksLikeExpression("10/0"))
        assertNull(evaluate("10/0", enUS))
    }

    @Test
    fun `texto sem digito nao e expressao`() {
        assertFalse(looksLikeExpression("abc"))
        assertNull(evaluate("abc", enUS))
    }

    @Test
    fun `um numero solto nao e expressao`() {
        assertFalse(looksLikeExpression("42"))
        assertFalse(looksLikeExpression("3,5"))
        assertFalse(looksLikeExpression("-7.25"))
    }

    @Test
    fun `nome de app com numero nao e expressao`() {
        assertFalse(looksLikeExpression("1password"))
        assertFalse(looksLikeExpression("gta 5"))
        assertFalse(looksLikeExpression("dia 3 de maio"))
    }

    /**
     * Notação científica é lida como "um número solto" e recusada antes de
     * chegar ao avaliador: na busca, "1e400" é texto. Chamado direto, o
     * avaliador aceita e formata em notação científica.
     */
    @Test
    fun `1e400 nao passa pelo teste de expressao mas avalia`() {
        assertFalse(looksLikeExpression("1e400"))
        assertEquals("1E+400", evaluate("1e400", enUS)?.formatted)
    }

    @Test
    fun `vezes e dividido com os sinais de calculadora`() {
        assertEquals("12", evaluate("3×4", enUS)?.formatted)
        assertEquals("3", evaluate("12÷4", enUS)?.formatted)
        assertEquals("6", evaluate("2 x 3", enUS)?.formatted)
    }

    @Test
    fun `resultado sem zeros a direita e com no maximo dez casas`() {
        assertEquals("7", evaluate("3.5*2", enUS)?.formatted)
        assertEquals("3.3333333333", evaluate("10/3", enUS)?.formatted)
    }

    @Test
    fun `numero grande vira notacao normal ate 1e15`() {
        assertEquals("1000000000000000", evaluate("10^15", enUS)?.formatted)
        assertEquals("1E+16", evaluate("10^16", enUS)?.formatted)
    }

    @Test
    fun `expressao longa demais nem e tentada`() {
        val long = "1+".repeat(120) + "1"
        assertTrue(long.length > 200)
        assertFalse(looksLikeExpression(long))
        assertNull(evaluate(long, enUS))
    }

    @Test
    fun `a expressao devolvida e a query aparada`() {
        val result = evaluate("  2+2  ", enUS)
        assertNotNull(result)
        assertEquals("2+2", result?.expression)
    }
}
