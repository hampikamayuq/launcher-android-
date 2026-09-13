package app.cascata.launcher.data

import android.content.ComponentName
import android.os.UserHandle
import java.text.Normalizer

/** Um app lançável, já com o rótulo normalizado para busca e seccionamento. */
data class AppEntry(
    val component: ComponentName,
    val user: UserHandle,
    /** O rótulo que aparece na tela: o apelido quando existe, senão [originalLabel]. */
    val label: String,
    /** O rótulo que o sistema dá ao app — é para onde se volta ao apagar o apelido. */
    val originalLabel: String,
    /** Sempre derivado de [label]: renomear muda o lugar do app na lista. */
    val normalizedLabel: String,
    val section: Char,
    /** Perfil privado do Android 15: some da lista quando o perfil está trancado. */
    val isPrivateProfile: Boolean = false,
) {
    /** Chave estável entre reinicializações e entre perfis (trabalho/pessoal). */
    val key: String get() = "${component.flattenToShortString()}#${user.hashCode()}"
}

/** Cria a entrada a partir do rótulo do sistema, sem apelido. */
fun appEntry(
    component: ComponentName,
    user: UserHandle,
    originalLabel: String,
    isPrivateProfile: Boolean = false,
): AppEntry {
    val normalized = normalizeLabel(originalLabel)
    return AppEntry(
        component = component,
        user = user,
        label = originalLabel,
        originalLabel = originalLabel,
        normalizedLabel = normalized,
        section = sectionOf(normalized),
        isPrivateProfile = isPrivateProfile,
    )
}

/** Aplica (ou tira, com null/branco) o apelido, recalculando normalização e seção. */
fun AppEntry.withAlias(alias: String?): AppEntry {
    val shown = alias?.trim()?.takeIf { it.isNotEmpty() } ?: originalLabel
    if (shown == label) return this
    val normalized = normalizeLabel(shown)
    return copy(label = shown, normalizedLabel = normalized, section = sectionOf(normalized))
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
