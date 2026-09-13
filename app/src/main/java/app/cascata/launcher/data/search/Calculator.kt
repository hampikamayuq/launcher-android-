package app.cascata.launcher.data.search

import com.ezylang.evalex.Expression
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * O EvalEx é síncrono e roda na thread de quem chama. Em vez de um timeout (que
 * exigiria uma thread só para vigiar), limitamos o tamanho: expressão maior que
 * isso não é conta de launcher, é texto.
 */
private const val MAX_LENGTH = 200

/** Casas decimais que cabem numa linha de resultado. */
private const val MAX_SCALE = 10

/** Passado disso o número vira notação científica em vez de uma fileira de zeros. */
private val PLAIN_LIMIT = BigDecimal("1E+15")

/**
 * As funções do EvalEx que fazem sentido numa busca. A lista serve de filtro:
 * qualquer outra palavra na query significa que aquilo não é uma conta.
 */
private val FUNCTIONS = setOf(
    "abs", "ceiling", "fact", "floor", "log", "log10", "max", "min", "round", "sqrt", "sum",
    "average", "sin", "cos", "tan", "asin", "acos", "atan", "cot", "sec", "csc", "rad", "deg",
)

/** Palavras que não são função mas também não desqualificam: "15% de 200", "2 x 3". */
private val KEYWORDS = setOf("de", "of", "x")

private val WORD = Regex("[a-z]+")

/** Um número solto (com expoente) não é expressão: "42" é "42", não uma conta. */
private val PLAIN_NUMBER = Regex("^[+-]?\\d+([.,]\\d+)?([eE][+-]?\\d+)?$")

/** Só estes caracteres contam como "tem operador". */
private const val OPERATORS = "+-*/×÷^%()"

private val DECIMAL_COMMA = Regex("(?<=\\d),(?=\\d)")

/** "x" entre números (e não no meio de "max") é multiplicação. */
private val X_AS_TIMES = Regex("(?<=[0-9)\\s])x(?=[0-9(\\s])")

/** "15% de 200" — porcentagem pós-fixa aplicada a um segundo número. */
private val PERCENT_OF = Regex("(\\d+(?:\\.\\d+)?)\\s*%\\s*(?:de|of)\\s+")

/** "15%" sozinho — porcentagem pós-fixa, vira divisão por 100. */
private val PERCENT = Regex("(\\d+(?:\\.\\d+)?)\\s*%")

/** O resultado de uma conta: o que o usuário digitou, o valor exato e o texto na tela. */
data class CalculationResult(
    /** A query, sem espaços nas pontas — é o lado esquerdo do "=" na UI. */
    val expression: String,
    val value: BigDecimal,
    val formatted: String,
)

/**
 * A query parece uma conta? É este teste que decide se a linha de resultado
 * aparece; [evaluate] só é chamado depois de ele passar.
 *
 * Precisa de dígito e de pelo menos um operador (ou de uma função conhecida).
 * Um número solto — inclusive em notação científica, como "1e400" — é recusado:
 * "42" na busca é o nome de um app, não uma conta.
 */
fun looksLikeExpression(query: String): Boolean {
    val raw = query.trim()
    if (raw.isEmpty() || raw.length > MAX_LENGTH) return false
    if (raw.none { it.isDigit() }) return false
    val lower = raw.lowercase()
    if (PLAIN_NUMBER.matches(lower.replace(" ", ""))) return false
    val words = WORD.findAll(lower).map { it.value }.toList()
    // Letra que não seja função conhecida nem "de"/"of"/"x": é nome de app.
    if (words.any { it !in FUNCTIONS && it !in KEYWORDS }) return false
    val hasOperator = lower.any { it in OPERATORS } || DECIMAL_COMMA.containsMatchIn(lower)
    return hasOperator || words.any { it in FUNCTIONS }
}

/**
 * Avalia a conta. Devolve null para qualquer erro — divisão por zero, parêntese
 * solto, função que não existe: na busca, "não deu" é simplesmente não mostrar
 * a linha.
 */
fun evaluate(query: String, locale: Locale = Locale.getDefault()): CalculationResult? {
    val raw = query.trim()
    if (raw.isEmpty() || raw.length > MAX_LENGTH) return null
    val prepared = prepare(raw, locale)
    val value = runCatching { Expression(prepared).evaluate().value as? BigDecimal }.getOrNull()
        ?: return null
    return CalculationResult(raw, value, formatValue(value, locale))
}

/** Traduz o que o usuário digita para o dialeto do EvalEx. */
private fun prepare(raw: String, locale: Locale): String {
    var text = raw.lowercase()
    // Vírgula decimal só quando o locale usa vírgula e não há ponto na conta —
    // assim "3,5*2" funciona em pt-BR sem estragar "1.234,5" de quem mistura.
    if (usesDecimalComma(locale) && '.' !in text) {
        text = DECIMAL_COMMA.replace(text, ".")
    }
    text = text.replace('×', '*').replace('÷', '/')
    text = X_AS_TIMES.replace(text, "*")
    // O EvalEx lê "%" como módulo; aqui ele é sempre pós-fixo, do jeito que se
    // digita numa calculadora: "15% de 200" = 0,15*200 e "15%" = 0,15.
    text = PERCENT_OF.replace(text) { "(${it.groupValues[1]}/100)*" }
    text = PERCENT.replace(text) { "(${it.groupValues[1]}/100)" }
    return text.trim()
}

private fun usesDecimalComma(locale: Locale): Boolean =
    DecimalFormatSymbols.getInstance(locale).decimalSeparator == ','

/**
 * Sem zeros à direita, no máximo [MAX_SCALE] casas e com o separador do locale.
 * Números gigantes (acima de 1e15) saem em notação científica: uma fileira de 400
 * dígitos não cabe na linha nem ajuda ninguém.
 */
private fun formatValue(value: BigDecimal, locale: Locale): String {
    val rounded = if (value.scale() > MAX_SCALE) {
        value.setScale(MAX_SCALE, RoundingMode.HALF_UP)
    } else {
        value
    }
    val trimmed = rounded.stripTrailingZeros()
    val text = if (trimmed.abs() <= PLAIN_LIMIT) trimmed.toPlainString() else trimmed.toString()
    return text.replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)
}
