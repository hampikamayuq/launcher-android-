package app.cascata.launcher.data.usage

import java.time.Instant
import java.time.ZoneId

/** O que interessa de um evento de uso: entrou em primeiro plano, ou saiu. */
enum class UsageEventKind { RESUMED, PAUSED, OTHER }

/**
 * Um evento já traduzido do `UsageEvents.Event` do Android. Sem nada de Android
 * para que a soma abaixo seja testável em JVM.
 */
data class UsageEvent(
    val packageName: String,
    val kind: UsageEventKind,
    val timestampMillis: Long,
)

/** Quanto tempo um app passou em primeiro plano na janela, e quando terminou. */
data class AppUsage(
    val packageName: String,
    val totalMillis: Long,
    val lastUsedMillis: Long,
)

/**
 * Soma o tempo de primeiro plano por pacote dentro de `[windowStart, windowEnd]`.
 *
 * O sistema não entrega "sessões", entrega eventos soltos — e entrega eventos
 * que faltam. As regras existem para nenhum buraco virar tempo inventado nem
 * tempo perdido:
 *
 * - RESUMED abre a sessão do pacote. Se já havia uma aberta (o PAUSED sumiu),
 *   ela é fechada no instante do novo RESUMED, nunca contada em dobro.
 * - PAUSED fecha a sessão aberta. Sem RESUMED antes, a sessão começou antes da
 *   janela: conta a partir de [windowStart].
 * - Sessão ainda aberta no fim conta até [windowEnd] — é o app em uso agora.
 *
 * Resultado ordenado por tempo, sem os zerados (abrir e fechar no mesmo
 * milissegundo não é uso).
 */
fun aggregateForeground(
    events: List<UsageEvent>,
    windowStart: Long,
    windowEnd: Long,
): List<AppUsage> {
    if (windowEnd <= windowStart) return emptyList()

    val open = HashMap<String, Long>()
    val total = HashMap<String, Long>()
    val lastUsed = HashMap<String, Long>()

    fun close(packageName: String, start: Long, end: Long) {
        val duration = (end - start).coerceAtLeast(0L)
        total[packageName] = (total[packageName] ?: 0L) + duration
        lastUsed[packageName] = maxOf(lastUsed[packageName] ?: 0L, end)
    }

    events.sortedBy { it.timestampMillis }.forEach { event ->
        // Evento fora da janela seria tempo de outro dia; a borda é o limite.
        val at = event.timestampMillis.coerceIn(windowStart, windowEnd)
        when (event.kind) {
            UsageEventKind.RESUMED -> {
                open.remove(event.packageName)?.let { close(event.packageName, it, at) }
                open[event.packageName] = at
            }
            UsageEventKind.PAUSED -> close(event.packageName, open.remove(event.packageName) ?: windowStart, at)
            UsageEventKind.OTHER -> Unit
        }
    }

    open.forEach { (packageName, start) -> close(packageName, start, windowEnd) }

    return total.entries
        .filter { it.value > 0L }
        .map { (packageName, millis) -> AppUsage(packageName, millis, lastUsed[packageName] ?: 0L) }
        // Empates desempatados por nome para a ordem não depender do HashMap.
        .sortedWith(compareByDescending<AppUsage> { it.totalMillis }.thenBy { it.packageName })
}

/** Meia-noite de hoje no fuso do aparelho: o início da janela de "uso hoje". */
fun startOfToday(nowMillis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(nowMillis)
        .atZone(zone)
        .toLocalDate()
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

/**
 * A decisão do gate de abertura, isolada do Android: sem limite (ou limite não
 * positivo, que é como se apaga um) o app abre direto.
 */
fun shouldPause(usedMillis: Long, limitMinutes: Int?): Boolean {
    val limit = limitMinutes?.takeIf { it > 0 } ?: return false
    return usedMillis >= limit * 60_000L
}
