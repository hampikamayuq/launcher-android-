package app.cascata.launcher.data.glance

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

private val UTC = ZoneId.of("UTC")

/** Meia-noite UTC do dia 1, para as contas ficarem legíveis. */
private const val DAY_1 = 1_700_000_000_000L - 1_700_000_000_000L % 86_400_000L
private const val HOUR = 3_600_000L
private const val DAY = 86_400_000L

class CalendarEventsTest {

    private fun event(
        id: Long,
        begin: Long,
        end: Long = begin + HOUR,
        allDay: Boolean = false,
    ) = CalendarEvent(id, "evento $id", begin, end, allDay, color = null)

    @Test
    fun `evento que ja terminou sai da lista`() {
        val now = DAY_1 + 10 * HOUR
        val events = listOf(
            event(1, DAY_1 + 8 * HOUR, DAY_1 + 9 * HOUR),
            event(2, DAY_1 + 11 * HOUR),
        )
        assertEquals(listOf(2L), upcomingEvents(events, now, zone = UTC).map { it.id })
    }

    @Test
    fun `evento em andamento continua na lista`() {
        val now = DAY_1 + 10 * HOUR
        val events = listOf(event(1, DAY_1 + 9 * HOUR, DAY_1 + 11 * HOUR))
        assertEquals(listOf(1L), upcomingEvents(events, now, zone = UTC).map { it.id })
    }

    @Test
    fun `dia inteiro vem antes dos eventos do mesmo dia`() {
        val now = DAY_1 + 9 * HOUR
        val events = listOf(
            event(1, DAY_1 + 10 * HOUR),
            event(2, DAY_1, DAY_1 + DAY, allDay = true),
            event(3, DAY_1 + 15 * HOUR),
        )
        assertEquals(listOf(2L, 1L, 3L), upcomingEvents(events, now, zone = UTC).map { it.id })
    }

    @Test
    fun `o dia inteiro de amanha nao passa na frente de hoje`() {
        val now = DAY_1 + 9 * HOUR
        val events = listOf(
            event(1, DAY_1 + DAY, DAY_1 + 2 * DAY, allDay = true),
            event(2, DAY_1 + 10 * HOUR),
        )
        assertEquals(listOf(2L, 1L), upcomingEvents(events, now, zone = UTC).map { it.id })
    }

    @Test
    fun `o limite corta o excedente`() {
        val now = DAY_1
        val events = (1L..5L).map { event(it, DAY_1 + it * HOUR) }
        assertEquals(listOf(1L, 2L), upcomingEvents(events, now, limit = 2, zone = UTC).map { it.id })
        assertEquals(emptyList<Long>(), upcomingEvents(events, now, limit = 0, zone = UTC).map { it.id })
    }

    @Test
    fun `lista vazia continua vazia`() {
        assertEquals(emptyList<CalendarEvent>(), upcomingEvents(emptyList(), DAY_1, zone = UTC))
    }

    @Test
    fun `mesma hora e desempatada pelo id`() {
        val now = DAY_1
        val events = listOf(event(9, DAY_1 + HOUR), event(4, DAY_1 + HOUR))
        assertEquals(listOf(4L, 9L), upcomingEvents(events, now, zone = UTC).map { it.id })
    }
}
