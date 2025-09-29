package io.ionic.libs.iongeolocationlib.controller

import android.app.Activity
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import io.ionic.libs.iongeolocationlib.controller.helper.IONGLOCBuildConfig
import io.ionic.libs.iongeolocationlib.controller.helper.IONGLOCFallbackHelper
import io.ionic.libs.iongeolocationlib.controller.helper.IONGLOCGoogleServicesHelper
import io.ionic.libs.iongeolocationlib.model.IONGLOCException
import io.ionic.libs.iongeolocationlib.model.IONGLOCLocationOptions
import io.ionic.libs.iongeolocationlib.model.IONGLOCLocationResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first

/**
 * Entry point in IONGeolocationLib-Android
 *
 */
class IONGLOCController internal constructor(
    fusedLocationClient: FusedLocationProviderClient,
    private val locationManager: LocationManager,
    activityLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val googleServicesHelper: IONGLOCGoogleServicesHelper = IONGLOCGoogleServicesHelper(
        fusedLocationClient,
        activityLauncher
    ),
    private val fallbackHelper: IONGLOCFallbackHelper = IONGLOCFallbackHelper(locationManager)
) {

    constructor(
        context: Context,
        activityLauncher: ActivityResultLauncher<IntentSenderRequest>
    ) : this(
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context),
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager,
        activityLauncher = activityLauncher
    )

    private lateinit var resolveLocationSettingsResultFlow: MutableSharedFlow<Result<Unit>>
    private val watchLocationHandlers: MutableMap<String, LocationHandler> = mutableMapOf()
    private val watchIdsBlacklist: MutableList<String> = mutableListOf()

    /**
     * Obtains the device's location using FusedLocationProviderClient.
     * Tries to obtain the last retrieved location, and then gets a fresh one if necessary.
     * @param activity the Android activity from which the location request is being triggered
     * @param options IONGLOCLocationOptions object with the options to obtain the location with (e.g. timeout)
     * @return Result<IONGLOCLocationResult> object with either the location or an exception to be handled by the caller
     */
    suspend fun getCurrentPosition(
        activity: Activity,
        options: IONGLOCLocationOptions
    ): Result<IONGLOCLocationResult> {
        try {
            val checkResult: Result<Unit> =
                checkLocationPreconditions(activity, options, isSingleLocationRequest = true)
            return if (checkResult.isFailure && !options.enableLocationManagerFallback) {
                Result.failure(
                    checkResult.exceptionOrNull() ?: NullPointerException()
                )
            } else {
                val location: Location = if (!options.enableLocationManagerFallback) {
                    googleServicesHelper.getCurrentLocation(options)
                } else {
                    fallbackHelper.getCurrentLocation()
                }
                return Result.success(location.toOSLocationResult())
            }
        } catch (exception: Exception) {
            Log.d(LOG_TAG, "Error fetching location: ${exception.message}")
            return Result.failure(exception)
        }
    }

    /**
     * Function to be called by the client after returning from the activity
     * that is launched when resolving the ResolvableApiException in checkLocationSettings,
     * that prompts the user to enable the location if it is disabled.
     * @param resultCode to determine if the user enabled the location when prompted
     */
    suspend fun onResolvableExceptionResult(resultCode: Int) {
        resolveLocationSettingsResultFlow.emit(
            if (resultCode == Activity.RESULT_OK)
                Result.success(Unit)
            else
                Result.failure(
                    IONGLOCException.IONGLOCRequestDeniedException(
                        message = "Request to enable location denied."
                    )
                )
        )
    }

    /**
     * Checks if location services are enabled
     * @return true if location is enabled, false otherwise
     */
    fun areLocationServicesEnabled(): Boolean {
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    /**
     * Creates a callback for location updates.
     * @param activity the Android activity from which the location request is being triggered
     * @param options location request options to use
     * @param watchId a unique id identifying the watch
     * @return Flow in which location updates will be emitted
     */
    fun addWatch(
        activity: Activity,
        options: IONGLOCLocationOptions,
        watchId: String
    ): Flow<Result<List<IONGLOCLocationResult>>> = callbackFlow {
        try {
            fun onNewLocations(locations: List<Location>) {
                if (checkWatchInBlackList(watchId)) {
                    return
                }
                val locationResultList = locations.map { currentLocation ->
                    currentLocation.toOSLocationResult()
                }
                trySend(Result.success(locationResultList))
            }
            val checkResult: Result<Unit> =
                checkLocationPreconditions(activity, options, isSingleLocationRequest = true)
            if (checkResult.isFailure && !options.enableLocationManagerFallback) {
                trySend(
                    Result.failure(checkResult.exceptionOrNull() ?: NullPointerException())
                )
            } else {
                watchLocationHandlers[watchId] = if (!options.enableLocationManagerFallback) {
                    LocationHandler.Callback(object : LocationCallback() {
                        override fun onLocationResult(location: LocationResult) {
                            onNewLocations(location.locations)
                        }
                    }).also {
                        googleServicesHelper.requestLocationUpdates(options, it.callback)
                    }
                } else {
                    LocationHandler.Listener(object : LocationListenerCompat {
                        override fun onLocationChanged(location: Location) {
                            onNewLocations(listOf(location))
                        }

                        override fun onLocationChanged(locations: List<Location?>) {
                            locations.filterNotNull().takeIf { it.isNotEmpty() }?.let {
                                onNewLocations(it)
                            }
                        }
                    }).also {
                        fallbackHelper.requestLocationUpdates(options, it.listener)
                    }
                }
            }
        } catch (exception: Exception) {
            Log.d(LOG_TAG, "Error requesting location updates: ${exception.message}")
            trySend(Result.failure(exception))
        }

        awaitClose {
            clearWatch(watchId)
        }
    }

    /**
     * Clears a watch by removing its location update request
     * @param id the watch id
     * @return true if watch was cleared, false if watch was not found
     */
    fun clearWatch(id: String): Boolean = clearWatch(id, addToBlackList = true)

    /**
     * Checks if all preconditions for retrieving location are met
     * @param activity the Android activity from which the location request is being triggered
     * @param options location request options to use
     * @param isSingleLocationRequest true if request is for a single location, false if for location updates
     */
    private suspend fun checkLocationPreconditions(
        activity: Activity,
        options: IONGLOCLocationOptions,
        isSingleLocationRequest: Boolean
    ): Result<Unit> {
        // check timeout
        if (options.timeout <= 0) {
            return Result.failure(
                IONGLOCException.IONGLOCInvalidTimeoutException(
                    message = "Timeout needs to be a positive value."
                )
            )
        }
        // if meant to use fallback, then resolvable errors from Play Services Location don't need to be addressed
        val playServicesResult = googleServicesHelper.checkGooglePlayServicesAvailable(
            activity, shouldTryResolve = !options.enableLocationManagerFallback
        )
        if (playServicesResult.isFailure && !options.enableLocationManagerFallback) {
            return Result.failure(playServicesResult.exceptionOrNull() ?: NullPointerException())
        }

        resolveLocationSettingsResultFlow = MutableSharedFlow()
        val locationSettingsResult = googleServicesHelper.checkLocationSettings(
            activity,
            locationManager,
            options.copy(timeout = if (isSingleLocationRequest) 0 else options.timeout),
            shouldTryResolve = !options.enableLocationManagerFallback
        )

        return when (locationSettingsResult) {
            IONGLOCGoogleServicesHelper.LocationSettingsResult.Success ->
                Result.success(Unit)

            IONGLOCGoogleServicesHelper.LocationSettingsResult.Resolving ->
                resolveLocationSettingsResultFlow.first()

            is IONGLOCGoogleServicesHelper.LocationSettingsResult.ResolveSkipped ->
                Result.failure(locationSettingsResult.resolvableError)

            is IONGLOCGoogleServicesHelper.LocationSettingsResult.UnresolvableError ->
                Result.failure(locationSettingsResult.error)
        }
    }

    /**
     * Clears a watch by removing its location update request
     * @param id the watch id
     * @param addToBlackList whether or not the watch id should go in blacklist if not found
     * @return true if watch was cleared, false if watch was not found
     */
    private fun clearWatch(id: String, addToBlackList: Boolean): Boolean {
        val watchHandler = watchLocationHandlers.remove(key = id)
        return if (watchHandler != null) {
            if (watchHandler is LocationHandler.Callback) {
                googleServicesHelper.removeLocationUpdates(watchHandler.callback)
            } else if (watchHandler is LocationHandler.Listener) {
                fallbackHelper.removeLocationUpdates(watchHandler.listener)
            }
            true
        } else {
            if (addToBlackList) {
                // It is possible that clearWatch is being called before requestLocationUpdates is triggered (e.g. very low timeout on JavaScript side.)
                //  add to a blacklist in order to remove the location callback in the future
                watchIdsBlacklist.add(id)
            }
            false
        }
    }

    /**
     * Checks if the current watch is in the blacklist
     *
     * If the watch is in the blacklist, location updates for that watch should be removed.
     * @param watchId the unique id of the watch
     * @return true if watch is in blacklist, false otherwise
     */
    private fun checkWatchInBlackList(watchId: String): Boolean {
        if (watchIdsBlacklist.contains(watchId)) {
            val cleared = clearWatch(watchId, addToBlackList = false)
            if (cleared) {
                watchIdsBlacklist.remove(watchId)
            }
            return true
        }
        return false
    }

    /**
     * Extension function to convert Location object into OSLocationResult object
     * @return OSLocationResult object
     */
    private fun Location.toOSLocationResult(): IONGLOCLocationResult = IONGLOCLocationResult(
        latitude = this.latitude,
        longitude = this.longitude,
        altitude = this.altitude,
        accuracy = this.accuracy,
        altitudeAccuracy = if (IONGLOCBuildConfig.getAndroidSdkVersionCode() >= Build.VERSION_CODES.O) this.verticalAccuracyMeters else null,
        heading = this.bearing,
        speed = this.speed,
        timestamp = this.time
    )

    sealed interface LocationHandler {
        data class Callback(val callback: LocationCallback) : LocationHandler
        data class Listener(val listener: LocationListenerCompat) : LocationHandler
    }

    companion object {
        private const val LOG_TAG = "IONGeolocationController"
    }

}