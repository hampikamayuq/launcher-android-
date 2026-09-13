package app.cascata.launcher.data.glance

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class GlanceSettingsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `tudo comeca desligado`() {
        val default = GlanceSettings.DEFAULT
        assertFalse(default.showAlarm)
        assertFalse(default.showBattery)
        assertFalse(default.showCalendar)
        assertFalse(default.showWeather)
        assertEquals(TemperatureUnit.CELSIUS, default.temperatureUnit)
    }

    @Test
    fun `o objeto padrao e sempre o mesmo`() {
        assertSame(GlanceSettings.DEFAULT, GlanceSettings.DEFAULT)
        assertEquals(GlanceSettings(), GlanceSettings.DEFAULT)
    }

    @Test
    fun `vai e volta pelo json`() {
        val settings = GlanceSettings(
            showAlarm = true,
            showCalendar = true,
            temperatureUnit = TemperatureUnit.FAHRENHEIT,
        )
        val text = json.encodeToString(settings)
        assertEquals(settings, json.decodeFromString<GlanceSettings>(text))
    }

    @Test
    fun `campos ausentes viram o padrao`() {
        assertEquals(GlanceSettings.DEFAULT, json.decodeFromString<GlanceSettings>("{}"))
    }
}
