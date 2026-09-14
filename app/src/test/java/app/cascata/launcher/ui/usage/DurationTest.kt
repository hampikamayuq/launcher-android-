package app.cascata.launcher.ui.usage

import org.junit.Assert.assertEquals
import org.junit.Test

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE

class DurationTest {

    @Test
    fun `abaixo de uma hora conta so minutos`() {
        assertEquals(UsageDuration.Minutes(35), formatDuration(35 * MINUTE))
        assertEquals(UsageDuration.Minutes(59), formatDuration(59 * MINUTE))
    }

    @Test
    fun `hora redonda nao mostra zero minutos`() {
        assertEquals(UsageDuration.Hours(1), formatDuration(HOUR))
        assertEquals(UsageDuration.Hours(3), formatDuration(3 * HOUR))
    }

    @Test
    fun `hora e minutos`() {
        assertEquals(UsageDuration.HoursMinutes(1, 20), formatDuration(HOUR + 20 * MINUTE))
    }

    @Test
    fun `segundos sao descartados, nunca arredondados para cima`() {
        assertEquals(UsageDuration.Minutes(59), formatDuration(HOUR - 1_000L))
        assertEquals(UsageDuration.Minutes(0), formatDuration(59_999L))
    }

    @Test
    fun `tempo vazio ou negativo e zero minuto`() {
        assertEquals(UsageDuration.Minutes(0), formatDuration(0L))
        assertEquals(UsageDuration.Minutes(0), formatDuration(-5 * MINUTE))
    }
}
