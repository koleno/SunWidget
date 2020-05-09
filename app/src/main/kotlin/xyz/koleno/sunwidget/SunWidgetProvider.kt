package xyz.koleno.sunwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.preference.PreferenceManager

/**
 * Broadcast receiver that controls the widgets
 * @author Dusan Koleno
 */
class SunWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_NO_CONNECTION = "actionNoConnection" // no internet connection notification
        const val ACTION_UPDATE_WIDGETS = "actionUpdateViews" // sent by update service to update views with new data
        const val ACTION_RUN_UPDATE = "actionRunUpdate" // generated after refresh button click to start update service
    }

    /**
     * Called by the system when widgets need to be updated
     */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = PrefHelper(PreferenceManager.getDefaultSharedPreferences(context))

        // start service if user's location is set
        if (prefs.hasLocation()) {
            updateWidgetsFromSaved(context, appWidgetIds) // first get saved data
            UpdateService.enqueueWork(context, appWidgetIds)
        } else {
            // notify user about missing location
            updateWidgets(context, appWidgetIds, context.resources.getString(R.string.no_location), context.resources.getString(R.string.no_location))
        }
    }

    /**
     * Custom actions used to handle various widget updates
     */
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PrefHelper(PreferenceManager.getDefaultSharedPreferences(context))
        val widgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                ?: return // stop if no widget ids

        when (intent.action) {
            ACTION_NO_CONNECTION -> {
                updateWidgets(context, widgetIds, context.getString(R.string.no_internet), context.getString(R.string.no_internet))
            }
            ACTION_UPDATE_WIDGETS -> {
                updateWidgetsFromSaved(context, widgetIds)
            }
            ACTION_RUN_UPDATE -> {
                if (!prefs.hasTimes()) { // no previous times saved, show loading
                    updateWidgets(context, widgetIds, context.getString(R.string.loading), context.getString(R.string.loading))
                }

                // start the update service
                UpdateService.enqueueWork(context, widgetIds)
            }
        }

        super.onReceive(context, intent)
    }

    /**
     * Generates widget update intent for the refresh button click
     */
    private fun generateUpdateIntent(context: Context, appWidgetIds: IntArray): PendingIntent {
        val intent = Intent(context, javaClass)
        intent.action = ACTION_RUN_UPDATE
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        return PendingIntent.getBroadcast(context, 0, intent, 0)
    }

    /**
     * Updates widgets with given data
     */
    private fun updateWidgets(context: Context, appWidgetIds: IntArray, sunriseText: String, sunsetText: String) {
        val manager = AppWidgetManager.getInstance(context.applicationContext)
        val remoteViews = RemoteViews(context.applicationContext.packageName, R.layout.sunwidget)
        remoteViews.setOnClickPendingIntent(R.id.button_refresh, generateUpdateIntent(context, appWidgetIds))

        for (widgetId in appWidgetIds) {
            remoteViews.setTextViewText(R.id.text_sunrise_value, sunriseText)
            remoteViews.setTextViewText(R.id.text_sunset_value, sunsetText)
            manager.updateAppWidget(widgetId, remoteViews)
        }
    }

    /**
     * Updates widgets from saved data
     */
    private fun updateWidgetsFromSaved(context: Context, appWidgetIds: IntArray) {
        val prefs = PrefHelper(PreferenceManager.getDefaultSharedPreferences(context))
        if (prefs.hasTimes()) {
            val data = prefs.loadTimes()
            updateWidgets(context, appWidgetIds, data.sunrise, data.sunset)
        }
    }
}
