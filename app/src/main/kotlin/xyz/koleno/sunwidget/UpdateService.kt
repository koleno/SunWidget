package xyz.koleno.sunwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.support.v4.app.JobIntentService
import android.util.Log
import android.widget.RemoteViews
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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

    private lateinit var widgetIds: IntArray
    private lateinit var manager: AppWidgetManager
    private lateinit var prefs: SharedPreferences
    private var longitude: Float = 0.0f
    private var latitude: Float = 0.0f

    /**
     * Convenience method for enqueuing work in to this service.
     */
    companion object {
        fun enqueueWork(context: Context, work: Intent) {
            enqueueWork(context, UpdateService::class.java, 1000, work)
        }
    }

    override fun onHandleWork(intent: Intent) {
        if (intent.extras != null) {
            // get everything necessary from the bundle
            manager = AppWidgetManager.getInstance(this.applicationContext)
            prefs = PreferenceManager.getDefaultSharedPreferences(this.applicationContext)
            widgetIds = intent.getIntArrayExtra("widgetIds")
            longitude = prefs.getFloat(MainActivity.PREFS_LONGITUDE, MainActivity.PREFS_LONGITUDE_DEFAULT)
            latitude = prefs.getFloat(MainActivity.PREFS_LATITUDE, MainActivity.PREFS_LATITUDE_DEFAULT)

            val retrofit = Retrofit.Builder().baseUrl(baseUrl).addConverterFactory(GsonConverterFactory.create()).build()
            val call = retrofit.create(DataService::class.java).getTimes(latitude, longitude)

            call.enqueue(object: Callback<xyz.koleno.sunwidget.api.json.ApiResponse>{
                override fun onFailure(call: Call<xyz.koleno.sunwidget.api.json.ApiResponse>, t: Throwable) {
                    loadCache()
                    Log.d(tag, "Failed to retrieve data from API", t)
                }

                override fun onResponse(call: Call<xyz.koleno.sunwidget.api.json.ApiResponse>, response: Response<ApiResponse>) {
                    if(response.code() == 200 && response.body()?.status.equals("OK")) {
                        response.body()?.results?.let {
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
    private fun updateWidgets(sunrise: String?, sunset: String?) {
        for (widgetId in widgetIds) {
            val rv = RemoteViews(this.applicationContext.packageName, R.layout.sunwidget)

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH)
            val sunriseTime = dateFormat.parse(sunrise)
            val sunsetTime = dateFormat.parse(sunset)

            // format for the widget display
            val outputFormat = SimpleDateFormat.getTimeInstance(DateFormat.SHORT)

            rv.setTextViewText(R.id.text_sunrise_value, outputFormat.format(sunriseTime))
            rv.setTextViewText(R.id.text_sunset_value, outputFormat.format(sunsetTime))

            manager.updateAppWidget(widgetId, rv)
        }
    }

    private fun cacheData(sunrise: String?, sunset: String?) {
        val editor = prefs.edit()

        editor.putString(MainActivity.PREFS_SUNRISE, sunrise)
        editor.putString(MainActivity.PREFS_SUNSET, sunset)
        editor.apply()
    }

    private fun loadCache() {
        if(prefs.contains(MainActivity.PREFS_SUNRISE) && prefs.contains(MainActivity.PREFS_SUNSET)) {
            val sunrise = prefs.getString(MainActivity.PREFS_SUNRISE, MainActivity.PREFS_SUNRISE_DEFAULT)
            val sunset = prefs.getString(MainActivity.PREFS_SUNSET, MainActivity.PREFS_SUNSET_DEFAULT)

            updateWidgets(sunrise, sunset)
        }
    }
}
