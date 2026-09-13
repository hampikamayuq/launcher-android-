package app.cascata.launcher.ui.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

private val UTC = ZoneId.of("UTC")

/** Meia-noite UTC de um dia qualquer, para as contas ficarem legíveis. */
private const val DAY_1 = 1_700_000_000_000L - 1_700_000_000_000L % 86_400_000L
private const val MINUTE = 60_000L
private const val HOUR = 3_600_000L
private const val DAY = 86_400_000L

class RelativeTimeTest {

    @Test
    fun `menos de um minuto e agora`() {
        val now = DAY_1 + 10 * HOUR
        assertEquals(RelativeTime.Now, relativeTime(now - 59_000L, now, UTC))
    }

    @Test
    fun `relogio adiantado nao vira conta negativa`() {
        val now = DAY_1 + 10 * HOUR
        assertEquals(RelativeTime.Now, relativeTime(now + 5 * MINUTE, now, UTC))
    }

    @Test
    fun `entre um minuto e uma hora conta minutos`() {
        val now = DAY_1 + 10 * HOUR
        assertEquals(RelativeTime.Minutes(1), relativeTime(now - MINUTE, now, UTC))
        assertEquals(RelativeTime.Minutes(59), relativeTime(now - 59 * MINUTE, now, UTC))
    }

    @Test
    fun `no mesmo dia conta horas`() {
        val now = DAY_1 + 23 * HOUR
        assertEquals(RelativeTime.Hours(1), relativeTime(now - HOUR, now, UTC))
        assertEquals(RelativeTime.Hours(21), relativeTime(DAY_1 + 2 * HOUR, now, UTC))
    }

    @Test
    fun `ontem e dia de calendario, nao vinte e quatro horas`() {
        // Uma da manhã olhando para as onze da noite: duas horas de idade, ontem.
        val now = DAY_1 + DAY + HOUR
        assertEquals(RelativeTime.Yesterday, relativeTime(DAY_1 + 23 * HOUR, now, UTC))
    }

    @Test
    fun `antes de ontem conta dias`() {
        val now = DAY_1 + 3 * DAY + 10 * HOUR
        assertEquals(RelativeTime.Days(3), relativeTime(DAY_1 + 9 * HOUR, now, UTC))
    }
}
