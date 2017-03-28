package xyz.koleno.sunwidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
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

        // do something only if coordinates are set
        if(prefs.contains("longitude") && prefs.contains("latitude")) {
            Intent intent = new Intent(context, UpdateService.class);
            Bundle bundle = new Bundle();
            bundle.putFloat("longitude", prefs.getFloat("longitude", 0.0f));
            bundle.putFloat("latitude", prefs.getFloat("latitude", 0.0f));
            bundle.putIntArray("widgetIds", appWidgetIds);
            intent.putExtras(bundle);

            // start the update service on scheduled update
            context.startService(intent);
        } else {
            coordsNotAvailable(context, appWidgetIds);
        }
    }

    /**
     * Informs users about missing location
     *
     * @param context
     * @param appWidgetIds
     */
    private void coordsNotAvailable(Context context, int[] appWidgetIds) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context.getApplicationContext());

        for (int widgetId : appWidgetIds) {
            RemoteViews rv = new RemoteViews(context.getApplicationContext().getPackageName(), R.layout.sunwidget);


            rv.setTextViewText(R.id.sunriseTimeTextView,  context.getResources().getString(R.string.no_location));
            rv.setTextViewText(R.id.sunsetTimeTextView, context.getResources().getString(R.string.no_location));

            manager.updateAppWidget(widgetId, rv);
        }

    }
}
