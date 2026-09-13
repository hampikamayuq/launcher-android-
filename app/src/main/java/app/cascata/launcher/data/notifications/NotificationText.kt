package app.cascata.launcher.data.notifications

/**
 * Os campos crus de uma notificação, já fora do `Bundle`. Nada de Android aqui
 * de propósito: a escolha do que mostrar é regra, e regra se testa em JVM.
 */
data class RawExtras(
    val title: CharSequence?,
    val text: CharSequence?,
    val bigText: CharSequence?,
    val textLines: List<CharSequence>?,
    val lastMessage: CharSequence?,
    val lastMessageSender: CharSequence?,
    val conversationTitle: CharSequence?,
    val subText: CharSequence?,
)

/** O que a linha da lista desenha. Campo vazio é sempre null, nunca "". */
data class DisplayText(val title: String?, val text: String?, val subText: String?)

/** Quantas linhas de um InboxStyle cabem numa linha só. */
private const val MAX_INBOX_LINES = 3

/** Separador das linhas do InboxStyle — ponto médio, como o resto do sistema. */
private const val INBOX_SEPARATOR = " · "

private val WHITESPACE = Regex("\\s+")

/**
 * Escolhe título e texto entre os estilos que os apps usam de verdade, na ordem
 * em que um deles é mais informativo que o outro:
 *
 * 1. MessagingStyle — a última mensagem vale mais que o resumo "3 mensagens";
 * 2. InboxStyle — as últimas linhas, que já são o resumo;
 * 3. BigText — o texto longo cobre o curto, que costuma ser um prefixo dele.
 */
fun displayText(extras: RawExtras): DisplayText {
    val title = clean(extras.title)
    val subText = clean(extras.subText)
    val lastMessage = clean(extras.lastMessage)
    val lines = extras.textLines?.mapNotNull(::clean).orEmpty()

    return when {
        lastMessage != null -> {
            // Numa conversa o título é o nome dela; o do grupo tem prioridade
            // sobre o do app, que costuma ser só o nome do contato.
            val shown = clean(extras.conversationTitle) ?: title
            val sender = clean(extras.lastMessageSender)
            // Em conversa de duas pessoas o remetente é o próprio título:
            // repeti-lo só gasta a linha.
            val text = if (sender != null && sender != shown) "$sender: $lastMessage" else lastMessage
            DisplayText(shown, text, subText)
        }

        lines.isNotEmpty() -> DisplayText(
            title = title,
            text = lines.takeLast(MAX_INBOX_LINES).joinToString(INBOX_SEPARATOR),
            subText = subText,
        )

        else -> DisplayText(title, clean(extras.bigText) ?: clean(extras.text), subText)
    }
}

/** Quebras e espaços repetidos viram um espaço; o que sobrar vazio vira null. */
private fun clean(raw: CharSequence?): String? =
    raw?.toString()?.replace(WHITESPACE, " ")?.trim()?.takeIf { it.isNotEmpty() }
