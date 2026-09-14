package app.cascata.launcher.ui

import android.content.pm.ShortcutInfo
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import app.cascata.launcher.data.AppEntry
import app.cascata.launcher.data.IconSource
import app.cascata.launcher.data.LoadedIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Quanto do círculo um ícone legado ocupa. Sobra de margem é o que o deixa parecido com um adaptativo. */
private const val LEGACY_SCALE = 0.78f

/** O bitmap pronto mais o que decide como desenhá-lo — os dois vêm da mesma volta fora da main thread. */
private data class RasterIcon(val image: ImageBitmap, val adaptive: Boolean)

/**
 * Ícone de um app. O bitmap é rasterizado no tamanho exato de exibição, fora da
 * main thread, e o repositório mantém os últimos em cache.
 *
 * Ícone legado (não adaptativo) não tem máscara nenhuma: desenhamos encolhido
 * dentro de um círculo, para não destoar dos adaptativos na mesma lista.
 */
@Composable
fun AppIcon(
    entry: AppEntry,
    repository: IconSource,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val inspecting = LocalInspectionMode.current
    var raster by remember(entry.key, px, inspecting) {
        mutableStateOf(if (inspecting) inspectionRaster(repository, entry, px) else null)
    }

    LaunchedEffect(entry.key, px) {
        if (inspecting) return@LaunchedEffect
        raster = withContext(Dispatchers.Default) {
            runCatching { rasterize(repository.icon(entry), px) }.getOrNull()
        }
    }

    IconSurface(raster = raster, size = size, modifier = modifier)
}

/**
 * Ícone de uma prévia (layoutlib), onde não há quadro seguinte: a imagem é
 * capturada logo depois da primeira composição, e o efeito acima não chegaria a
 * devolver nada — o ícone sairia vazio. Só aqui a rasterização é síncrona, e a
 * fonte é sempre a fictícia do source set `screenshotTest`; no aparelho este
 * caminho nunca roda.
 */
private fun inspectionRaster(repository: IconSource, entry: AppEntry, px: Int): RasterIcon? =
    runCatching { runBlocking { rasterize(repository.icon(entry), px) } }.getOrNull()

/** Mesma rasterização do [AppIcon], para os atalhos do menu de contexto. */
@Composable
fun ShortcutIcon(
    shortcut: ShortcutInfo,
    repository: IconSource,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val key = "${shortcut.`package`}#${shortcut.id}"
    var raster by remember(key) { mutableStateOf<RasterIcon?>(null) }
    val px = with(LocalDensity.current) { size.roundToPx() }

    LaunchedEffect(key, px) {
        raster = withContext(Dispatchers.Default) {
            runCatching { rasterize(repository.shortcutIcon(shortcut), px) }.getOrNull()
        }
    }

    IconSurface(raster = raster, size = size, modifier = modifier)
}

@Composable
private fun IconSurface(raster: RasterIcon?, size: Dp, modifier: Modifier) {
    // Caixa sempre do mesmo tamanho: a lista não "pula" quando o ícone chega.
    Box(
        modifier = modifier.size(size).then(
            if (raster?.adaptive == true) {
                Modifier
            } else {
                // Fundo e recorte só para o legado (e para o vazio, que vira placeholder).
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            }
        ),
        contentAlignment = Alignment.Center,
    ) {
        val current = raster ?: return@Box
        Image(
            bitmap = current.image,
            contentDescription = null,
            modifier = Modifier.size(if (current.adaptive) size else size * LEGACY_SCALE),
        )
    }
}

/**
 * Ícone vindo de um pacote de ícones já chega no formato final do pacote:
 * mascarar de novo cortaria o desenho de quem escolheu aquele visual. Por isso
 * ele segue o mesmo caminho do adaptativo — sem máscara.
 */
private fun rasterize(icon: LoadedIcon?, px: Int): RasterIcon? =
    icon?.let { rasterize(it.drawable, px, unmasked = it.fromPack) }

/**
 * Descobre o tipo do drawable e rasteriza de uma vez só, fora da main thread:
 * `is AdaptiveIconDrawable` é barato, mas quem carrega o drawable não é.
 */
private fun rasterize(drawable: Drawable?, px: Int, unmasked: Boolean = false): RasterIcon? {
    if (drawable == null) return null
    val full = unmasked || drawable is AdaptiveIconDrawable
    val target = if (full) px else (px * LEGACY_SCALE).roundToInt().coerceAtLeast(1)
    return RasterIcon(drawable.toBitmap(target, target).asImageBitmap(), full)
}
