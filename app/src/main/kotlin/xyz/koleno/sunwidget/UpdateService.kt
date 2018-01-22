package xyz.koleno.sunwidget

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.AsyncTask
import android.os.IBinder
import android.widget.RemoteViews

import org.json.JSONException
import org.json.JSONObject

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Service that updates the widgets
 *
 * @author Dusan Koleno
 */
class UpdateService : Service() {

    private lateinit var widgetIds: IntArray
    private lateinit var manager: AppWidgetManager
    private var longitude: Float = 0.0f
    private var latitude: Float = 0.0f

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.extras != null) {
            // get everything necessary from the bundle
            manager = AppWidgetManager.getInstance(this.applicationContext)
            widgetIds = intent.getIntArrayExtra("widgetIds")
            longitude = intent.getFloatExtra("longitude", longitude)
            latitude = intent.getFloatExtra("latitude", latitude)

            // start background task
            ClientTask().execute()
        }

        return Service.START_STICKY
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

        } catch (e: ParseException) {
            // do nothing on json exception, widgets will be updated next time the json string is received correctly
        } catch (e: JSONException) {
        }

        stopSelf() // stop the service until next schedule update
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    /**
     * Background task for downloading the json from sunrise-sunset.org API
     */
    private inner class ClientTask : AsyncTask<Void, Void, JSONObject>() {
        override fun doInBackground(vararg voids: Void): JSONObject? {

            val response = StringBuilder()
            var json: JSONObject? = null

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
                }

                json = JSONObject(response.toString())

            } catch (e: JSONException) {
                return null // return nothing on error
            } catch (e: IOException) {
                return null
            }

            return json
        }

        override fun onPostExecute(json: JSONObject?) {
            if (json != null) { // update widgets when json was received
                updateWidgets(json)
            }
        }

    }

}
