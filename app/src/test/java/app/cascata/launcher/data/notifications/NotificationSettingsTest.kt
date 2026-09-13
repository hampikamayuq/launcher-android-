package app.cascata.launcher.data.notifications

import app.cascata.launcher.data.appKeyOf
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSettingsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `comeca desligado e sem ninguem silenciado`() {
        val default = NotificationSettings.DEFAULT
        assertFalse(default.enabled)
        assertFalse(default.showMedia)
        assertTrue(default.expandInline)
        assertTrue(default.mutedPackages.isEmpty())
        assertEquals(BadgeStyle.COUNT, default.badgeStyle)
    }

    @Test
    fun `vai e volta pelo json`() {
        val settings = NotificationSettings(
            enabled = true,
            badgeStyle = BadgeStyle.DOT,
            expandInline = false,
            mutedPackages = setOf("app.um", "app.dois"),
            showMedia = true,
        )
        val text = json.encodeToString(settings)
        assertEquals(settings, json.decodeFromString<NotificationSettings>(text))
    }

    @Test
    fun `campos ausentes viram o padrao`() {
        assertEquals(NotificationSettings.DEFAULT, json.decodeFromString<NotificationSettings>("{}"))
    }

    /**
     * A lista casa app e notificação por esta chave. Não dá para montar um
     * `AppEntry` em JVM (o `ComponentName` do android.jar é um esqueleto), então
     * o que se testa é a fórmula que os dois lados chamam.
     */
    @Test
    fun `a chave de app e a mesma dos dois lados`() {
        assertEquals("app.um#0", appKeyOf("app.um", 0))
        assertEquals("app.um#10", appKeyOf("app.um", 10))
        assertFalse(appKeyOf("app.um", 0) == appKeyOf("app.um", 10))
    }
}
