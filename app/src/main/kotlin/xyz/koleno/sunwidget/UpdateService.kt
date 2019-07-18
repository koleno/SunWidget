package xyz.koleno.sunwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
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
import xyz.koleno.sunwidget.api.json.Results
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
    var longitude: Float = 0.0f
    var latitude: Float = 0.0f

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
            widgetIds = intent.getIntArrayExtra("widgetIds")
            longitude = intent.getFloatExtra("longitude", longitude)
            latitude = intent.getFloatExtra("latitude", latitude)

            val retrofit = Retrofit.Builder().baseUrl(baseUrl).addConverterFactory(GsonConverterFactory.create()).build()
            val call = retrofit.create(DataService::class.java).getTimes(latitude, longitude)

            call.enqueue(object: Callback<xyz.koleno.sunwidget.api.json.ApiResponse>{
                override fun onFailure(call: Call<xyz.koleno.sunwidget.api.json.ApiResponse>, t: Throwable) {
                    Log.d(tag, "Failed to retrieve data from API", t)
                }

                override fun onResponse(call: Call<xyz.koleno.sunwidget.api.json.ApiResponse>, response: Response<ApiResponse>) {
                    if(response.code() == 200 && response.body()?.status.equals("OK")) {
                        response.body()?.results?.let {
                            updateWidgets(it)
                            Log.i(tag, "API data retrieved, updating widgets")
                        }
                    } else {
                        Log.d(tag, "Returned response code " + response.code() + " and status " + response.body()?.status)
                    }
                }

            })
        }
    }

    /**
     * Updates widgets - called when data are available
     *
     * @param json received data
     */
    private fun updateWidgets(results: Results) {
        for (widgetId in widgetIds) {
            val rv = RemoteViews(this.applicationContext.packageName, R.layout.sunwidget)

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH)
            val sunrise = dateFormat.parse(results.sunrise)
            val sunset = dateFormat.parse(results.sunset)

            // format for the widget display
            val outputFormat = SimpleDateFormat.getTimeInstance(DateFormat.SHORT)

            rv.setTextViewText(R.id.text_sunrise_value, outputFormat.format(sunrise))
            rv.setTextViewText(R.id.text_sunset_value, outputFormat.format(sunset))

            manager.updateAppWidget(widgetId, rv)
        }
    }
}
