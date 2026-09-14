package app.cascata.launcher.ui.usage

/**
 * Quanto tempo de uso, em horas e minutos — sem texto nenhum.
 *
 * Como em `RelativeTime`, a função devolve o caso e não a frase: as palavras
 * continuam em `strings.xml` (e traduzíveis) e o teste afirma sobre dados, não
 * sobre português. Quem mapeia caso para string é a UI, em `UsageText`.
 */
sealed interface UsageDuration {
    /** Menos de uma hora. Zero incluído: um app aberto por segundos dá "0 min". */
    data class Minutes(val value: Int) : UsageDuration
    /** Horas redondas: "2 h" em vez de "2 h 0 min". */
    data class Hours(val value: Int) : UsageDuration
    data class HoursMinutes(val hours: Int, val minutes: Int) : UsageDuration
}

private const val MINUTE_MILLIS = 60_000L

/**
 * Os segundos são descartados, não arredondados: 59 min 50 s é "59 min", e não
 * uma hora que ainda não passou — o limite por app conta em minutos inteiros, e
 * o card não pode mostrar mais tempo do que o gate enxerga.
 */
fun formatDuration(millis: Long): UsageDuration {
    val totalMinutes = (millis.coerceAtLeast(0L) / MINUTE_MILLIS).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> UsageDuration.Minutes(minutes)
        minutes == 0 -> UsageDuration.Hours(hours)
        else -> UsageDuration.HoursMinutes(hours, minutes)
    }
}
