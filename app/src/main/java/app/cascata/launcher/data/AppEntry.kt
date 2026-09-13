package app.cascata.launcher.data

import android.content.ComponentName
import android.os.UserHandle
import java.text.Normalizer

/** Um app lançável, já com o rótulo normalizado para busca e seccionamento. */
data class AppEntry(
    val component: ComponentName,
    val user: UserHandle,
    val label: String,
    val normalizedLabel: String,
    val section: Char,
) {
    /** Chave estável entre reinicializações e entre perfis (trabalho/pessoal). */
    val key: String get() = "${component.flattenToShortString()}#${user.hashCode()}"
}

private val DIACRITICS = Regex("\\p{Mn}+")

/** "Ônibus  Já" -> "onibus ja". Usado na busca e na letra da seção. */
fun normalizeLabel(raw: String): String =
    Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase()

/** Letra da seção: A-Z para letras, '#' para qualquer outra coisa (dígitos, emoji, CJK). */
fun sectionOf(normalized: String): Char {
    val first = normalized.firstOrNull() ?: return '#'
    return if (first in 'a'..'z') first.uppercaseChar() else '#'
}
