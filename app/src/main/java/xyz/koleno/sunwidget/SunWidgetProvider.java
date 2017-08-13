package xyz.koleno.sunwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.RemoteViews;

/**
 * Controls updates of the widget
 * @author Dusan Koleno
 */
public class SunWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        ComponentName component = new ComponentName(context, getClass());
        RemoteViews remoteViews = new RemoteViews(context.getApplicationContext().getPackageName(), R.layout.sunwidget);

        // activate refresh button
        remoteViews.setOnClickPendingIntent(R.id.button_refresh, generateUpdateIntent(context, appWidgetIds));
        appWidgetManager.updateAppWidget(component, remoteViews);

        // start service if user's location is set
        if(prefs.contains("longitude") && prefs.contains("latitude")) {
            Intent intent = new Intent(context, UpdateService.class);
            Bundle bundle = new Bundle();
            bundle.putFloat("longitude", prefs.getFloat("longitude", 0.0f));
            bundle.putFloat("latitude", prefs.getFloat("latitude", 0.0f));
            bundle.putIntArray("widgetIds", appWidgetManager.getAppWidgetIds(component));
            intent.putExtras(bundle);

            // start the update service
            context.startService(intent);
        } else {
            coordsNotAvailable(context, appWidgetIds);
        }
    }

    /**
     * Generates widget update intent
     *
     * @param context
     * @param appWidgetIds
     * @return intent
     */
    private PendingIntent generateUpdateIntent(Context context, int[] appWidgetIds) {
        Intent intent = new Intent(context, getClass());
        intent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);

        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    /**
     * Informs users about missing location
     *
     * @param context
     * @param appWidgetIds
     */
    private void coordsNotAvailable(Context context, int[] appWidgetIds) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context.getApplicationContext());
        RemoteViews remoteViews = new RemoteViews(context.getApplicationContext().getPackageName(), R.layout.sunwidget);

        for (int widgetId : appWidgetIds) {
            remoteViews.setTextViewText(R.id.text_sunrise_value,  context.getResources().getString(R.string.no_location));
            remoteViews.setTextViewText(R.id.text_sunset_value, context.getResources().getString(R.string.no_location));

            manager.updateAppWidget(widgetId, remoteViews);
        }

    }
}
