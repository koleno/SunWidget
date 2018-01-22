package xyz.koleno.sunwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.RemoteViews

/**
 * Controls updates of the widget
 * @author Dusan Koleno
 */
class SunWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val component = ComponentName(context, javaClass)
        val remoteViews = RemoteViews(context.applicationContext.packageName, R.layout.sunwidget)

        // activate refresh button
        remoteViews.setOnClickPendingIntent(R.id.button_refresh, generateUpdateIntent(context, appWidgetIds))
        appWidgetManager.updateAppWidget(component, remoteViews)

        // start service if user's location is set
        if (prefs.contains("longitude") && prefs.contains("latitude")) {
            val intent = Intent(context, UpdateService::class.java)
            val bundle = Bundle()
            bundle.putFloat("longitude", prefs.getFloat("longitude", 0.0f))
            bundle.putFloat("latitude", prefs.getFloat("latitude", 0.0f))
            bundle.putIntArray("widgetIds", appWidgetManager.getAppWidgetIds(component))
            intent.putExtras(bundle)

            // start the update service
            context.startService(intent)
        } else {
            coordsNotAvailable(context, appWidgetIds)
        }
    }

    /**
     * Generates widget update intent
     *
     * @param context
     * @param appWidgetIds
     * @return intent
     */
    private fun generateUpdateIntent(context: Context, appWidgetIds: IntArray): PendingIntent {
        val intent = Intent(context, javaClass)
        intent.action = "android.appwidget.action.APPWIDGET_UPDATE"
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)

        return PendingIntent.getBroadcast(context, 0, intent, 0)
    }

    /**
     * Informs users about missing location
     *
     * @param context
     * @param appWidgetIds
     */
    private fun coordsNotAvailable(context: Context, appWidgetIds: IntArray) {
        val manager = AppWidgetManager.getInstance(context.applicationContext)
        val remoteViews = RemoteViews(context.applicationContext.packageName, R.layout.sunwidget)

        for (widgetId in appWidgetIds) {
            remoteViews.setTextViewText(R.id.text_sunrise_value, context.resources.getString(R.string.no_location))
            remoteViews.setTextViewText(R.id.text_sunset_value, context.resources.getString(R.string.no_location))

            manager.updateAppWidget(widgetId, remoteViews)
        }

    }
}
