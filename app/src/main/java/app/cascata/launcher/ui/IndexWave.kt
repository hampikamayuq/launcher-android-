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
