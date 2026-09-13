package app.cascata.launcher.ui.glance

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cascata.launcher.R
import app.cascata.launcher.data.glance.MediaSource
import app.cascata.launcher.data.glance.MediaState

/** O chip de mídia é o mais largo da linha: três botões e duas linhas de texto. */
private val MEDIA_TEXT_MAX = 180.dp

/** Botões de transporte compactos — três deles num chip da altura dos outros. */
private val TRANSPORT_BUTTON = 32.dp

/**
 * A faixa que está tocando, com os controles. Some sozinho quando não há sessão
 * de mídia: o `MediaSessionManager` só entrega sessões a quem tem acesso a
 * notificações, e sem ele o fluxo emite null.
 */
@Composable
internal fun MediaChip(source: MediaSource) {
    val context = LocalContext.current
    val state by remember(source) { source.current() }.collectAsStateWithLifecycle(null)
    val media = state ?: return

    val title = media.title?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.media_untitled)
    val artist = media.artist?.takeIf { it.isNotBlank() }
    val description = if (artist != null) {
        stringResource(R.string.media_description, title, artist)
    } else {
        stringResource(R.string.media_description_title, title)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CHIP_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(percent = 50),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            TransportButton(
                onClick = { source.previous(media) },
                description = stringResource(R.string.media_previous),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = null,
                    modifier = Modifier.size(CHIP_ICON),
                )
            }

            Column(
                modifier = Modifier
                    .widthIn(max = MEDIA_TEXT_MAX)
                    .clickable(role = Role.Button) { openMediaApp(context, media) }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    // A descrição completa fica no texto, que é o que se toca
                    // para abrir o app; cada botão tem a sua.
                    .semantics(mergeDescendants = true) { contentDescription = description },
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (artist != null) {
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            TransportButton(
                onClick = { source.playPause(media) },
                description = stringResource(
                    if (media.playing) R.string.media_pause else R.string.media_play,
                ),
            ) {
                if (media.playing) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pause),
                        contentDescription = null,
                        modifier = Modifier.size(CHIP_ICON),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(CHIP_ICON),
                    )
                }
            }

            TransportButton(
                onClick = { source.next(media) },
                description = stringResource(R.string.media_next),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(CHIP_ICON),
                )
            }
        }
    }
}

/** Botão de transporte: a descrição vai no nó, o ícone dentro dele fica mudo. */
@Composable
private fun TransportButton(
    onClick: () -> Unit,
    description: String,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(TRANSPORT_BUTTON)
            .semantics { contentDescription = description },
        content = content,
    )
}

/**
 * Abre o app que está tocando. O `LauncherApps` não chega até aqui e a sessão de
 * mídia só dá o nome do pacote — o intent de lançamento resolve, e `runCatching`
 * cobre o app que não tem tela nenhuma para abrir.
 */
private fun openMediaApp(context: Context, media: MediaState) {
    runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(media.packageName)
            ?: return
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
