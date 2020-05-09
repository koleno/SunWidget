package xyz.koleno.sunwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.core.app.JobIntentService
import android.util.Log
import android.widget.RemoteViews
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import xyz.koleno.sunwidget.MainActivityViewModel.Companion.PREFS_LATITUDE
import xyz.koleno.sunwidget.MainActivityViewModel.Companion.PREFS_LATITUDE_DEFAULT
import xyz.koleno.sunwidget.MainActivityViewModel.Companion.PREFS_LONGITUDE
import xyz.koleno.sunwidget.MainActivityViewModel.Companion.PREFS_LONGITUDE_DEFAULT
import xyz.koleno.sunwidget.api.DataService
import xyz.koleno.sunwidget.api.json.ApiResponse
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service that updates the widgets
 *
 * @author Dusan Koleno
 */
class UpdateService : JobIntentService() {

    private val tag = BuildConfig.APPLICATION_ID
    private val baseUrl = "https://api.sunrise-sunset.org/"

    private lateinit var manager: AppWidgetManager
    private lateinit var prefs: SharedPreferences
    private var widgetIds: IntArray? = null
    private var longitude: Float = 0.0f
    private var latitude: Float = 0.0f

    companion object {
        /**
         * Convenience method for enqueuing work in to this service.
         */
        fun enqueueWork(context: Context, work: Intent) {
            enqueueWork(context, UpdateService::class.java, 1000, work)
        }

        const val PREFS_SUNRISE = "sunrise"
        const val PREFS_SUNSET = "sunset"

        const val PREFS_SUNRISE_DEFAULT = "2015-05-21T05:05:35+00:00"
        const val PREFS_SUNSET_DEFAULT = "2015-05-21T19:22:59+00:00"
    }

    override fun onHandleWork(intent: Intent) {
        if (intent.extras != null) {
            // get everything necessary from the bundle
            manager = AppWidgetManager.getInstance(this.applicationContext)
            prefs = PreferenceManager.getDefaultSharedPreferences(this.applicationContext)
            widgetIds = intent.getIntArrayExtra("widgetIds")
            longitude = prefs.getFloat(PREFS_LONGITUDE, PREFS_LONGITUDE_DEFAULT.toFloat())
            latitude = prefs.getFloat(PREFS_LATITUDE, PREFS_LATITUDE_DEFAULT.toFloat())

            val retrofit = Retrofit.Builder().baseUrl(baseUrl).addConverterFactory(GsonConverterFactory.create()).build()
            val call = retrofit.create(DataService::class.java).getTimes(latitude, longitude)

            call.enqueue(object : Callback<ApiResponse> {
                override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                    loadCache()
                    Log.d(tag, "Failed to retrieve data from API", t)
                }

                override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                    if (response.code() == 200 && response.body()?.status.equals("OK")) {
                        response.body()?.results?.let {
                            if (it.sunrise == null || it.sunset == null) {
                                return
                            }

                            cacheData(it.sunrise, it.sunset)
                            updateWidgets(it.sunrise, it.sunset)
                            Log.i(tag, "API data retrieved, updating widgets")
                        }
                    } else {
                        loadCache()
                        Log.d(tag, "Returned response code " + response.code() + " and status " + response.body()?.status)
                    }
                }

            })
        }
    }

    /**
     * Updates widgets - called when data are available
     *
     * @param sunrise sunrise time
     * @param sunset sunset time
     */
    private fun updateWidgets(sunrise: String, sunset: String) {
        widgetIds?.let {
            for (widgetId in it) {
                val rv = RemoteViews(this.applicationContext.packageName, R.layout.sunwidget)

                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH)
                val sunriseTime = dateFormat.parse(sunrise)
                val sunsetTime = dateFormat.parse(sunset)

                if (sunriseTime != null && sunsetTime != null) {
                    // format for the widget display
                    val outputFormat = SimpleDateFormat.getTimeInstance(DateFormat.SHORT)

                    rv.setTextViewText(R.id.text_sunrise_value, outputFormat.format(sunriseTime))
                    rv.setTextViewText(R.id.text_sunset_value, outputFormat.format(sunsetTime))

                    manager.updateAppWidget(widgetId, rv)
                }
            }
        }
    }

    private fun cacheData(sunrise: String, sunset: String) {
        val editor = prefs.edit()

        editor.putString(PREFS_SUNRISE, sunrise)
        editor.putString(PREFS_SUNSET, sunset)
        editor.apply()
    }

    private fun loadCache() {
        if (prefs.contains(PREFS_SUNRISE) && prefs.contains(PREFS_SUNSET)) {
            val sunrise = prefs.getString(PREFS_SUNRISE, PREFS_SUNRISE_DEFAULT)
            val sunset = prefs.getString(PREFS_SUNSET, PREFS_SUNSET_DEFAULT)

            if (sunrise != null && sunset != null)
                updateWidgets(sunrise, sunset)
        }
    }
}
