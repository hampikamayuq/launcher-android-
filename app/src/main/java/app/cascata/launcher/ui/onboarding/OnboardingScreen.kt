package app.cascata.launcher.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.cascata.launcher.R
import app.cascata.launcher.data.AppRepository
import app.cascata.launcher.data.theme.Density
import app.cascata.launcher.ui.theme.iconSize
import app.cascata.launcher.ui.theme.rowPadding
import kotlinx.coroutines.launch

/** Três telas: virar padrão, escolher a densidade, entender o índice lateral. */
private const val PAGE_COUNT = 3

/** Quanto da lista de verdade cabe numa miniatura. */
private const val THUMB_SCALE = 0.45f

private val DOT_SIZE = 8.dp
private val DOT_GAP = 6.dp
private const val DOT_INACTIVE_ALPHA = 0.35f

/** As letras da ilustração do índice, e qual delas aparece em destaque. */
private val INDEX_LETTERS = listOf('J', 'K', 'L', 'M', 'N', 'O', 'P')
private const val INDEX_HIGHLIGHT = 'M'

/**
 * As boas-vindas da primeira abertura, no lugar da home. Nada de imagem: as três
 * ilustrações são desenhadas com o próprio tema, e por isso já nascem na cor e
 * na fonte que o usuário vai ver na lista.
 *
 * A densidade da página 2 é gravada no toque, sem botão de confirmar — a própria
 * miniatura muda, e sair daqui já deixa a lista do jeito escolhido.
 */
@Composable
fun OnboardingScreen(
    repository: AppRepository,
    density: Density,
    onDensityChange: (Density) -> Unit,
    /** Marca o onboarding como visto: vale tanto para "Pular" quanto para "Começar". */
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val pageLabel = stringResource(
        R.string.onboarding_page_of,
        pagerState.currentPage + 1,
        PAGE_COUNT,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            // Opaco: o onboarding não é a home, e ler texto sobre o papel de
            // parede do usuário seria pedir demais do contraste.
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .semantics { contentDescription = pageLabel },
        ) { page ->
            when (page) {
                0 -> HomePage(repository)
                1 -> DensityPage(selected = density, onSelect = onDensityChange)
                else -> IndexPage()
            }
        }

        Dots(current = pagerState.currentPage)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            // "Pular" some na última página: ali o botão de avançar já é o de sair.
            if (pagerState.currentPage < PAGE_COUNT - 1) {
                TextButton(onClick = onFinish) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }
            Spacer(Modifier.weight(1f))
            val last = pagerState.currentPage == PAGE_COUNT - 1
            Button(
                onClick = {
                    if (last) {
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
            ) {
                Text(
                    stringResource(
                        if (last) R.string.onboarding_start else R.string.onboarding_next,
                    ),
                )
            }
        }
    }
}

/** O esqueleto comum das três páginas: título, explicação e a ilustração embaixo. */
@Composable
private fun Page(title: String, body: String, content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        content()
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Página 1. O pedido para virar a home é o único do onboarding que sai do app:
 * quem já é o padrão (reinstalou, por exemplo) vê o botão apagado em vez de um
 * diálogo do sistema que não teria o que perguntar.
 */
@Composable
private fun HomePage(repository: AppRepository) {
    // Em aparelho sem RoleManager nem tela de home o intent não existe: resta o texto.
    val intent = remember { repository.requestDefaultLauncherIntent() }
    var isDefault by remember { mutableStateOf(repository.isDefaultLauncher()) }
    val chooser = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { isDefault = repository.isDefaultLauncher() }

    // O usuário pode ter trocado a home numa tela do sistema, fora do nosso diálogo.
    LifecycleResumeEffect(Unit) {
        isDefault = repository.isDefaultLauncher()
        onPauseOrDispose { }
    }

    Page(
        title = stringResource(R.string.onboarding_home_title),
        body = stringResource(R.string.onboarding_home_body),
    ) {
        ListPreview(density = Density.DEFAULT, withIndex = true)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { intent?.let(chooser::launch) },
            enabled = !isDefault && intent != null,
        ) {
            Text(
                stringResource(
                    if (isDefault) R.string.onboarding_default_done else R.string.welcome_action,
                ),
            )
        }
    }
}

/** Página 2: a densidade da lista, escolhida pela miniatura e gravada no toque. */
@Composable
private fun DensityPage(selected: Density, onSelect: (Density) -> Unit) {
    Page(
        title = stringResource(R.string.density),
        body = stringResource(R.string.onboarding_density_body),
    ) {
        val options = listOf(
            Density.COMPACT to stringResource(R.string.density_compact),
            Density.DEFAULT to stringResource(R.string.density_default),
            Density.COMFORTABLE to stringResource(R.string.density_comfortable),
        )
        val selectedLabel = stringResource(R.string.state_selected)
        val unselectedLabel = stringResource(R.string.state_not_selected)

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            options.forEach { (value, label) ->
                val chosen = value == selected
                Surface(
                    color = if (chosen) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        // O cartão inteiro é o alvo: a miniatura sozinha seria um
                        // alvo pequeno demais para o dedo e para o TalkBack.
                        .selectable(
                            selected = chosen,
                            role = Role.RadioButton,
                            onClick = { onSelect(value) },
                        )
                        .semantics {
                            stateDescription = if (chosen) selectedLabel else unselectedLabel
                        },
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                    ) {
                        ListPreview(density = value, withIndex = false)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (chosen) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** Página 3: o índice lateral, com uma letra em destaque como sob o dedo. */
@Composable
private fun IndexPage() {
    Page(
        title = stringResource(R.string.onboarding_index_title),
        body = stringResource(R.string.onboarding_index_body),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            // Ilustração: o texto acima já diz tudo o que ela mostra, e ler
            // "J K L M N O P" em voz alta não ajudaria ninguém.
            modifier = Modifier.clearAndSetSemantics { },
        ) {
            ListPreview(density = Density.DEFAULT, withIndex = false)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                INDEX_LETTERS.forEach { letter ->
                    val active = letter == INDEX_HIGHLIGHT
                    Text(
                        text = letter.toString(),
                        style = if (active) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.labelMedium
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
        }
    }
}

/**
 * Uma lista de três apps em miniatura, no espaçamento de [density]. Os "apps"
 * são blocos de cor: desenhar ícones de verdade aqui pediria o repositório
 * inteiro para ilustrar uma diferença de altura.
 */
@Composable
private fun ListPreview(density: Density, withIndex: Boolean) {
    val icon = density.iconSize * THUMB_SCALE
    val gap = density.rowPadding * THUMB_SCALE
    val ink = MaterialTheme.colorScheme.onSurfaceVariant

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(gap * 2)) {
            repeat(3) { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(icon)
                            .clip(CircleShape)
                            .background(ink.copy(alpha = 0.45f)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            // Larguras diferentes: um nome de app não é uma barra.
                            .width(if (line == 1) 56.dp else 72.dp)
                            .height(icon / 3)
                            .clip(RoundedCornerShape(50))
                            .background(ink.copy(alpha = 0.3f)),
                    )
                }
            }
        }
        if (withIndex) {
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(6) {
                    Box(
                        modifier = Modifier
                            .size(width = 6.dp, height = 3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

/** Onde estamos nas três telas. A contagem falada fica no pager. */
@Composable
private fun Dots(current: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(DOT_GAP),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        repeat(PAGE_COUNT) { index ->
            Box(
                modifier = Modifier
                    .size(DOT_SIZE)
                    .clip(CircleShape)
                    .background(
                        if (index == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = DOT_INACTIVE_ALPHA)
                        },
                    ),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}
