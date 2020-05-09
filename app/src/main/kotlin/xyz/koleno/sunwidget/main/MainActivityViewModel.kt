package xyz.koleno.sunwidget.main

import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import xyz.koleno.sunwidget.PrefHelper
import xyz.koleno.sunwidget.R

/**
 * View model for the main activity
 * @author Dusan Koleno
 */
class MainActivityViewModel(application: Application) : AndroidViewModel(application), LocationListener {

    enum class Action { REQUEST_PERM, LOAD_FULL_CONTENT, LOAD_MIN_CONTENT, UPDATE_WIDGETS, UPDATE_FINISH }

    private var permissionRequested = false
    private var openedFromWidget = false

    val action = MutableLiveData<Action>()
    val message = MutableLiveData<String>()
    val dialog = MutableLiveData<DialogData>()
    val location = MutableLiveData<PrefHelper.LocationData>()
    val locationActive = MutableLiveData<Boolean>(false)

    private val resources: Resources = application.resources
    private val prefs: PrefHelper = PrefHelper(PreferenceManager.getDefaultSharedPreferences(application))
    private val locMgr: LocationManager = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun init(permGranted: Boolean, openedFromWidget: Boolean) {
        this.openedFromWidget = openedFromWidget

        if (!permGranted) {
            if (!permissionRequested) { // ask for permissions
                requestPermissions()
            } else { // permissions where not granted by user
                action.postValue(Action.LOAD_MIN_CONTENT)
                message.postValue(resources.getString(R.string.permission_denied))
                location.postValue(prefs.loadLocation())
            }
        } else { // permissions granted, load full content with map
            action.postValue(Action.LOAD_FULL_CONTENT)

            if (prefs.hasLocation()) {
                val stored = prefs.loadLocation()
                location.postValue(stored)
            } else {
                // if no location was loaded, try to find current location
                startLocationService()
            }
        }
    }

    private fun requestPermissions() {
        action.postValue(Action.REQUEST_PERM)
        permissionRequested = true
    }

    private fun startLocationService() {
        if (!locMgr.isProviderEnabled(LocationManager.GPS_PROVIDER) && !locMgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            // no gps or network provider enabled
            message.postValue(resources.getString(R.string.location_enable))
        } else {
            try {
                // choose best provider for medium accuracy
                val criteria = Criteria()
                criteria.horizontalAccuracy = Criteria.ACCURACY_MEDIUM
                val bestProvider = locMgr.getBestProvider(criteria, true)

                // first get best last known location
                var lastKnownLocation: Location? = null
                for (provider in locMgr.getProviders(false)) {
                    val current = locMgr.getLastKnownLocation(provider) ?: continue

                    if (lastKnownLocation == null) { // if no last location, set currently checked as last
                        lastKnownLocation = current
                    } else if (lastKnownLocation.accuracy > current.accuracy) { // if current is better then last know, use it
                        lastKnownLocation = current
                    }
                }

                // display last known location
                lastKnownLocation?.let {
                    this.location.postValue(PrefHelper.LocationData(it.latitude, it.longitude))
                }

                // register for location updates
                bestProvider?.let {
                    locationActive.postValue(true)
                    locMgr.requestLocationUpdates(it, 10, 0f, this)
                }
            } catch (e: SecurityException) {
                requestPermissions()
            }
        }
    }

    private fun stopLocationService() {
        locMgr.removeUpdates(this)
        locationActive.postValue(false)
    }

    /**
     * Saves coordinates to shared preferences
     */
    fun saveCoordinates(latitude: Float?, longitude: Float?) {
        if (latitude == null || longitude == null) {
            dialog.postValue(DialogData(resources.getString(R.string.empty_dialog_title), resources.getString(R.string.empty_dialog_message)))
            return
        }

        prefs.saveLocation(latitude, longitude)

        if (openedFromWidget) {
            message.postValue(resources.getString(R.string.coordinates_saved))
            action.postValue(Action.UPDATE_FINISH)
        } else {
            action.postValue(Action.UPDATE_WIDGETS)
            dialog.postValue(DialogData(resources.getString(R.string.success_dialog_title),
                    resources.getString(R.string.success_dialog_message)))
        }

        stopLocationService()
    }

    fun pause() {
        stopLocationService()
    }

    fun locationButtonClicked() {
        locationActive.value?.let {
            if (it) {
                stopLocationService()
            } else {
                startLocationService()
            }
        }
    }

    fun useMapClicked() {
        requestPermissions()
    }

    override fun onLocationChanged(location: Location?) {
        location?.let {
            this.location.postValue(PrefHelper.LocationData(it.latitude, it.longitude))

            if (it.accuracy < LOCATION_ACCURACY) { // stop service if high enough accuracy is achieved
                stopLocationService()
            }
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }

    override fun onProviderEnabled(provider: String?) {
    }

    override fun onProviderDisabled(provider: String?) {
    }

    data class DialogData(val title: String, val message: String)

    companion object {
        private const val LOCATION_ACCURACY = 10f
    }

}