package app.cascata.launcher.data.usage

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageSettingsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `comeca desligado e sem limites`() {
        val default = UsageSettings.DEFAULT
        assertFalse(default.enabled)
        assertTrue(default.showCard)
        assertEquals(5, default.pauseSeconds)
        assertEquals(emptyMap<String, Int>(), default.limitsMinutes)
    }

    @Test
    fun `o objeto padrao e sempre o mesmo`() {
        assertSame(UsageSettings.DEFAULT, UsageSettings.DEFAULT)
        assertEquals(UsageSettings(), UsageSettings.DEFAULT)
    }

    @Test
    fun `a pausa fica entre tres e trinta segundos`() {
        assertEquals(3, UsageSettings(pauseSeconds = 0).coerced().pauseSeconds)
        assertEquals(3, UsageSettings(pauseSeconds = -4).coerced().pauseSeconds)
        assertEquals(30, UsageSettings(pauseSeconds = 120).coerced().pauseSeconds)
    }

    @Test
    fun `pausa dentro da faixa devolve o mesmo objeto`() {
        val settings = UsageSettings(pauseSeconds = 8)
        assertSame(settings, settings.coerced())
    }

    @Test
    fun `coercao nao mexe no resto`() {
        val settings = UsageSettings(
            enabled = true,
            showCard = false,
            pauseSeconds = 99,
            limitsMinutes = mapOf("app.exemplo" to 30),
        )
        assertEquals(settings.copy(pauseSeconds = 30), settings.coerced())
    }

    @Test
    fun `vai e volta pelo json`() {
        val settings = UsageSettings(
            enabled = true,
            showCard = false,
            pauseSeconds = 10,
            limitsMinutes = mapOf("app.um" to 15, "app.dois" to 60),
        )
        val text = json.encodeToString(settings)
        assertEquals(settings, json.decodeFromString<UsageSettings>(text))
    }

    @Test
    fun `campos ausentes viram o padrao`() {
        assertEquals(UsageSettings.DEFAULT, json.decodeFromString<UsageSettings>("{}"))
    }
}
