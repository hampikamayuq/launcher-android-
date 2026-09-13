package app.cascata.launcher

import app.cascata.launcher.data.normalizeLabel

/** Uma linha da lista: cabeçalho de seção ou app. */
sealed interface Row<out T> {
    data class Header(val letter: Char) : Row<Nothing>
    data class App<T>(val entry: T, val favorite: Boolean) : Row<T>
}

/** O que a montagem devolve: as linhas prontas mais as listas derivadas. */
data class HomeList<T>(
    val rows: List<Row<T>> = emptyList(),
    /** Letra -> índice da linha onde a seção começa. Alimenta o índice alfabético. */
    val sectionIndex: Map<Char, Int> = emptyMap(),
    val favorites: List<T> = emptyList(),
    val hidden: List<T> = emptyList(),
)

/**
 * Monta a lista da home sem saber o que é um app: recebe as leituras por lambdas,
 * então dá para exercitar tudo em JVM, sem ComponentName nem UserHandle.
 */
class HomeStateBuilder<T>(
    private val key: (T) -> String,
    private val label: (T) -> String,
    private val normalized: (T) -> String,
    private val section: (T) -> Char,
    private val withAlias: (T, String?) -> T,
    /** Ordenação dos rótulos; no app é um Collator, nos testes a natural serve. */
    private val compareLabels: Comparator<String> = Comparator { a, b -> a.compareTo(b) },
) {

    fun build(
        apps: List<T>,
        favoriteKeys: List<String>,
        hiddenKeys: Set<String>,
        aliases: Map<String, String>,
        query: String,
    ): HomeList<T> {
        // Apelido antes de ordenar: um app renomeado muda de lugar (e de seção).
        val named = apps.map { withAlias(it, aliases[key(it)]) }
            .sortedWith { a, b -> compareLabels.compare(label(a), label(b)) }

        val hidden = named.filter { key(it) in hiddenKeys }
        val visible = named.filterNot { key(it) in hiddenKeys }

        val byKey = visible.associateBy(key)
        val favorites = favoriteKeys.mapNotNull(byKey::get)
        val favoriteSet = favoriteKeys.toSet()

        val needle = normalizeLabel(query)
        if (needle.isNotEmpty()) {
            // Oculto não aparece na busca: quem escondeu não quer ver nem procurando.
            val matches = visible
                .filter { matchesQuery(normalized(it), needle) }
                .sortedWith(compareBy({ queryRank(normalized(it), needle) }, { normalized(it) }))
            return HomeList(
                rows = matches.map { Row.App(it, key(it) in favoriteSet) },
                favorites = favorites,
                hidden = hidden,
            )
        }

        val rows = ArrayList<Row<T>>(visible.size + 32)
        val index = LinkedHashMap<Char, Int>()
        var last: Char? = null
        for (app in visible) {
            val letter = section(app)
            if (letter != last) {
                index[letter] = rows.size
                rows += Row.Header(letter)
                last = letter
            }
            rows += Row.App(app, key(app) in favoriteSet)
        }
        return HomeList(rows = rows, sectionIndex = index, favorites = favorites, hidden = hidden)
    }
}

/** Casa no começo do rótulo ou de qualquer palavra dele — nunca no meio de uma palavra. */
fun matchesQuery(normalizedLabel: String, needle: String): Boolean =
    normalizedLabel.startsWith(needle) ||
        normalizedLabel.split(' ').any { it.startsWith(needle) }

/** Prefixo do rótulo inteiro vem antes de prefixo de palavra interna. */
fun queryRank(normalizedLabel: String, needle: String): Int =
    if (normalizedLabel.startsWith(needle)) 0 else 1

/** Move um item de lugar. Índice fora da lista devolve null — o arraste foi perdido. */
fun <E> moveItem(list: List<E>, fromIndex: Int, toIndex: Int): List<E>? {
    if (fromIndex !in list.indices || toIndex !in list.indices) return null
    if (fromIndex == toIndex) return list
    val copy = list.toMutableList()
    copy.add(toIndex, copy.removeAt(fromIndex))
    return copy
}
