package app.cascata.launcher.ui.notifications

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Há quanto tempo a notificação chegou — sem texto nenhum.
 *
 * A função devolve o caso, não a frase: assim as palavras continuam em
 * `strings.xml` (e traduzíveis) e o teste afirma sobre dados, não sobre
 * português. Quem mapeia caso para string é a UI, em `NotificationInline`.
 */
sealed interface RelativeTime {
    /** Menos de um minuto. */
    data object Now : RelativeTime
    data class Minutes(val value: Int) : RelativeTime
    data class Hours(val value: Int) : RelativeTime
    data object Yesterday : RelativeTime
    data class Days(val value: Int) : RelativeTime
}

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 60 * MINUTE_MILLIS

/**
 * "ontem" é dia de calendário, não 24 h corridas: uma notificação das 23 h vista
 * à 1 h da manhã é de ontem, ainda que tenha duas horas de idade. Relógio
 * adiantado (chegada no futuro) cai em [RelativeTime.Now] em vez de virar conta
 * negativa.
 */
fun relativeTime(
    postTimeMillis: Long,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): RelativeTime {
    val elapsed = nowMillis - postTimeMillis
    if (elapsed < MINUTE_MILLIS) return RelativeTime.Now
    if (elapsed < HOUR_MILLIS) return RelativeTime.Minutes((elapsed / MINUTE_MILLIS).toInt())

    val days = ChronoUnit.DAYS.between(
        Instant.ofEpochMilli(postTimeMillis).atZone(zone).toLocalDate(),
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate(),
    )
    return when {
        days <= 0L -> RelativeTime.Hours((elapsed / HOUR_MILLIS).toInt())
        days == 1L -> RelativeTime.Yesterday
        else -> RelativeTime.Days(days.toInt())
    }
}
