package xyz.koleno.sunwidget

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * Main activity
 * @author Dusan Koleno
 */
class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var prefs: SharedPreferences
    private lateinit var map: MapView
    private lateinit var currentLocButton: ImageButton
    private lateinit var locMgr: LocationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        locMgr = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        loadContentForPermissions()
    }

    /**
     * Checks permissions for writing and accessing location and loads content accordingly
     * Full layout is loaded only if there is a permission to write (for osm maps) and access location
     */
    private fun loadContentForPermissions() {
        if (checkWritePermission() != PackageManager.PERMISSION_GRANTED || checkLocationPermission() != PackageManager.PERMISSION_GRANTED) { // check permissions
            showPermissionsDialog(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION))
        } else {
            loadFullContent()
        }
    }

    /**
     * Loads map and other content in case the permissions are OK
     */
    private fun loadFullContent() {
        setContentView(R.layout.activity_main_full)

        map = findViewById<View>(R.id.map) as MapView
        map.setMultiTouchControls(true) // zoom with fingers
        map.setBuiltInZoomControls(true) // zoom with buttons
        map.isTilesScaledToDpi = true
        setMapZoom(ZOOM_DEFAULT)

        currentLocButton = findViewById<View>(R.id.button_location) as ImageButton
        currentLocButton.setOnClickListener { setLocation() }

        val saveButton = findViewById<View>(R.id.button_save) as Button
        saveButton.setOnClickListener {
            val center = map.mapCenter as GeoPoint
            saveCoordinates(center.latitude.toFloat(), center.longitude.toFloat())
        }

        // check preferences and load coordinates from them
        if (prefs.contains("longitude") && prefs.contains("latitude")) {
            setMapCenter(prefs.getFloat("latitude", 0.0f).toDouble(), prefs.getFloat("longitude", 0.0f).toDouble())
        } else { // no preferences, load current location
            setMapCenter(0.0, 0.0)
            setMapZoom(ZOOM_NO_LOCATION)
        }
    }

    /**
     * Gets location and shows it on the map
     */
    private fun setLocation() {
        // check permissions
        if (checkLocationPermission() == PackageManager.PERMISSION_GRANTED) {
            startButtonAnimation()

            // check location services
            val network = locMgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            val gps = locMgr.isProviderEnabled(LocationManager.GPS_PROVIDER)

            var lastKnownLocation: Location? = null

            if (!network && !gps) {
                Toast.makeText(this, R.string.location_enable, Toast.LENGTH_SHORT).show()
            } else {
                if (gps) { // gps location
                    locMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10, 0f, this)
                    lastKnownLocation = locMgr.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                } else { // network location
                    locMgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10, 0f, this)
                    lastKnownLocation = locMgr.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                }

            }

            // display last known location right away
            if (lastKnownLocation != null) {
                setMapCenter(lastKnownLocation)
            }
        }
    }

    /**
     * Sets map to particular location
     * @param loc location
     */
    fun setMapCenter(loc: Location?) {
        if (loc != null) {
            setMapCenter(loc.latitude, loc.longitude)
        }
    }

    /**
     * Sets map to particular location
     *
     * @param latitude
     * @param longitude
     */
    fun setMapCenter(latitude: Double, longitude: Double) {
        map.controller.setCenter(GeoPoint(latitude, longitude))
    }

    /**
     * Sets zoom
     *
     * @param zoom
     */
    fun setMapZoom(zoom: Int) {
        map.controller.setZoom(zoom)
    }

    /**
     * Loads simple input fields for the coordinates
     */
    private fun loadMinContent() {
        setContentView(R.layout.activity_main_min)

        val longitudeEditText = findViewById<View>(R.id.edit_text_longitude) as EditText
        val latitudeEditText = findViewById<View>(R.id.edit_text_latitude) as EditText

        // check preferences and load them
        if (prefs.contains("longitude")) {
            longitudeEditText.setText(prefs.getFloat("longitude", 0.0f).toString())
        }

        if (prefs.contains("latitude")) {
            latitudeEditText.setText(prefs.getFloat("latitude", 0.0f).toString())
        }

        val button = findViewById<View>(R.id.button) as Button
        button.setOnClickListener {
            if (longitudeEditText.text.toString().isEmpty() || latitudeEditText.text.toString().isEmpty()) {
                showEmptyDialog()
            } else {
                saveCoordinates(java.lang.Float.valueOf(latitudeEditText.text.toString())!!, java.lang.Float.valueOf(longitudeEditText.text.toString())!!)
            }
        }
    }

    /**
     * Shows warning about empty coordinates provided
     */
    private fun showEmptyDialog() {
        AlertDialog.Builder(this)
                .setTitle(R.string.empty_dialog_title)
                .setMessage(R.string.empty_dialog_message)
                .setPositiveButton(R.string.empty_dialog_button, null)
                .show()
    }

    private fun showSaveSuccessDialog() {
        AlertDialog.Builder(this)
                .setTitle(R.string.success_dialog_title)
                .setMessage(R.string.success_dialog_message)
                .setPositiveButton(R.string.success_dialog_button, null)
                .show()
    }

    /**
     * Saves coordinates to the shared preferences
     * @param latitude
     * @param longitude
     */
    private fun saveCoordinates(latitude: Float, longitude: Float) {
        val editor = prefs.edit()

        editor.putFloat("latitude", latitude)
        editor.putFloat("longitude", longitude)
        editor.apply()

        notifyWidgets()

        // check if the activity was opened directly from widget, if yes, then close activity on save and notify widget
        val extras = intent.extras
        if (extras != null && extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            Toast.makeText(this, R.string.coordinates_saved, Toast.LENGTH_SHORT).show()

            val resultValue = Intent()
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        } else {
            showSaveSuccessDialog()
        }
    }

    /**
     * Notifies widgets about new coordinates
     */
    private fun notifyWidgets() {
        val intent = Intent(this, SunWidgetProvider::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE

        val widgetIds = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, SunWidgetProvider::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)

        sendBroadcast(intent)
    }

    /**
     * Shows dialog that requests permissions
     * @param permissions
     */
    private fun showPermissionsDialog(permissions: Array<String>) {
        AlertDialog.Builder(this)
                .setTitle(R.string.permissions_dialog_title)
                .setMessage(R.string.permissions_dialog_message)
                .setPositiveButton(R.string.permissions_dialog_button) { dialogInterface, i -> ActivityCompat.requestPermissions(this@MainActivity, permissions, PERMISSIONS) }
                .setNegativeButton(R.string.permissions_dialog_button_no) { dialogInterface, i -> loadMinContent() }
                .show()
    }

    /**
     * Checks write permission
     * @return flag indicating permission status (e.g. PERMISSION_GRANTED)
     */
    private fun checkWritePermission(): Int {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    /**
     * Checks location permission
     * @return flag indicating permission status (e.g. PERMISSION_GRANTED)
     */
    private fun checkLocationPermission(): Int {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /**
     * Starts button animation
     */
    fun startButtonAnimation() {
        currentLocButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.animation_button_location))
    }

    /**
     * Stops button animation
     */
    fun stopButtonAnimation() {
        currentLocButton.clearAnimation()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == PERMISSIONS) {

            // check if all permissions are granted
            var granted = true
            for (result in grantResults) {
                if (result == PackageManager.PERMISSION_DENIED) {
                    granted = false
                }
            }

            if (granted) {
                loadFullContent()
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
                loadMinContent()
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        setMapCenter(location)
        locMgr.removeUpdates(this)
        stopButtonAnimation()
    }

    override fun onStatusChanged(s: String, i: Int, bundle: Bundle) {}

    override fun onProviderEnabled(s: String) {}

    override fun onProviderDisabled(s: String) {}

    companion object {

        private val PERMISSIONS = 1

        private val ZOOM_DEFAULT = 13
        private val ZOOM_NO_LOCATION = 1
    }
}
