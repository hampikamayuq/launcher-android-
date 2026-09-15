package app.cascata.launcher.data.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Um contato como a busca precisa dele: nome, um telefone (se houver) e a foto. */
data class Contact(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val phone: String?,
    val photoUri: String?,
)

private val PROJECTION = arrayOf(
    ContactsContract.Contacts._ID,
    ContactsContract.Contacts.LOOKUP_KEY,
    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
    ContactsContract.Contacts.HAS_PHONE_NUMBER,
)

/**
 * Contatos na busca. Como os cards da Fase 3: a permissão é declarada mas só é
 * pedida quando o usuário liga a opção, e sem ela nada é consultado — nem o
 * provedor é tocado.
 */
class ContactsSource(private val context: Context) {

    fun hasPermission(): Boolean = runCatching {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /**
     * Casamento é o do próprio provedor (`CONTENT_FILTER_URI`): ele já lida com
     * acento, sobrenome e apelido melhor do que qualquer filtro nosso.
     */
    suspend fun search(query: String, limit: Int = 5): List<Contact> = withContext(Dispatchers.IO) {
        val needle = query.trim()
        if (needle.isEmpty() || !hasPermission()) return@withContext emptyList()
        val found = runCatching {
            val uri = ContactsContract.Contacts.CONTENT_FILTER_URI.buildUpon()
                .appendPath(needle)
                .build()
            context.contentResolver.query(
                uri,
                PROJECTION,
                null,
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
            )?.use { cursor -> cursor.readContacts(limit) }.orEmpty()
        }.getOrElse {
            // Permissão revogada com o app aberto, provedor de contatos de
            // fabricante lançando o que quiser: a busca só fica sem esta seção.
            emptyList()
        }
        // Uma segunda consulta por contato, e só para quem tem telefone: é o
        // preço de trazer o número primário sem varrer a tabela de dados inteira.
        found.map { row ->
            if (row.hasPhone) row.contact.copy(phone = primaryPhone(row.contact.id)) else row.contact
        }
    }

    private fun primaryPhone(contactId: Long): String? = runCatching {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
        }
    }.getOrNull()

    /** Disca sem ligar: quem confirma a chamada é o discador. Sem permissão nova. */
    fun dialIntent(phone: String): Intent =
        Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Abre o app de mensagens com o destinatário preenchido. Sem permissão nova. */
    fun smsIntent(phone: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** A ficha do contato na agenda do aparelho. */
    fun viewIntent(contact: Contact): Intent =
        Intent(
            Intent.ACTION_VIEW,
            ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

private class ContactRow(val contact: Contact, val hasPhone: Boolean)

private fun Cursor.readContacts(limit: Int): List<ContactRow> {
    val rows = ArrayList<ContactRow>(limit)
    while (rows.size < limit && moveToNext()) {
        val name = if (isNull(2)) "" else getString(2)
        val lookup = if (isNull(1)) "" else getString(1)
        if (name.isBlank() || lookup.isBlank()) continue
        rows += ContactRow(
            contact = Contact(
                id = getLong(0),
                lookupKey = lookup,
                name = name,
                phone = null,
                photoUri = if (isNull(3)) null else getString(3),
            ),
            hasPhone = !isNull(4) && getInt(4) != 0,
        )
    }
    return rows
}
