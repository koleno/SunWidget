package xyz.koleno.sunwidget;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.widget.RemoteViews;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Service that updates the widgets
 *
 * @author Dusan Koleno
 */
public class UpdateService extends Service {

    private int[] widgetIds;
    private AppWidgetManager manager;
    private float longitude;
    private float latitude;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null && intent.getExtras() != null) {
            // get everything necessary from the bundle
            manager = AppWidgetManager.getInstance(this.getApplicationContext());
            widgetIds = intent.getIntArrayExtra("widgetIds");
            longitude = intent.getFloatExtra("longitude", 0.0f);
            latitude = intent.getFloatExtra("latitude", 0.0f);

            // start background task
            new ClientTask().execute();
        }

        return START_STICKY;
    }

    /**
     * Updates widgets - called when data are available
     *
     * @param json received data
     */
    private void updateWidgets(JSONObject json) {
        try {
            JSONObject results = json.getJSONObject("results");

            for (int widgetId : widgetIds) {
                RemoteViews rv = new RemoteViews(this.getApplicationContext().getPackageName(), R.layout.sunwidget);

                DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH);
                Date sunrise = dateFormat.parse(results.getString("sunrise"));
                Date sunset = dateFormat.parse(results.getString("sunset"));

                // format for the widget display
                DateFormat outputFormat = new SimpleDateFormat("HH:mm");

                rv.setTextViewText(R.id.sunriseTimeTextView, outputFormat.format(sunrise));
                rv.setTextViewText(R.id.sunsetTimeTextView, outputFormat.format(sunset));

                manager.updateAppWidget(widgetId, rv);
            }

        } catch (ParseException | JSONException e) {
            // do nothing on json exception, widgets will be updated next time the json string is received correctly
        }

        stopSelf(); // stop the service until next schedule update
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Background task for downloading the json from sunrise-sunset.org API
     */
    private class ClientTask extends AsyncTask<Void, Void, JSONObject> {
        @Override
        protected JSONObject doInBackground(Void... voids) {

            StringBuilder response = new StringBuilder();
            JSONObject json = null;

            try {
                URL url = new URL("http://api.sunrise-sunset.org/json?lat=" + latitude + "&lng=" + longitude + "&formatted=0");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                int responseCode = connection.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // read the json
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line + "\n");
                    }
                    reader.close();
                }

                json = new JSONObject(response.toString());

            } catch (JSONException | IOException e) {
                return null; // return nothing on error
            }

            return json;
        }

        @Override
        protected void onPostExecute(JSONObject json) {
            if(json != null) { // update widgets when json was received
                updateWidgets(json);
            }
        }

    }

}
