package app.cascata.launcher.data.glance.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoCodecTest {

    /** Resposta real do endpoint, com os campos extras que a API manda junto. */
    private val sample = """
        {
          "latitude": -23.5,
          "longitude": -46.625,
          "generationtime_ms": 0.0389,
          "utc_offset_seconds": -10800,
          "timezone": "America/Sao_Paulo",
          "timezone_abbreviation": "GMT-3",
          "elevation": 760.0,
          "current_units": {"time": "iso8601", "interval": "seconds", "temperature_2m": "°C", "weather_code": "wmo code"},
          "current": {"time": "2026-09-13T15:00", "interval": 900, "temperature_2m": 21.4, "weather_code": 61},
          "daily_units": {"time": "iso8601", "temperature_2m_max": "°C", "temperature_2m_min": "°C"},
          "daily": {"time": ["2026-09-13"], "temperature_2m_max": [24.8], "temperature_2m_min": [13.2]}
        }
    """.trimIndent()

    @Test
    fun `a url leva so as duas coordenadas`() {
        val url = buildForecastUrl(-23.5489, -46.6388)
        assertEquals(
            "https://api.open-meteo.com/v1/forecast?latitude=-23.55&longitude=-46.64" +
                "&current=temperature_2m,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=1",
            url,
        )
        // Nada de chave nem de identificador do aparelho na query.
        assertFalse(url, url.contains("key", ignoreCase = true))
        assertFalse(url, url.contains("id=", ignoreCase = true))
    }

    @Test
    fun `a url usa ponto decimal em qualquer idioma`() {
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("pt-BR"))
            assertTrue(buildForecastUrl(10.5, 20.25).contains("latitude=10.50"))
        } finally {
            java.util.Locale.setDefault(original)
        }
    }

    @Test
    fun `a resposta real vira um snapshot`() {
        val snapshot = parseForecast(sample, nowMillis = 1_700_000_000_000L).getOrThrow()
        assertEquals(21.4, snapshot.temperatureC, 0.0001)
        assertEquals(13.2, snapshot.minC!!, 0.0001)
        assertEquals(24.8, snapshot.maxC!!, 0.0001)
        assertEquals(WeatherCondition.RAIN, snapshot.condition)
        assertEquals(1_700_000_000_000L, snapshot.fetchedAtMillis)
        assertEquals("Sao Paulo", snapshot.locationLabel)
    }

    @Test
    fun `json invalido vira falha e nao excecao`() {
        assertTrue(parseForecast("não é json", 0L).isFailure)
        assertTrue(parseForecast("", 0L).isFailure)
        assertTrue(parseForecast("{", 0L).isFailure)
    }

    @Test
    fun `sem temperatura atual e falha`() {
        assertTrue(parseForecast("""{"timezone":"America/Sao_Paulo"}""", 0L).isFailure)
        assertTrue(parseForecast("""{"current":{"weather_code":0}}""", 0L).isFailure)
    }

    @Test
    fun `sem o bloco diario ainda da um snapshot`() {
        val snapshot = parseForecast(
            """{"current":{"temperature_2m":8.0,"weather_code":3}}""",
            nowMillis = 5L,
        ).getOrThrow()
        assertEquals(8.0, snapshot.temperatureC, 0.0001)
        assertNull(snapshot.minC)
        assertNull(snapshot.maxC)
        assertNull(snapshot.locationLabel)
        assertEquals(WeatherCondition.CLOUDY, snapshot.condition)
    }

    @Test
    fun `sem weather code a condicao e desconhecida`() {
        val snapshot = parseForecast("""{"current":{"temperature_2m":8.0}}""", 0L).getOrThrow()
        assertEquals(WeatherCondition.UNKNOWN, snapshot.condition)
    }

    @Test
    fun `fuso sem cidade nao vira rotulo`() {
        val snapshot = parseForecast(
            """{"timezone":"GMT","current":{"temperature_2m":8.0}}""",
            0L,
        ).getOrThrow()
        assertNull(snapshot.locationLabel)
    }
}
