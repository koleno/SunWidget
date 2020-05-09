package xyz.koleno.sunwidget

import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Convenience class for handling preferences
 *
 * @author Dusan Koleno
 */
class PrefHelper(private val preferences: SharedPreferences) {

    fun saveTimes(sunrise: String, sunset: String) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH)
            val sunriseTime = dateFormat.parse(sunrise)
            val sunsetTime = dateFormat.parse(sunset)
            val outputFormat = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT)

            if (sunriseTime != null && sunsetTime != null) {
                val editor = preferences.edit()
                editor.putString(PREFS_SUNRISE, outputFormat.format(sunriseTime))
                editor.putString(PREFS_SUNSET, outputFormat.format(sunsetTime))
                editor.apply()
            } else {
                Log.d(BuildConfig.APPLICATION_ID, "Failed to save data due to format error")
            }
        } catch (e: Exception) {
            Log.d(BuildConfig.APPLICATION_ID, "Failed to save data", e)
        }
    }

    fun hasTimes(): Boolean {
        return preferences.contains(PREFS_SUNRISE) && preferences.contains(PREFS_SUNSET)
    }

    fun loadTimes(): TimesData {
        if (preferences.contains(PREFS_SUNRISE) && preferences.contains(PREFS_SUNSET)) {
            val sunrise = preferences.getString(PREFS_SUNRISE, null)
            val sunset = preferences.getString(PREFS_SUNSET, null)

            if (sunrise != null && sunset != null)
                return TimesData(sunrise, sunset)
        }

        return TimesData("", "")
    }

    fun loadLocation(): LocationData {
        return LocationData(preferences.getFloat(PREFS_LATITUDE, 0f).toDouble(), preferences.getFloat(PREFS_LONGITUDE, 0f).toDouble())
    }

    fun hasLocation(): Boolean {
        return preferences.contains(PREFS_LONGITUDE) && preferences.contains(PREFS_LATITUDE)
    }

    fun saveLocation(latitude: Float, longitude: Float) {
        val editor = preferences.edit()
        editor.putFloat(PREFS_LATITUDE, latitude)
        editor.putFloat(PREFS_LONGITUDE, longitude)
        editor.apply()
    }

    companion object {
        const val PREFS_SUNRISE = "sunrise"
        const val PREFS_SUNSET = "sunset"
        const val PREFS_LONGITUDE = "longitude"
        const val PREFS_LATITUDE = "latitude"
    }

    data class TimesData(val sunrise: String, val sunset: String)
    data class LocationData(val latitude: Double, val longitude: Double)

}