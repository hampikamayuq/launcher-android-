package app.cascata.launcher.data.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

/** Meia-noite de um dia qualquer em UTC: as contas ficam legíveis. */
private const val MIDNIGHT = 1_700_000_000_000L - 1_700_000_000_000L % DAY

private fun resumed(packageName: String, at: Long) = UsageEvent(packageName, UsageEventKind.RESUMED, at)
private fun paused(packageName: String, at: Long) = UsageEvent(packageName, UsageEventKind.PAUSED, at)

class UsageAggregationTest {

    private val start = MIDNIGHT
    private val end = MIDNIGHT + 12 * HOUR

    @Test
    fun `sessao simples soma o intervalo`() {
        val events = listOf(
            resumed("a", start + HOUR),
            paused("a", start + HOUR + 20 * MINUTE),
        )
        val usage = aggregateForeground(events, start, end)
        assertEquals(listOf(AppUsage("a", 20 * MINUTE, start + HOUR + 20 * MINUTE)), usage)
    }

    @Test
    fun `eventos fora de ordem sao ordenados antes de somar`() {
        val events = listOf(
            paused("a", start + 2 * HOUR),
            resumed("a", start + HOUR),
        )
        assertEquals(HOUR, aggregateForeground(events, start, end).single().totalMillis)
    }

    @Test
    fun `resumido duas vezes nao conta em dobro`() {
        val events = listOf(
            resumed("a", start + HOUR),
            resumed("a", start + 2 * HOUR),
            paused("a", start + 3 * HOUR),
        )
        assertEquals(2 * HOUR, aggregateForeground(events, start, end).single().totalMillis)
    }

    @Test
    fun `pausado sem resumido conta a partir do inicio da janela`() {
        val events = listOf(paused("a", start + 30 * MINUTE))
        val usage = aggregateForeground(events, start, end).single()
        assertEquals(30 * MINUTE, usage.totalMillis)
        assertEquals(start + 30 * MINUTE, usage.lastUsedMillis)
    }

    @Test
    fun `sessao aberta no fim conta ate o fim da janela`() {
        val events = listOf(resumed("a", end - 15 * MINUTE))
        val usage = aggregateForeground(events, start, end).single()
        assertEquals(15 * MINUTE, usage.totalMillis)
        assertEquals(end, usage.lastUsedMillis)
    }

    @Test
    fun `sessao que cruza a meia-noite conta so o pedaco de hoje`() {
        // Ontem às 23h abriu o app; hoje, uma hora depois da meia-noite, fechou.
        val ontem = start - HOUR
        val events = listOf(resumed("a", ontem), paused("a", start + HOUR))
        assertEquals(HOUR, aggregateForeground(events, start, end).single().totalMillis)
    }

    @Test
    fun `dois apps alternando somam cada um o seu`() {
        val events = listOf(
            resumed("a", start),
            paused("a", start + 10 * MINUTE),
            resumed("b", start + 10 * MINUTE),
            paused("b", start + 40 * MINUTE),
            resumed("a", start + 40 * MINUTE),
            paused("a", start + 45 * MINUTE),
        )
        assertEquals(
            listOf("b" to 30 * MINUTE, "a" to 15 * MINUTE),
            aggregateForeground(events, start, end).map { it.packageName to it.totalMillis },
        )
    }

    @Test
    fun `troca de app sem pausar fecha a sessao do anterior`() {
        // Falta o PAUSED de "a": o RESUMED de "b" não fecha nada dele, e a sessão
        // de "a" só termina quando ele mesmo volta ou a janela acaba.
        val events = listOf(
            resumed("a", start),
            resumed("b", start + 10 * MINUTE),
            paused("b", start + 20 * MINUTE),
            resumed("a", start + 20 * MINUTE),
            paused("a", start + 25 * MINUTE),
        )
        val usage = aggregateForeground(events, start, end).associate { it.packageName to it.totalMillis }
        assertEquals(25 * MINUTE, usage["a"])
        assertEquals(10 * MINUTE, usage["b"])
    }

    @Test
    fun `app aberto e fechado no mesmo instante some da lista`() {
        val events = listOf(resumed("a", start + HOUR), paused("a", start + HOUR))
        assertEquals(emptyList<AppUsage>(), aggregateForeground(events, start, end))
    }

    @Test
    fun `eventos que nao sao de tela sao ignorados`() {
        val events = listOf(
            UsageEvent("a", UsageEventKind.OTHER, start + HOUR),
            resumed("a", start + HOUR),
            paused("a", start + 2 * HOUR),
        )
        assertEquals(HOUR, aggregateForeground(events, start, end).single().totalMillis)
    }

    @Test
    fun `janela invertida nao produz tempo`() {
        val events = listOf(resumed("a", start), paused("a", end))
        assertEquals(emptyList<AppUsage>(), aggregateForeground(events, end, start))
    }

    @Test
    fun `ordem e por tempo e o empate pelo nome`() {
        val events = listOf(
            resumed("b", start), paused("b", start + 10 * MINUTE),
            resumed("a", start + 10 * MINUTE), paused("a", start + 20 * MINUTE),
            resumed("c", start + 20 * MINUTE), paused("c", start + HOUR),
        )
        assertEquals(
            listOf("c", "a", "b"),
            aggregateForeground(events, start, end).map { it.packageName },
        )
    }

    @Test
    fun `o inicio de hoje e a meia-noite do fuso`() {
        val saoPaulo = ZoneId.of("America/Sao_Paulo")
        val now = MIDNIGHT + 3 * HOUR
        val startOfDay = startOfToday(now, saoPaulo)
        assertTrue(startOfDay <= now)
        assertEquals(0, (now - startOfDay) % MINUTE)
        // Meia-noite em São Paulo (UTC-3) é 3h UTC: o dia começou agora mesmo.
        assertEquals(now, startOfDay)
    }

    @Test
    fun `o inicio de hoje nao anda dentro do mesmo dia`() {
        val utc = ZoneId.of("UTC")
        assertEquals(MIDNIGHT, startOfToday(MIDNIGHT, utc))
        assertEquals(MIDNIGHT, startOfToday(MIDNIGHT + 23 * HOUR, utc))
        assertEquals(MIDNIGHT + DAY, startOfToday(MIDNIGHT + DAY, utc))
    }

    @Test
    fun `sem limite o app abre direto`() {
        assertFalse(shouldPause(5 * HOUR, null))
        assertFalse(shouldPause(5 * HOUR, 0))
        assertFalse(shouldPause(5 * HOUR, -10))
    }

    @Test
    fun `pausa quando o usado alcanca o limite`() {
        assertFalse(shouldPause(9 * MINUTE, 10))
        assertTrue(shouldPause(10 * MINUTE, 10))
        assertTrue(shouldPause(11 * MINUTE, 10))
    }
}
