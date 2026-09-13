package app.cascata.launcher.data.iconpack

/**
 * A parte pura do `appfilter.xml`: pares `component` -> `drawable` crus viram um
 * mapa `"pacote/classe" -> nome do drawable`. Fica sem Android de propósito — é
 * o único pedaço do suporte a pacotes de ícones que dá para testar em JVM.
 */

private const val COMPONENT_PREFIX = "ComponentInfo{"

/**
 * Normaliza e indexa. Entrada malformada é descartada em silêncio (arquivos de
 * pacote de ícones são escritos à mão e vêm com lixo); a **primeira** ocorrência
 * de um componente vence, que é como os launchers do formato se comportam.
 */
fun parseAppFilterEntries(items: Sequence<Pair<String, String>>): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    for ((rawComponent, rawDrawable) in items) {
        val component = normalizeComponent(rawComponent) ?: continue
        val drawable = rawDrawable.trim()
        if (drawable.isEmpty()) continue
        if (!result.containsKey(component)) result[component] = drawable
    }
    return result
}

/**
 * `ComponentInfo{pacote/classe}` (ou já `pacote/classe`) -> `pacote/classe`.
 * Devolve null quando não dá para ler. Classe relativa (`.Main`) é expandida com
 * o pacote, porque é o que `ComponentName` faz e há appfilters escritos assim.
 */
fun normalizeComponent(raw: String): String? {
    var value = raw.trim()
    if (value.isEmpty()) return null
    if (value.startsWith(COMPONENT_PREFIX)) {
        if (!value.endsWith("}")) return null
        value = value.substring(COMPONENT_PREFIX.length, value.length - 1).trim()
    } else if (value.contains('{') || value.contains('}')) {
        return null
    }
    val slash = value.indexOf('/')
    if (slash <= 0 || slash == value.length - 1) return null
    if (value.indexOf('/', slash + 1) >= 0) return null
    if (value.any { it.isWhitespace() }) return null
    val packageName = value.substring(0, slash)
    val className = value.substring(slash + 1)
    return if (className.startsWith('.')) "$packageName/$packageName$className" else value
}
