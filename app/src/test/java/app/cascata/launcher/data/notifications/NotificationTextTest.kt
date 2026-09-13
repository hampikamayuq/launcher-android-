package app.cascata.launcher.data.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTextTest {

    private fun extras(
        title: CharSequence? = null,
        text: CharSequence? = null,
        bigText: CharSequence? = null,
        textLines: List<CharSequence>? = null,
        lastMessage: CharSequence? = null,
        lastMessageSender: CharSequence? = null,
        conversationTitle: CharSequence? = null,
        subText: CharSequence? = null,
    ) = RawExtras(title, text, bigText, textLines, lastMessage, lastMessageSender, conversationTitle, subText)

    @Test
    fun `notificacao simples passa direto`() {
        val shown = displayText(extras(title = "Banco", text = "Pix recebido", subText = "Conta"))
        assertEquals("Banco", shown.title)
        assertEquals("Pix recebido", shown.text)
        assertEquals("Conta", shown.subText)
    }

    @Test
    fun `big text cobre o texto curto`() {
        val shown = displayText(extras(title = "Notícia", text = "Resumo", bigText = "O texto inteiro"))
        assertEquals("O texto inteiro", shown.text)
    }

    @Test
    fun `inbox mostra as tres ultimas linhas`() {
        val shown = displayText(
            extras(title = "E-mail", textLines = listOf("um", "dois", "três", "quatro"))
        )
        assertEquals("dois · três · quatro", shown.text)
    }

    @Test
    fun `inbox com linhas em branco cai no texto`() {
        val shown = displayText(extras(title = "E-mail", text = "2 novas", textLines = listOf("  ", "")))
        assertEquals("2 novas", shown.text)
    }

    @Test
    fun `conversa de grupo mostra remetente e titulo do grupo`() {
        val shown = displayText(
            extras(
                title = "Ana",
                text = "3 mensagens",
                lastMessage = "chegando",
                lastMessageSender = "Ana",
                conversationTitle = "Time",
            )
        )
        assertEquals("Time", shown.title)
        assertEquals("Ana: chegando", shown.text)
    }

    @Test
    fun `conversa de duas pessoas nao repete o remetente`() {
        val shown = displayText(
            extras(title = "Ana", lastMessage = "chegando", lastMessageSender = "Ana")
        )
        assertEquals("Ana", shown.title)
        assertEquals("chegando", shown.text)
    }

    @Test
    fun `mensagem sem remetente fica so com o texto`() {
        val shown = displayText(extras(title = "Ana", lastMessage = "oi"))
        assertEquals("oi", shown.text)
    }

    @Test
    fun `mensagem tem prioridade sobre big text e linhas`() {
        val shown = displayText(
            extras(
                title = "Chat",
                text = "curto",
                bigText = "longo",
                textLines = listOf("a", "b"),
                lastMessage = "última",
                lastMessageSender = "Beto",
            )
        )
        assertEquals("Beto: última", shown.text)
    }

    @Test
    fun `espacos e quebras colapsam`() {
        val shown = displayText(extras(title = "  Banco \n Central ", text = "linha um\n\nlinha dois"))
        assertEquals("Banco Central", shown.title)
        assertEquals("linha um linha dois", shown.text)
    }

    @Test
    fun `texto vazio vira nulo`() {
        val shown = displayText(extras(title = "   ", text = "", subText = "\n"))
        assertNull(shown.title)
        assertNull(shown.text)
        assertNull(shown.subText)
    }
}
