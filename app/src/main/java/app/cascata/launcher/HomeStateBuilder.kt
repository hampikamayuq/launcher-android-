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

/** A partir daqui vale a tolerância a erro; abaixo disso, prefixo tem de ser exato. */
private const val FUZZY_MIN = 4

/**
 * Casa no começo do rótulo ou de qualquer palavra dele — nunca no meio de uma
 * palavra. Com quatro letras ou mais, aceita também uma letra errada: "whatsap"
 * acha "WhatsApp" e "telgram" acha "Telegram". Prefixos curtos continuam
 * exatos, senão duas letras casariam com meia gaveta.
 */
fun matchesQuery(normalizedLabel: String, needle: String): Boolean {
    if (normalizedLabel.startsWith(needle)) return true
    val words = normalizedLabel.split(' ')
    return words.any { it.startsWith(needle) } || words.any { fuzzyMatches(it, needle) }
}

/** Prefixo do rótulo, prefixo de palavra interna, e por último o que só casa com erro. */
fun queryRank(normalizedLabel: String, needle: String): Int = when {
    normalizedLabel.startsWith(needle) -> 0
    normalizedLabel.split(' ').any { it.startsWith(needle) } -> 1
    else -> 2
}

/**
 * A busca com erro compara a query com o começo da palavra. O tamanho do
 * pedaço varia de um para cada lado porque o erro pode ser uma letra a mais ou
 * a menos: "telgram" (7) só encontra "telegram" quando comparado com as 8
 * primeiras letras dela.
 */
private fun fuzzyMatches(word: String, needle: String): Boolean {
    if (needle.length < FUZZY_MIN || word.length < FUZZY_MIN) return false
    for (size in needle.length - 1..needle.length + 1) {
        if (size < 1 || size > word.length) continue
        if (editDistanceAtMostOne(needle, word.take(size))) return true
    }
    return false
}

/**
 * Distância de Damerau-Levenshtein <= 1 (troca, inserção, remoção ou duas
 * letras trocadas de lugar). Como o limite é 1, basta achar a primeira
 * diferença e conferir se o resto bate — não há matriz para alocar, e a busca
 * roda a cada tecla.
 */
fun editDistanceAtMostOne(a: String, b: String): Boolean {
    val diff = a.length - b.length
    if (diff > 1 || diff < -1) return false
    var i = 0
    while (i < a.length && i < b.length && a[i] == b[i]) i++
    if (i == a.length && i == b.length) return true
    return when {
        // Mesmo tamanho: uma troca de letra, ou duas letras invertidas.
        diff == 0 ->
            a.regionMatches(i + 1, b, i + 1, a.length - i - 1) ||
                (
                    i + 1 < a.length &&
                        a[i] == b[i + 1] && a[i + 1] == b[i] &&
                        a.regionMatches(i + 2, b, i + 2, a.length - i - 2)
                    )
        // "a" tem uma letra a mais: remover a[i] iguala as duas.
        diff == 1 -> a.regionMatches(i + 1, b, i, b.length - i)
        // "b" tem uma letra a mais: remover b[i].
        else -> b.regionMatches(i + 1, a, i, a.length - i)
    }
}

/** Move um item de lugar. Índice fora da lista devolve null — o arraste foi perdido. */
fun <E> moveItem(list: List<E>, fromIndex: Int, toIndex: Int): List<E>? {
    if (fromIndex !in list.indices || toIndex !in list.indices) return null
    if (fromIndex == toIndex) return list
    val copy = list.toMutableList()
    copy.add(toIndex, copy.removeAt(fromIndex))
    return copy
}
