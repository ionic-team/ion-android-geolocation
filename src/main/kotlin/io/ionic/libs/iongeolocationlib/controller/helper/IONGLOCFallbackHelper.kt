package io.ionic.libs.iongeolocationlib.controller.helper

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import io.ionic.libs.iongeolocationlib.model.IONGLOCException
import io.ionic.libs.iongeolocationlib.model.IONGLOCLocationOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Helper class that wraps the functionality of Android's [LocationManager].
 * Meant to be used only as a fallback in case we cannot used the Fused Location Provider from Play Services.
 */
internal class IONGLOCFallbackHelper(
    private val locationManager: LocationManager
) {
    /**
     * Obtains a fresh device location.
     * This fallback method does not receive options because Android API does not allow to configure specific options.
     * @return Location object representing the location
     */
    @SuppressLint("MissingPermission")
    internal suspend fun getCurrentLocation(): Location =
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal: CancellationSignal? = null
            LocationManagerCompat.getCurrentLocation(
                locationManager,
                LocationManager.GPS_PROVIDER,
                cancellationSignal,
                Executors.newSingleThreadExecutor()
            ) { location ->
                if (location != null) {
                    continuation.resume(location)
                } else {
                    continuation.resumeWithException(
                        IONGLOCException.IONGLOCLocationRetrievalTimeoutException(
                            message = "Location request timed out"
                        )
                    )
                }
            }
        }

    /**
     * Requests updates of device location.
     *
     * Locations returned in callback associated with watchId
     * @param options location request options to use
     * @param locationListener the [LocationListenerCompat] to receive location updates in
     */
    @SuppressLint("MissingPermission")
    internal fun requestLocationUpdates(
        options: IONGLOCLocationOptions,
        locationListener: LocationListenerCompat
    ) {
        val locationRequest = LocationRequestCompat.Builder(options.timeout).apply {
            // note: setMaxUpdateAgeMillis unavailable in this API, so options.maximumAge is not used
            setQuality(if (options.enableHighAccuracy) LocationRequestCompat.QUALITY_HIGH_ACCURACY else LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
            if (options.minUpdateInterval != null) {
                setMinUpdateIntervalMillis(options.minUpdateInterval)
            }
        }.build()

        LocationManagerCompat.requestLocationUpdates(
            locationManager,
            LocationManager.GPS_PROVIDER,
            locationRequest,
            locationListener,
            Looper.getMainLooper()
        )
    }

    /**
     * Remove location updates for a specific listener.
     *
     * This method only triggers the removal, it does not await to see if the listener was actually removed.
     *
     * @param locationListener the location listener to be removed
     */
    internal fun removeLocationUpdates(
        locationListener: LocationListenerCompat
    ) {
        LocationManagerCompat.removeUpdates(locationManager, locationListener)
    }
}