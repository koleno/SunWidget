package xyz.koleno.sunwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.support.v4.app.JobIntentService
import android.widget.RemoteViews
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service that updates the widgets
 *
 * @author Dusan Koleno
 */
class UpdateService : JobIntentService() {

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

            // start download
            val json = download()

            // update widgets
            updateWidgets(json)
        }
    }

    /**
     * Updates widgets - called when data are available
     *
     * @param json received data
     */
    private fun updateWidgets(json: JSONObject) {
        try {
            val results = json.getJSONObject("results")

            for (widgetId in widgetIds) {
                val rv = RemoteViews(this.applicationContext.packageName, R.layout.sunwidget)

                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH)
                val sunrise = dateFormat.parse(results.getString("sunrise"))
                val sunset = dateFormat.parse(results.getString("sunset"))

                // format for the widget display
                val outputFormat = SimpleDateFormat("HH:mm")

                rv.setTextViewText(R.id.text_sunrise_value, outputFormat.format(sunrise))
                rv.setTextViewText(R.id.text_sunset_value, outputFormat.format(sunset))

                manager.updateAppWidget(widgetId, rv)
            }

        } catch (e: Exception) {
            // ignore, parse error, will try next time
        }
    }

    /**
     * Downloads data from sunrise-sunset.org API
     */
    private fun download() : JSONObject {
        val response = StringBuilder()

        try {
            val url = URL("http://api.sunrise-sunset.org/json?lat=$latitude&lng=$longitude&formatted=0")
            val connection = url.openConnection() as HttpURLConnection

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // read the json
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                reader.forEachLine {
                    response.append(it + "\n")
                }
                reader.close()

                return JSONObject(response.toString())
            }
        } catch (e: Exception) {
            // ignore, will try next time
        }

        return JSONObject()
    }
}
