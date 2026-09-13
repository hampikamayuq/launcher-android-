package app.cascata.launcher.data.notifications

import app.cascata.launcher.data.appKeyOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Um mínimo de notificação: `AppNotification` carrega `UserHandle` e
 * `PendingIntent`, que não existem fora do aparelho. A regra só olha para isto.
 */
private data class Fact(
    val id: String,
    override val packageName: String = "app.um",
    override val groupKey: String? = null,
    override val isGroupSummary: Boolean = false,
    override val postTimeMillis: Long = 0,
    override val isMedia: Boolean = false,
    val userHash: Int = 0,
) : NotificationFacts {
    override val appKey: String get() = appKeyOf(packageName, userHash)
}

class NotificationGroupingTest {

    @Test
    fun `resumo some quando os filhos estao a vista`() {
        val items = listOf(
            Fact("resumo", groupKey = "g", isGroupSummary = true, postTimeMillis = 30),
            Fact("filho1", groupKey = "g", postTimeMillis = 20),
            Fact("filho2", groupKey = "g", postTimeMillis = 10),
        )
        assertEquals(listOf("filho1", "filho2"), groupForDisplay(items).map { it.id })
    }

    @Test
    fun `resumo sozinho fica`() {
        val items = listOf(Fact("resumo", groupKey = "g", isGroupSummary = true))
        assertEquals(listOf("resumo"), groupForDisplay(items).map { it.id })
    }

    @Test
    fun `resumo de um grupo nao derruba o de outro`() {
        val items = listOf(
            Fact("resumo-a", groupKey = "a", isGroupSummary = true, postTimeMillis = 40),
            Fact("filho-a", groupKey = "a", postTimeMillis = 30),
            Fact("resumo-b", groupKey = "b", isGroupSummary = true, postTimeMillis = 20),
        )
        assertEquals(listOf("filho-a", "resumo-b"), groupForDisplay(items).map { it.id })
    }

    @Test
    fun `resumo sem grupo fica`() {
        val items = listOf(Fact("solto", isGroupSummary = true), Fact("filho"))
        assertEquals(setOf("solto", "filho"), groupForDisplay(items).map { it.id }.toSet())
    }

    @Test
    fun `a mais nova vem primeiro`() {
        val items = listOf(
            Fact("velha", postTimeMillis = 1),
            Fact("nova", postTimeMillis = 3),
            Fact("meio", postTimeMillis = 2),
        )
        assertEquals(listOf("nova", "meio", "velha"), groupForDisplay(items).map { it.id })
    }

    @Test
    fun `continua mostra notificacao em andamento`() {
        val items = listOf(Fact("download"))
        assertEquals(listOf("download"), groupForDisplay(items).map { it.id })
    }

    @Test
    fun `limita a dez por app`() {
        val items = (1..15).map { Fact("n$it", postTimeMillis = it.toLong()) }
        val shown = groupForDisplay(items)
        assertEquals(10, shown.size)
        assertEquals("n15", shown.first().id)
        assertEquals("n6", shown.last().id)
    }

    @Test
    fun `o teto vale por app, nao para a lista toda`() {
        val items = (1..12).map { Fact("a$it", packageName = "app.um", postTimeMillis = it.toLong()) } +
            (1..12).map { Fact("b$it", packageName = "app.dois", postTimeMillis = it.toLong()) }
        val shown = groupForDisplay(items).groupBy { it.packageName }
        assertEquals(10, shown.getValue("app.um").size)
        assertEquals(10, shown.getValue("app.dois").size)
    }

    @Test
    fun `mesmo pacote em perfis diferentes nao divide o teto`() {
        val items = (1..12).map { Fact("p$it", userHash = 0, postTimeMillis = it.toLong()) } +
            (1..12).map { Fact("t$it", userHash = 10, postTimeMillis = it.toLong()) }
        assertEquals(20, groupForDisplay(items).size)
    }

    @Test
    fun `desligado nao mostra nada`() {
        val byApp = mapOf("app.um#0" to listOf(Fact("n")))
        assertTrue(visibleNotifications(byApp, NotificationSettings.DEFAULT).isEmpty())
    }

    @Test
    fun `pacote silenciado some do mapa`() {
        val byApp = mapOf(
            "app.um#0" to listOf(Fact("n", packageName = "app.um")),
            "app.dois#0" to listOf(Fact("m", packageName = "app.dois")),
        )
        val settings = NotificationSettings(enabled = true, mutedPackages = setOf("app.um"))
        assertEquals(setOf("app.dois#0"), visibleNotifications(byApp, settings).keys)
    }

    @Test
    fun `midia so aparece quando o usuario pede`() {
        val byApp = mapOf(
            "app.um#0" to listOf(Fact("faixa", isMedia = true), Fact("aviso")),
        )
        val escondida = visibleNotifications(byApp, NotificationSettings(enabled = true))
        assertEquals(listOf("aviso"), escondida.getValue("app.um#0").map { it.id })

        val mostrada = visibleNotifications(byApp, NotificationSettings(enabled = true, showMedia = true))
        assertEquals(2, mostrada.getValue("app.um#0").size)
    }
}
