package app.cascata.launcher.data.glance.weather

import app.cascata.launcher.data.glance.TemperatureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {

    @Test
    fun `cada grupo do WMO vira a condicao certa`() {
        assertEquals(WeatherCondition.CLEAR, conditionFromWmoCode(0))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, conditionFromWmoCode(1))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, conditionFromWmoCode(2))
        assertEquals(WeatherCondition.CLOUDY, conditionFromWmoCode(3))
        assertEquals(WeatherCondition.FOG, conditionFromWmoCode(45))
        assertEquals(WeatherCondition.FOG, conditionFromWmoCode(48))
        for (code in 51..57) assertEquals("$code", WeatherCondition.DRIZZLE, conditionFromWmoCode(code))
        for (code in 61..67) assertEquals("$code", WeatherCondition.RAIN, conditionFromWmoCode(code))
        for (code in 80..82) assertEquals("$code", WeatherCondition.RAIN, conditionFromWmoCode(code))
        for (code in 71..77) assertEquals("$code", WeatherCondition.SNOW, conditionFromWmoCode(code))
        assertEquals(WeatherCondition.SNOW, conditionFromWmoCode(85))
        assertEquals(WeatherCondition.SNOW, conditionFromWmoCode(86))
        for (code in 95..99) assertEquals("$code", WeatherCondition.THUNDERSTORM, conditionFromWmoCode(code))
    }

    @Test
    fun `codigo fora da tabela e desconhecido`() {
        assertEquals(WeatherCondition.UNKNOWN, conditionFromWmoCode(-1))
        assertEquals(WeatherCondition.UNKNOWN, conditionFromWmoCode(4))
        assertEquals(WeatherCondition.UNKNOWN, conditionFromWmoCode(50))
        assertEquals(WeatherCondition.UNKNOWN, conditionFromWmoCode(60))
        assertEquals(WeatherCondition.UNKNOWN, conditionFromWmoCode(79))
        assertEquals(WeatherCondition.UNKNOWN, conditionFromWmoCode(83))
        assertEquals(WeatherCondition.UNKNOWN, conditionFromWmoCode(100))
    }

    private fun snapshot(fetchedAt: Long) = WeatherSnapshot(
        temperatureC = 20.0,
        minC = 15.0,
        maxC = 25.0,
        condition = WeatherCondition.CLEAR,
        fetchedAtMillis = fetchedAt,
    )

    @Test
    fun `fresco ate trinta minutos`() {
        val now = 1_000_000_000L
        assertTrue(snapshot(now).isFresh(now))
        assertTrue(snapshot(now - 29 * 60_000L).isFresh(now))
        assertTrue(snapshot(now - WEATHER_MAX_AGE_MILLIS).isFresh(now))
        assertFalse(snapshot(now - WEATHER_MAX_AGE_MILLIS - 1).isFresh(now))
    }

    @Test
    fun `cache do futuro nao e fresco`() {
        val now = 1_000_000_000L
        assertFalse(snapshot(now + 60_000L).isFresh(now))
    }

    @Test
    fun `a idade maxima e configuravel`() {
        val now = 1_000_000_000L
        assertTrue(snapshot(now - 60_000L).isFresh(now, maxAgeMillis = 120_000L))
        assertFalse(snapshot(now - 60_000L).isFresh(now, maxAgeMillis = 30_000L))
    }

    @Test
    fun `celsius passa intacto e fahrenheit converte`() {
        assertEquals(21.5, 21.5.toUnit(TemperatureUnit.CELSIUS), 0.0001)
        assertEquals(32.0, 0.0.toUnit(TemperatureUnit.FAHRENHEIT), 0.0001)
        assertEquals(212.0, 100.0.toUnit(TemperatureUnit.FAHRENHEIT), 0.0001)
        assertEquals(-40.0, (-40.0).toUnit(TemperatureUnit.FAHRENHEIT), 0.0001)
    }
}
