package app.cascata.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.cascata.launcher.R
import app.cascata.launcher.data.theme.IndexStyle
import kotlin.math.roundToInt

/** Largura da coluna de letras. A mesma que a home reserva à direita da lista. */
internal val INDEX_WIDTH = 28.dp

/** Deslocamento da letra que está exatamente sob o dedo. */
private val WAVE_AMPLITUDE = 56.dp

/** Largura da onda: a uma sigma do dedo já sobrou pouco mais de um terço dela. */
private val WAVE_SIGMA = 64.dp

/** Quanto a letra do pico cresce, em fração do tamanho normal. */
private const val WAVE_SCALE = 0.7f

/** A bolha que repete a letra escolhida, longe do dedo que a esconderia. */
private val BUBBLE_SIZE = 56.dp

/**
 * Quanto a bolha fica à esquerda da coluna. É a amplitude mais o diâmetro dela:
 * assim ela para logo antes da letra do pico, que já andou a amplitude inteira
 * — a bolha acompanha a letra em vez de cobri-la.
 */
private val BUBBLE_SHIFT = WAVE_AMPLITUDE + BUBBLE_SIZE

/** O que a onda precisa saber entre um quadro e o outro, sem recompor nada. */
private class IndexTouch {
    /** Altura da coluna, para converter alvo em posição e posição em alvo. */
    var height by mutableIntStateOf(0)

    /** Onde o dedo está agora, em pixels a partir do topo da coluna. */
    var fingerY by mutableFloatStateOf(0f)

    /** [NO_TARGET], [STAR_TARGET] ou 1 + o índice da letra. */
    var target by mutableIntStateOf(NO_TARGET)
}

/**
 * Índice alfabético lateral: arrastar o dedo percorre as letras e leva a lista
 * junto. É o atalho que substitui rolar centenas de apps — cada letra presente
 * vira um alvo, e a letra sob o dedo é destacada.
 *
 * No topo, acima das letras, a estrela é a tela inicial: tocá-la (ou arrastar
 * até ela) sai do modo filtrado e volta ao começo da lista, onde ficam os
 * favoritos.
 *
 * Em [IndexStyle.WAVE] a coluna se curva ao redor do dedo — cada letra anda
 * para a esquerda conforme a distância até ele ([waveOffset]) — e uma bolha
 * repete a letra escolhida ao lado. O desenho todo sai de um `graphicsLayer`
 * que lê o estado do toque: o dedo andando não recompõe a coluna, só refaz a
 * camada de cada letra. Em [IndexStyle.STRAIGHT] a coluna é reta e não há bolha.
 */
@Composable
fun AlphabetIndex(
    letters: List<Char>,
    /** Em onda, filtra a lista pela seção; reto, rola até ela. Quem decide é a home. */
    onLetterFocused: (Char) -> Unit,
    /** A estrela: sair do modo filtrado e voltar ao topo. */
    onHome: () -> Unit,
    style: IndexStyle,
    modifier: Modifier = Modifier,
    /**
     * Só as prévias da loja usam: layoutlib renderiza um quadro só e não tem
     * dedo nenhum, então a letra em foco (a onda e a bolha) vem de fora.
     */
    previewLetter: Char? = null,
) {
    if (letters.isEmpty()) return

    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val indexLabel = stringResource(R.string.alphabet_index)
    val homeLabel = stringResource(R.string.index_home)
    val touch = remember { IndexTouch() }
    val focus by rememberUpdatedState(onLetterFocused)
    val home by rememberUpdatedState(onHome)

    val amplitudePx = with(density) { WAVE_AMPLITUDE.toPx() }
    val sigmaPx = with(density) { WAVE_SIGMA.toPx() }
    val bubbleShiftPx = with(density) { BUBBLE_SHIFT.toPx() }
    val bubbleHalfPx = with(density) { (BUBBLE_SIZE / 2).toPx() }

    // A prévia entra pelo mesmo caminho do dedo, sem dedo nenhum: o alvo é fixo
    // e a "posição do dedo" é o meio da letra pedida.
    val previewTarget = previewLetter?.let { letters.indexOf(it) }?.takeIf { it >= 0 }?.plus(1)
    // A lista de letras encolhe quando um app some (desinstalado, escondido,
    // renomeado): um alvo guardado do arraste anterior apontaria além dela.
    val target = (previewTarget ?: touch.target).takeIf { it <= letters.size } ?: NO_TARGET

    /** Onde o dedo está — ou onde ele estaria, na prévia. Lido dentro das camadas. */
    fun fingerY(): Float = if (previewTarget != null) {
        indexTargetCenter(previewTarget, touch.height, letters.size)
    } else {
        touch.fingerY
    }

    /** Quanto da onda está desenhada agora: some suave quando o dedo sai. */
    val wave = animateFloatAsState(
        targetValue = if (style == IndexStyle.WAVE && target != NO_TARGET) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "indexWave",
    )

    // A prévia é um quadro só: a animação não teria tempo de sair do zero, e a
    // onda sairia reta na foto.
    fun waveStrength(): Float = when {
        previewTarget == null -> wave.value
        style == IndexStyle.WAVE -> 1f
        else -> 0f
    }

    /** Um alvo novo sob o dedo: avisa a home e devolve o toquinho de resposta. */
    fun moveTo(next: Int) {
        if (next == NO_TARGET || next == touch.target) return
        touch.target = next
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (next == STAR_TARGET) home() else focus(letters[next - 1])
    }

    Box(modifier = modifier.fillMaxHeight().width(INDEX_WIDTH)) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .onSizeChanged { touch.height = it.height }
                .semantics { contentDescription = indexLabel }
                .pointerInput(letters, style) {
                    // O detector não avisa quando o próprio bloco é reiniciado no
                    // meio de um arraste (a lista de letras mudou): sem isto o
                    // alvo ficaria preso, com a bolha em cena e a lista filtrada.
                    touch.target = NO_TARGET
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            touch.fingerY = offset.y
                            moveTo(indexTargetAt(offset.y, touch.height, letters.size))
                        },
                        onDragEnd = { touch.target = NO_TARGET },
                        onDragCancel = { touch.target = NO_TARGET },
                    ) { change, _ ->
                        touch.fingerY = change.position.y
                        moveTo(indexTargetAt(change.position.y, touch.height, letters.size))
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A estrela tem toque próprio: é o único alvo do índice que se
            // alcança sem arrastar, e o caminho de volta para a tela inicial.
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = homeLabel,
                tint = if (target == STAR_TARGET) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .size(18.dp)
                    .clickable(role = Role.Button, onClick = home),
            )

            letters.forEachIndexed { index, letter ->
                val active = target == index + 1
                val jumpLabel = stringResource(R.string.jump_to_letter, letter.toString())
                Text(
                    text = letter.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    // A letra não tem toque próprio — o índice inteiro é um gesto de
                    // arraste. Para o leitor de tela ela vira um botão de verdade:
                    // a ação de acessibilidade faz o mesmo salto, sem roubar o
                    // arraste de quem enxerga.
                    modifier = Modifier
                        .semantics {
                            contentDescription = jumpLabel
                            role = Role.Button
                            onClick {
                                onLetterFocused(letter)
                                true
                            }
                        }
                        // Tudo que a onda desenha é lido aqui dentro, na camada:
                        // o dedo andando invalida o desenho, não a composição.
                        .graphicsLayer {
                            val strength = waveStrength()
                            if (strength <= 0f) return@graphicsLayer
                            val center = indexTargetCenter(index + 1, touch.height, letters.size)
                            val dx = waveOffset(center - fingerY(), amplitudePx * strength, sigmaPx)
                            translationX = dx
                            // O pico vale a amplitude inteira: a fração que falta
                            // para lá é a mesma fração de aumento da letra.
                            val grow = 1f + WAVE_SCALE * (-dx / amplitudePx)
                            scaleX = grow
                            scaleY = grow
                        },
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        if (style == IndexStyle.WAVE && target != NO_TARGET) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // `offset` com lambda acompanha o dedo na medida, sem recompor.
                    .offset {
                        IntOffset(
                            x = -bubbleShiftPx.roundToInt(),
                            y = (fingerY() - bubbleHalfPx).roundToInt(),
                        )
                    }
                    // `requiredSize`, e não `size`: a bolha é maior que a
                    // coluna de 28.dp, e as medidas de lá a achatariam.
                    .requiredSize(BUBBLE_SIZE)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                if (target == STAR_TARGET) {
                    Icon(
                        imageVector = Icons.Outlined.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                } else {
                    Text(
                        text = letters[target - 1].toString(),
                        // A bolha é opaca: a sombra que descola o texto do papel
                        // de parede aqui só sujaria a letra.
                        style = MaterialTheme.typography.headlineMedium.copy(shadow = null),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}
