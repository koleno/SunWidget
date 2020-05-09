package xyz.koleno.sunwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.JobIntentService
import androidx.preference.PreferenceManager
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import xyz.koleno.sunwidget.api.DataService
import java.net.HttpURLConnection
import java.net.URL


/**
 * Service that updates widgets
 * Not using WorkManager due to https://commonsware.com/blog/2018/11/24/workmanager-app-widgets-side-effects.html
 *
 * @author Dusan Koleno
 */
class UpdateService : JobIntentService() {

    private lateinit var manager: AppWidgetManager
    private lateinit var prefs: PrefHelper
    private var widgetIds: IntArray? = null

    override fun onHandleWork(intent: Intent) {
        if (intent.extras != null) {
            // get everything necessary from the bundle
            manager = AppWidgetManager.getInstance(this.applicationContext)
            prefs = PrefHelper(PreferenceManager.getDefaultSharedPreferences(this.applicationContext))
            widgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            val location = prefs.loadLocation()

            // check internet connection
            if (!checkConnection() && !prefs.hasTimes()) {
                sendNoConnectionBroadcast(widgetIds)
                return
            }

            // try to retrieve data
            val retrofit = Retrofit.Builder().baseUrl(BASE_URL).addConverterFactory(GsonConverterFactory.create()).build()
            val call = retrofit.create(DataService::class.java).getTimes(location.latitude, location.longitude)

            try {
                val response = call.execute()
                if (response.code() == 200 && response.body()?.status.equals("OK")) {
                    response.body()?.results?.let {
                        if (it.sunrise == null || it.sunset == null) {
                            return
                        }

                        Log.i(TAG, "API data retrieved, updating widgets")
                        prefs.saveTimes(it.sunrise, it.sunset)
                        sendUpdateBroadcast(widgetIds)
                    }
                } else {
                    Log.d(TAG, "Returned response code " + response.code() + " and status " + response.body()?.status)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed to retrieve data from API" + e.message)
            }

        }
    }

    private fun sendNoConnectionBroadcast(widgetIds: IntArray?) {
        widgetIds?.let {
            val intent = Intent(this, SunWidgetProvider::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, it)
            intent.action = SunWidgetProvider.ACTION_NO_CONNECTION
            sendBroadcast(intent)
        }
    }


    private fun sendUpdateBroadcast(widgetIds: IntArray?) {
        widgetIds?.let {
            val intent = Intent(this, SunWidgetProvider::class.java)
            intent.action = SunWidgetProvider.ACTION_UPDATE_WIDGETS
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, it)
            sendBroadcast(intent)
        }
    }

    /**
     * Checks connections
     * Used when user initiates update
     */
    private fun checkConnection(): Boolean {
        return try {
            val endpoint = URL(BASE_URL)
            val connection = endpoint.openConnection() as HttpURLConnection
            connection.connectTimeout = 1500
            connection.connect()

            connection.responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        /**
         * Convenience method for enqueuing work in to this service.
         */
        fun enqueueWork(context: Context, widgetIds: IntArray) {
            val intent = Intent(context, UpdateService::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            enqueueWork(context, UpdateService::class.java, 1000, intent)
        }

        private const val TAG = BuildConfig.APPLICATION_ID
        private const val BASE_URL = "https://api.sunrise-sunset.org/"
    }

}
