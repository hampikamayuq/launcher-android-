package app.cascata.launcher.ui

import kotlin.math.exp

/**
 * A matemática da onda do índice alfabético, sem Compose no meio: dá para
 * exercitar em JVM e para desenhar a onda por quadro sem recompor nada.
 *
 * Cada letra anda para a esquerda conforme a distância dela até o dedo, numa
 * gaussiana: no dedo o deslocamento é o máximo, e some depressa ao redor. O
 * sinal é negativo porque "para a esquerda" é X decrescente na tela.
 *
 * @param distancePx quanto a letra está abaixo (positivo) ou acima (negativo) do dedo.
 * @param amplitudePx o deslocamento no pico, sob o dedo.
 * @param sigmaPx a largura da onda: a uma distância de 1 sigma sobra ~37% dela.
 * @return o deslocamento horizontal, sempre em `-amplitudePx..0`.
 */
fun waveOffset(distancePx: Float, amplitudePx: Float, sigmaPx: Float): Float {
    // Sigma zero (ou negativa) seria divisão por zero: sem largura não há onda.
    if (sigmaPx <= 0f || amplitudePx == 0f) return 0f
    val ratio = distancePx / sigmaPx
    return -amplitudePx * exp(-ratio * ratio)
}

/** Nenhum alvo sob o dedo. O alvo 0 é a estrela; as letras vêm de 1 em diante. */
internal const val NO_TARGET = -1
internal const val STAR_TARGET = 0

/**
 * O alvo sob o dedo em [y] pixels a partir do topo de uma coluna de [height]
 * pixels com [letters] letras. A estrela e as letras dividem a coluna em
 * `letters + 1` fatias iguais — é o que `Arrangement.SpaceEvenly` desenha e o
 * que o toque assume.
 *
 * Devolve [NO_TARGET] enquanto a coluna não foi medida (ou não há letra
 * nenhuma), [STAR_TARGET] na primeira fatia e `1 + índice da letra` no resto.
 * Dedo fora da coluna cai no alvo da ponta mais próxima, e o resultado nunca
 * sai de `0..letters` — nenhuma chamada pode indexar fora da lista de letras.
 */
internal fun indexTargetAt(y: Float, height: Int, letters: Int): Int {
    if (height <= 0 || letters <= 0) return NO_TARGET
    val slot = height.toFloat() / (letters + 1)
    return (y / slot).toInt().coerceIn(STAR_TARGET, letters)
}

/**
 * O meio do alvo [target] (0 = a estrela) na mesma coluna de [indexTargetAt]:
 * é daí que sai a distância de cada letra até o dedo, e a posição da bolha.
 */
internal fun indexTargetCenter(target: Int, height: Int, letters: Int): Float {
    if (height <= 0 || letters <= 0) return 0f
    val slot = height.toFloat() / (letters + 1)
    return slot * (target + 0.5f)
}
