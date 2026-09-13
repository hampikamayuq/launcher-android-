package app.cascata.launcher.data.glance.weather

import android.content.Context

/** Edição `full`: a única que tem INTERNET no manifesto. */
object WeatherSourceFactory {
    fun create(context: Context, cache: WeatherCache): WeatherSource =
        OpenMeteoWeatherSource(context, cache)
}
