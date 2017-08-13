package xyz.koleno.sunwidget;

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

/**
 * Main activity
 * @author Dusan Koleno
 */
public class MainActivity extends AppCompatActivity implements LocationListener {

    private static final int PERMISSIONS = 1;

    private static final int ZOOM_DEFAULT = 13;
    private static final int ZOOM_NO_LOCATION = 1;

    private SharedPreferences prefs;
    private MapView map;
    private ImageButton currentLocButton;
    private LocationManager locMgr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        locMgr = (LocationManager) getSystemService(LOCATION_SERVICE);

        loadContentForPermissions();
    }

    /**
     * Checks permissions for writing and accessing location and loads content accordingly
     * Full layout is loaded only if there is a permission to write (for osm maps) and access location
     */
    private void loadContentForPermissions() {
        if (checkWritePermission() != PackageManager.PERMISSION_GRANTED ||
                checkLocationPermission() != PackageManager.PERMISSION_GRANTED) { // check permissions
            showPermissionsDialog(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION});
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
        setMapZoom(ZOOM_DEFAULT);

        currentLocButton = (ImageButton) findViewById(R.id.button_location);
        currentLocButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setLocation();
            }
        });

        Button saveButton = (Button) findViewById(R.id.button_save);
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
            setMapCenter(0.0, 0.0);
            setMapZoom(ZOOM_NO_LOCATION);
        }
    }

    /**
     * Gets location and shows it on the map
     */
    private void setLocation() {
        // check permissions
        if(checkLocationPermission() == PackageManager.PERMISSION_GRANTED) {
            startButtonAnimation();

            // check location services
            boolean network = locMgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            boolean gps = locMgr.isProviderEnabled(LocationManager.GPS_PROVIDER);

            Location lastKnownLocation = null;

            if (!network && !gps) {
                Toast.makeText(this, R.string.location_enable, Toast.LENGTH_SHORT).show();
            } else {
                if (gps) { // gps location
                    locMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10, 0, this);
                    lastKnownLocation = locMgr.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                } else { // network location
                    locMgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10, 0, this);
                    lastKnownLocation = locMgr.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }

            }

            // display last known location right away
            if(lastKnownLocation != null) {
                setMapCenter(lastKnownLocation);
            }
        }
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
     * Sets zoom
     *
     * @param zoom
     */
    public void setMapZoom(int zoom) {
        map.getController().setZoom(zoom);
    }

    /**
     * Loads simple input fields for the coordinates
     */
    private void loadMinContent() {
        setContentView(R.layout.activity_main_min);

        final EditText longitudeEditText = (EditText) findViewById(R.id.edit_text_longitude);
        final EditText latitudeEditText = (EditText) findViewById(R.id.edit_text_latitude);

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

    private void showSaveSuccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.success_dialog_title)
                .setMessage(R.string.success_dialog_message)
                .setPositiveButton(R.string.success_dialog_button, null)
                .show();    }

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

        // check if the activity was opened directly from widget, if yes, then close activity on save and notify widget
        Bundle extras = getIntent().getExtras();
        int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        if(extras != null &&
                (widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)) != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Toast.makeText(this, R.string.coordinates_saved, Toast.LENGTH_SHORT).show();

            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            setResult(RESULT_OK, resultValue);
            finish();
        } else {
            showSaveSuccessDialog();
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

    /**
     * Shows dialog that requests permissions
     * @param permissions
     */
    private void showPermissionsDialog(final String[] permissions) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.permissions_dialog_title)
                .setMessage(R.string.permissions_dialog_message)
                .setPositiveButton(R.string.permissions_dialog_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ActivityCompat.requestPermissions(MainActivity.this, permissions, PERMISSIONS);
                    }
                })
                .setNegativeButton(R.string.permissions_dialog_button_no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        loadMinContent();
                    }
                })
                .show();
    }

    /**
     * Checks write permission
     * @return flag indicating permission status (e.g. PERMISSION_GRANTED)
     */
    private int checkWritePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    /**
     * Checks location permission
     * @return flag indicating permission status (e.g. PERMISSION_GRANTED)
     */
    private int checkLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
    }

    /**
     * Starts button animation
     */
    public void startButtonAnimation() {
        currentLocButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.animation_button_location));
    }

    /**
     * Stops button animation
     */
    public void stopButtonAnimation() {
        currentLocButton.clearAnimation();
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

    @Override
    public void onLocationChanged(Location location) {
        setMapCenter(location);
        locMgr.removeUpdates(this);
        stopButtonAnimation();
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {}

    @Override
    public void onProviderEnabled(String s) {}

    @Override
    public void onProviderDisabled(String s) {}
}
