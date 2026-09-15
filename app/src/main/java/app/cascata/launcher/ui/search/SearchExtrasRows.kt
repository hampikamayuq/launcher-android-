package app.cascata.launcher.ui.search

import android.content.ClipData
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.cascata.launcher.R
import app.cascata.launcher.SearchExtras
import app.cascata.launcher.data.IconSource
import app.cascata.launcher.data.ShortcutMatch
import app.cascata.launcher.data.search.CalculationResult
import app.cascata.launcher.data.search.Contact
import app.cascata.launcher.data.search.SettingEntry
import app.cascata.launcher.ui.ShortcutIcon
import app.cascata.launcher.ui.theme.SurfaceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Foto do contato — e o teto do bitmap que carregamos para ela. */
private val PHOTO_SIZE = 40.dp

/** Menor que o ícone de app: o atalho é uma parte do app, não um irmão dele. */
private val SHORTCUT_ICON = 32.dp

/** Quanto tempo o aviso de "Copiado" fica no card da conta. */
private const val COPIED_MS = 1_500L

/**
 * A conta é o primeiro item da busca, antes até dos apps: quem digitou "12*7"
 * quer o número, não a gaveta.
 */
fun LazyListScope.calculationItem(result: CalculationResult) {
    item(key = "calculation", contentType = "calculation") {
        CalculationCard(result)
    }
}

/**
 * O que vem depois das linhas de app, nesta ordem: atalhos, contatos,
 * configurações do sistema e, por último, a web — do mais específico (um atalho
 * daquele app) ao mais genérico (jogar o termo no navegador).
 *
 * Cada seção some sozinha quando está vazia; a lista chega aqui já filtrada
 * pelas opções desligadas.
 */
fun LazyListScope.searchExtraItems(
    extras: SearchExtras,
    repository: IconSource,
    onOpenShortcut: (ShortcutMatch) -> Unit,
    onOpenContact: (Contact) -> Unit,
    onCallContact: (Contact) -> Unit,
    onMessageContact: (Contact) -> Unit,
    onOpenSetting: (SettingEntry) -> Unit,
    onWebSearch: () -> Unit,
) {
    if (extras.shortcuts.isNotEmpty()) {
        item(key = "shortcuts-header", contentType = "search-header") {
            SearchHeader(stringResource(R.string.shortcuts))
        }
        items(
            items = extras.shortcuts,
            key = ::shortcutKey,
            contentType = { "shortcut" },
        ) { match ->
            ShortcutRow(
                match = match,
                repository = repository,
                onClick = { onOpenShortcut(match) },
            )
        }
    }

    if (extras.contacts.isNotEmpty()) {
        item(key = "contacts-header", contentType = "search-header") {
            SearchHeader(stringResource(R.string.search_section_contacts))
        }
        items(
            items = extras.contacts,
            key = { "contact-${it.lookupKey}" },
            contentType = { "contact" },
        ) { contact ->
            ContactRow(
                contact = contact,
                onClick = { onOpenContact(contact) },
                onCall = { onCallContact(contact) },
                onMessage = { onMessageContact(contact) },
            )
        }
    }

    if (extras.settings.isNotEmpty()) {
        item(key = "settings-header", contentType = "search-header") {
            SearchHeader(stringResource(R.string.search_section_settings))
        }
        items(
            items = extras.settings,
            key = { "setting-${it.id}" },
            contentType = { "setting" },
        ) { entry ->
            SettingRow(entry = entry, onClick = { onOpenSetting(entry) })
        }
    }

    extras.web?.let { (engine, term) ->
        item(key = "web", contentType = "web") {
            WebRow(engine = engine.label, term = term, onClick = onWebSearch)
        }
    }
}

/**
 * O perfil entra na chave: o mesmo app, instalado no pessoal e no de trabalho,
 * tem atalhos com o mesmo id.
 */
private fun shortcutKey(match: ShortcutMatch): String =
    "shortcut-${match.shortcut.`package`}#${match.shortcut.userHandle.hashCode()}#${match.shortcut.id}"

/** Mesmo estilo do cabeçalho de letra da lista: a busca não inventa outra hierarquia. */
@Composable
private fun SearchHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        // Cabeçalho de seção da busca, como as letras da lista.
        modifier = Modifier
            .semantics { heading() }
            .padding(top = 12.dp, bottom = 4.dp),
    )
}

/**
 * A conta, com a expressão em cima e o resultado grande embaixo, à direita —
 * como numa calculadora. O toque copia o resultado; o aviso vive no próprio
 * card e se apaga sozinho, porque a home não tem Scaffold e um Snackbar exigiria
 * um só para esta linha.
 */
@Composable
private fun CalculationCard(result: CalculationResult) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val description = stringResource(R.string.search_calculation_description, result.formatted)
    var copied by remember(result.formatted) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (!copied) return@LaunchedEffect
        delay(COPIED_MS)
        copied = false
    }

    // O cartão tem superfície própria e opaca: sem isto, sobre o papel de
    // parede, o resultado sairia em tinta clara por cima dela.
    SurfaceTheme {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable(role = Role.Button) {
                    // A API nova é suspensa: copiar sai do caminho do toque.
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("cascata", result.formatted)))
                    }
                    copied = true
                }
                .semantics(mergeDescendants = true) { contentDescription = description },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = result.expression,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.End),
                )
                Text(
                    text = result.formatted,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.End),
                )
                if (copied) {
                    Text(
                        text = stringResource(R.string.search_copied),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        // Aparece sozinho depois do toque e some sozinho: sem região
                        // viva o leitor de tela nunca saberia que a cópia aconteceu.
                        modifier = Modifier
                            .align(Alignment.End)
                            // Nó próprio: dentro do nó do card ele seria absorvido
                            // pela descrição do resultado e nunca seria anunciado.
                            .semantics(mergeDescendants = true) {
                                liveRegion = LiveRegionMode.Polite
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortcutRow(match: ShortcutMatch, repository: IconSource, onClick: () -> Unit) {
    val label = (match.shortcut.shortLabel ?: match.shortcut.longLabel)?.toString().orEmpty()
    val appLabel = match.app?.label
    val description = if (appLabel == null) {
        label
    } else {
        stringResource(R.string.search_shortcut_description, label, appLabel)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        ShortcutIcon(shortcut = match.shortcut, repository = repository, size = SHORTCUT_ICON)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // De quem é o atalho: dois apps podem ter um "Nova mensagem".
            if (appLabel != null) {
                Text(
                    text = appLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Ligar e mandar mensagem só aparecem para quem tem telefone — botão que não
 * faz nada é pior que botão ausente. A linha inteira abre a ficha na agenda.
 */
@Composable
private fun ContactRow(
    contact: Contact,
    onClick: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
) {
    // Os botões de ligar e mensagem são nós próprios: a descrição da linha fala
    // só de abrir o contato, e não engole a deles.
    val description = stringResource(R.string.search_contact_open, contact.name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp)
            .semantics { contentDescription = description },
    ) {
        ContactPhoto(contact)
        Spacer(Modifier.width(14.dp))
        Text(
            text = contact.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (contact.phone != null) {
            IconButton(onClick = onCall) {
                Icon(
                    imageVector = Icons.Outlined.Call,
                    contentDescription = stringResource(R.string.search_contact_call, contact.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onMessage) {
                Icon(
                    // O conjunto de ícones do projeto é o `core`: o envelope é o
                    // que há de mais próximo de "mandar uma mensagem".
                    imageVector = Icons.Outlined.Email,
                    contentDescription = stringResource(R.string.search_contact_message, contact.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Foto do contato, ou a inicial dele num círculo quando não há foto (nem como lê-la). */
@Composable
private fun ContactPhoto(contact: Contact) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { PHOTO_SIZE.roundToPx() }
    var photo by remember(contact.lookupKey) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(contact.photoUri, px) {
        photo = contact.photoUri?.let { uri ->
            withContext(Dispatchers.IO) { decodePhoto(context, uri, px) }
        }
    }

    Box(
        modifier = Modifier
            .size(PHOTO_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val image = photo
        if (image == null) {
            Text(
                text = contact.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(PHOTO_SIZE),
            )
        }
    }
}

@Composable
private fun SettingRow(entry: SettingEntry, onClick: () -> Unit) {
    val label = stringResource(entry.labelRes)
    val description = stringResource(R.string.search_setting_description, label)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        SearchRowIcon(Icons.Outlined.Settings)
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Última linha da busca. O app não vai à rede: quem abre o endereço é o navegador. */
@Composable
private fun WebRow(engine: String, term: String, onClick: () -> Unit) {
    val label = stringResource(R.string.search_web, term, engine)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        SearchRowIcon(Icons.Outlined.Search)
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Ícone no lugar (e no tamanho) de onde estaria o ícone do app, para as linhas alinharem. */
@Composable
private fun SearchRowIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(SHORTCUT_ICON)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Sem Coil no projeto: duas passadas no BitmapFactory — a primeira só mede, a
 * segunda decodifica já reduzida ao tamanho da linha. Qualquer falha (foto
 * apagada entre a consulta e o desenho, permissão revogada com o app aberto)
 * devolve null e a linha fica com a inicial.
 */
private fun decodePhoto(context: Context, uri: String, px: Int): ImageBitmap? = runCatching {
    val parsed = uri.toUri()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(parsed)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, px)
    }
    context.contentResolver.openInputStream(parsed)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }?.asImageBitmap()
}.getOrNull()

/** Maior potência de dois que ainda deixa a menor dimensão acima do tamanho de exibição. */
private fun sampleSize(width: Int, height: Int, target: Int): Int {
    var smallest = minOf(width, height)
    if (smallest <= 0 || target <= 0) return 1
    var sample = 1
    while (smallest / 2 >= target) {
        smallest /= 2
        sample *= 2
    }
    return sample
}
