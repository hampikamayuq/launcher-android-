package app.cascata.launcher.data.glance

import kotlinx.serialization.Serializable

/** Unidade em que o clima é mostrado. O dado guardado é sempre em Celsius. */
enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

/**
 * Quais cards do "at a glance" estão ligados. Tudo começa desligado: card
 * desligado é permissão não pedida, e é essa a regra do projeto.
 */
@Serializable
data class GlanceSettings(
    val showAlarm: Boolean = false,
    val showBattery: Boolean = false,
    val showCalendar: Boolean = false,
    val showWeather: Boolean = false,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
) {
    companion object {
        val DEFAULT = GlanceSettings()
    }
}
