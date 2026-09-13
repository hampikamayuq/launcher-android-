package app.cascata.launcher.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import app.cascata.launcher.data.AppEntry
import app.cascata.launcher.data.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ícone de um app. O bitmap é rasterizado no tamanho exato de exibição, fora da
 * main thread, e o repositório mantém os últimos em cache.
 */
@Composable
fun AppIcon(
    entry: AppEntry,
    repository: AppRepository,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    var image by remember(entry.key) { mutableStateOf<ImageBitmap?>(null) }
    val px = with(LocalDensity.current) { size.roundToPx() }

    LaunchedEffect(entry.key, px) {
        image = withContext(Dispatchers.Default) {
            runCatching { repository.icon(entry)?.toBitmap(px, px)?.asImageBitmap() }.getOrNull()
        }
    }

    val current = image
    if (current != null) {
        Image(bitmap = current, contentDescription = null, modifier = modifier.size(size))
    } else {
        // Placeholder do mesmo tamanho: a lista não "pula" quando o ícone chega.
        Box(
            modifier = modifier
                .size(size)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        )
    }
}
