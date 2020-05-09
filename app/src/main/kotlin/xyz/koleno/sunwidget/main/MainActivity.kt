package xyz.koleno.sunwidget.main

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.activity_main_full.*
import kotlinx.android.synthetic.main.activity_main_min.*
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import xyz.koleno.sunwidget.R
import xyz.koleno.sunwidget.widget.SunWidgetProvider

/**
 * Main activity
 * @author Dusan Koleno
 */
@Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainActivityViewModel
    private var menu: Menu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(MainActivityViewModel::class.java)

        viewModel.action.observe(this, Observer { action ->
            when (action) {
                MainActivityViewModel.Action.REQUEST_PERM -> {
                    showPermissionsDialog(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION))
                }
                MainActivityViewModel.Action.LOAD_FULL_CONTENT -> {
                    loadFullContent()
                }
                MainActivityViewModel.Action.LOAD_MIN_CONTENT -> {
                    loadMinContent()
                }
                MainActivityViewModel.Action.UPDATE_WIDGETS -> {
                    updateWidgets()
                }
                MainActivityViewModel.Action.UPDATE_FINISH -> {
                    updateWidgets()

                    val widgetId = intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                    val resultValue = Intent()
                    resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    setResult(Activity.RESULT_OK, resultValue)
                    finish()
                }
            }
        })

        viewModel.locationActive.observe(this, Observer {
            if (it) {
                startButtonAnimation()
            } else {
                stopButtonAnimation()
            }
        })

        viewModel.message.observe(this, Observer {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
        })

        viewModel.dialog.observe(this, Observer {
            AlertDialog.Builder(this)
                    .setTitle(it.title)
                    .setMessage(it.message)
                    .setPositiveButton(R.string.ok, null)
                    .show()
        })

        viewModel.location.observe(this, Observer {
            it?.let {
                if (map != null) {
                    map.controller.setCenter(GeoPoint(it.latitude, it.longitude))
                } else {
                    edit_text_longitude.setText(it.longitude.toString())
                    edit_text_latitude.setText(it.latitude.toString())
                }
            }
        })

        viewModel.init(checkWritePermission() && checkLocationPermission(), openedFromWidget())
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        this.menu = menu

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.useMap) {
            viewModel.useMapClicked()
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        super.onPause()
        viewModel.pause()
    }

    /**
     * Loads map and other content in case the permissions are OK
     */
    private fun loadFullContent() {
        setContentView(R.layout.activity_main_full)
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        menu?.findItem(R.id.useMap)?.isVisible = false

        map.setMultiTouchControls(true) // zoom with fingers
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER) // zoom with buttons disabled
        map.isTilesScaledToDpi = true
        map.controller.setZoom(ZOOM_DEFAULT)

        button_location.setOnClickListener { viewModel.locationButtonClicked() }

        button_save.setOnClickListener {
            val center = map.mapCenter as GeoPoint
            viewModel.saveCoordinates(center.latitude.toFloat(), center.longitude.toFloat())
        }
    }

    /**
     * Loads simple input fields for the coordinates
     */
    private fun loadMinContent() {
        setContentView(R.layout.activity_main_min)
        menu?.findItem(R.id.useMap)?.isVisible = true

        button.setOnClickListener {
            viewModel.saveCoordinates(edit_text_latitude.text.toString().toFloatOrNull(), edit_text_longitude.text.toString().toFloatOrNull())
        }
    }


    /**
     * Starts button animation
     */
    private fun startButtonAnimation() {
        button_location?.startAnimation(AnimationUtils.loadAnimation(this, R.anim.animation_button_location))
    }

    /**
     * Stops button animation
     */
    private fun stopButtonAnimation() {
        button_location?.clearAnimation()
    }

    /**
     * Start service for updating the widgets
     */
    private fun updateWidgets() {
        val widgetIds = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, SunWidgetProvider::class.java))
        val intent = Intent(this, SunWidgetProvider::class.java)
        intent.action = SunWidgetProvider.ACTION_RUN_UPDATE
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
        sendBroadcast(intent)
    }

    /**
     * Shows dialog that requests permissions
     */
    private fun showPermissionsDialog(permissions: Array<String>) {
        AlertDialog.Builder(this)
                .setTitle(R.string.permissions_dialog_title)
                .setMessage(R.string.permissions_dialog_message)
                .setPositiveButton(R.string.permissions_dialog_button) { _, _ -> ActivityCompat.requestPermissions(this@MainActivity, permissions, PERMISSIONS) }
                .setNegativeButton(R.string.permissions_dialog_button_no) { _, _ -> viewModel.init(false, openedFromWidget()) }
                .show()
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
                viewModel.init(true, openedFromWidget())
            } else {
                viewModel.init(false, openedFromWidget())
            }
        }
    }

    /**
     * Checks if write permission for maps was granted
     */
    private fun checkWritePermission(): Boolean {
        return PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    /**
     * Checks if location permission was granted
     */
    private fun checkLocationPermission(): Boolean {
        return PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /**
     * Checks if app was opened from widget
     */
    private fun openedFromWidget(): Boolean {
        val extras = intent.extras
        return extras != null && extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) != AppWidgetManager.INVALID_APPWIDGET_ID
    }

    companion object {
        private const val PERMISSIONS = 1
        private const val ZOOM_DEFAULT = 13.0
    }
}
