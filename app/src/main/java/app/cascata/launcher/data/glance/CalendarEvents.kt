package app.cascata.launcher.data.glance

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Uma ocorrência da agenda. Sem nada de Android para que a ordenação abaixo seja
 * testável em JVM. [color] é ARGB, ou null quando a agenda não informa cor.
 */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val color: Int?,
)

/**
 * O que mostrar no card: eventos que ainda não terminaram, e dentro de cada dia
 * os de dia inteiro primeiro — "hoje é feriado" vale mais que a reunião das 15h.
 * Empate resolvido pelo id só para a ordem não depender da consulta.
 */
fun upcomingEvents(
    events: List<CalendarEvent>,
    nowMillis: Long,
    limit: Int = 3,
    zone: ZoneId = ZoneId.systemDefault(),
): List<CalendarEvent> = events
    .filter { it.endMillis > nowMillis }
    .sortedWith(
        compareBy(
            { it.dayOf(zone) },
            { !it.allDay },
            { it.beginMillis },
            { it.id },
        )
    )
    .take(limit.coerceAtLeast(0))

/**
 * O dia do evento. Evento de dia inteiro vem do provider em UTC (meia-noite a
 * meia-noite); lê-lo no fuso local o jogaria para o dia anterior ou seguinte.
 */
private fun CalendarEvent.dayOf(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(beginMillis)
        .atZone(if (allDay) ZoneOffset.UTC else zone)
        .toLocalDate()
