package xyz.koleno.sunwidget;

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

/**
 * Main activity
 * @author Dusan Koleno
 */
public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONS = 1;
    private SharedPreferences prefs;
    private MapView map;
    private Button currentLocButton;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        loadContentForPermissions();
    }

    /**
     * Checks permissions for writing and accessing location and loast content accordingly
     */
    private void loadContentForPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { // check permission

            new AlertDialog.Builder(this)
                    .setTitle(R.string.permissions_dialog_title)
                    .setMessage(R.string.permissions_dialog_message)
                    .setPositiveButton(R.string.permissions_dialog_button, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS); // request, if not granted
                        }
                    })
                    .setNegativeButton(R.string.permissions_dialog_button_no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            loadMinContent();
                        }
                    })
                    .show();
        } else {
            loadFullContent();
        }
    }

    /**
     * Loads map and other content in case the permissions are OK
     */
    private void loadFullContent() {
        setContentView(R.layout.activity_main_full);

        map = (MapView) findViewById(R.id.map);
        map.setMultiTouchControls(true); // zoom with fingers
        map.setBuiltInZoomControls(true); // zoom with buttons
        map.setTilesScaledToDpi(true);
        map.getController().setZoom(13);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        currentLocButton = (Button) findViewById(R.id.currentLocationButton);
        currentLocButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setLocation();
            }
        });

        Button saveButton = (Button) findViewById(R.id.saveButton);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                GeoPoint center = (GeoPoint) map.getMapCenter();
                saveCoordinates((float)center.getLatitude(), (float)center.getLongitude());
            }
        });

        // check preferences and load coordinates from them
        if(prefs.contains("longitude") && prefs.contains("latitude")) {
            setMapCenter(prefs.getFloat("latitude", 0.0f), prefs.getFloat("longitude", 0.0f));
        } else { // no preferences, load current location
            setLocation();
        }
    }

    /**
     * Shows Current location buttons
     */
    private void enableButton() {
            progressBar.setVisibility(View.INVISIBLE);
            currentLocButton.setVisibility(View.VISIBLE);
    }

    /**
     * Shows progress bar instead of current location button
     */
    private void enableProgressBar() {
        progressBar.setVisibility(View.VISIBLE);
        currentLocButton.setVisibility(View.INVISIBLE);
    }

    /**
     * Get location ans show it on the map
     */
    private void setLocation() {
        enableProgressBar();

        LocationManager locMgr = (LocationManager) getSystemService(LOCATION_SERVICE);

        // try last known location with all providers
        for(String provider : locMgr.getAllProviders()) {
            try {
                Location lastKnownLocation = locMgr.getLastKnownLocation(provider);

                if(lastKnownLocation != null) {
                    setMapCenter(lastKnownLocation);
                    enableButton();
                    return;
                }

            } catch(SecurityException e) {
                continue;
            }
        }

        // last known location wasn't found, inform user
        enableButton();
        Toast.makeText(this, R.string.location_unavailable, Toast.LENGTH_SHORT).show();
    }


    /**
     * Sets map to particular location
     * @param loc location
     */
    public void setMapCenter(Location loc) {
        if(loc != null) {
            setMapCenter(loc.getLatitude(), loc.getLongitude());
        }
    }


    /**
     * Sets map to particular location
     *
     * @param latitude
     * @param longitude
     */
    public void setMapCenter(double latitude, double longitude) {
        map.getController().setCenter(new GeoPoint(latitude, longitude));
    }

    /**
     * Loads simple input fields for the coordinates
     */
    private void loadMinContent() {
        setContentView(R.layout.activity_main_min);

        final EditText longitudeEditText = (EditText) findViewById(R.id.longitudeEditText);
        final EditText latitudeEditText = (EditText) findViewById(R.id.latitudeEditText);

        // check preferences and load them
        if(prefs.contains("longitude")) {
            longitudeEditText.setText(Float.toString(prefs.getFloat("longitude", 0.0f)));
        }

        if(prefs.contains("latitude")) {
            latitudeEditText.setText(Float.toString(prefs.getFloat("latitude", 0.0f)));
        }

        Button button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(longitudeEditText.getText().toString().isEmpty() || latitudeEditText.getText().toString().isEmpty()) {
                    showEmptyDialog();
                } else {
                    saveCoordinates(Float.valueOf(latitudeEditText.getText().toString()), Float.valueOf(longitudeEditText.getText().toString()));
                }
            }
        });
    }

    /**
     * Shows warning about empty coordinates provided
     */
    private void showEmptyDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.empty_dialog_title)
                .setMessage(R.string.empty_dialog_message)
                .setPositiveButton(R.string.empty_dialog_button, null)
                .show();
    }

    /**
     * Saves coordinates to the shared preferences
     * @param latitude
     * @param longitude
     */
    private void saveCoordinates(float latitude, float longitude) {
        SharedPreferences.Editor editor = prefs.edit();

        editor.putFloat("latitude", latitude);
        editor.putFloat("longitude", longitude);

        editor.apply();

        notifyWidgets();
        Toast.makeText(this, R.string.coordinates_saved, Toast.LENGTH_SHORT).show();

        // check if the activity was opened directly from widget, if yes, then close activity on save and notify widget
        Bundle extras = getIntent().getExtras();
        int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        if(extras != null &&
                (widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)) != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            setResult(RESULT_OK, resultValue);
            finish();
        }
    }

    /**
     * Notifies widgets about new coordinates
     */
    private void notifyWidgets() {
        Intent intent = new Intent(this, SunWidgetProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);

        int widgetIds[] = AppWidgetManager.getInstance(getApplication()).getAppWidgetIds(new ComponentName(getApplication(), SunWidgetProvider.class));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);

        sendBroadcast(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if(requestCode == PERMISSIONS) {

            // check if all permissions are granted
            boolean granted = true;
            for(int result : grantResults) {
                if(result == PackageManager.PERMISSION_DENIED) {
                    granted = false;
                }
            }

            if(granted) {
                loadFullContent();
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
                loadMinContent();
            }
        }
    }
}
