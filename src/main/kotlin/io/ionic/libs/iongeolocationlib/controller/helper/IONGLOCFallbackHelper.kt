package io.ionic.libs.iongeolocationlib.controller.helper

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import io.ionic.libs.iongeolocationlib.model.IONGLOCException
import io.ionic.libs.iongeolocationlib.model.IONGLOCLocationOptions
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Helper class that wraps the functionality of Android's [LocationManager].
 * Meant to be used only as a fallback in case we cannot used the Fused Location Provider from Play Services.
 */
internal class IONGLOCFallbackHelper(
    private val locationManager: LocationManager
) {
    /**
     * Obtains a fresh device location.
     * @param options location request options to use
     * @return Location object representing the location
     */
    @SuppressLint("MissingPermission")
    internal suspend fun getCurrentLocation(options: IONGLOCLocationOptions): Location = try {
        withTimeout(options.timeout) {
            suspendCancellableCoroutine { continuation ->
                val cachedLocation =
                    locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (cachedLocation != null && (System.currentTimeMillis() - cachedLocation.time) < options.maximumAge) {
                    continuation.resume(cachedLocation)
                    return@suspendCancellableCoroutine
                }

                // cached location inexistent or too old - must make a fresh location request
                val locationRequest = LocationRequestCompat.Builder(0).apply {
                    setQuality(if (options.enableHighAccuracy) LocationRequestCompat.QUALITY_HIGH_ACCURACY else LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
                }.build()
                var locationListener: LocationListenerCompat? = null
                locationListener = LocationListenerCompat { location ->
                    locationListener?.let {
                        // remove listener to only allow one location update
                        removeLocationUpdates(it)
                        locationListener = null
                    }
                    continuation.resume(location)
                }
                locationListener?.let {
                    LocationManagerCompat.requestLocationUpdates(
                        locationManager,
                        LocationManager.GPS_PROVIDER,
                        locationRequest,
                        it,
                        Looper.getMainLooper()
                    )
                }

                // If coroutine is cancelled (due to timeout or external cancel), remove listener
                continuation.invokeOnCancellation {
                    locationListener?.let {
                        removeLocationUpdates(it)
                        locationListener = null
                    }
                }
            }
        }
    } catch (e: TimeoutCancellationException) {
        throw IONGLOCException.IONGLOCLocationRetrievalTimeoutException(
            message = "Location request timed out",
            cause = e
        )
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
    @SuppressLint("MissingPermission")
    internal fun removeLocationUpdates(
        locationListener: LocationListenerCompat
    ) {
        LocationManagerCompat.removeUpdates(locationManager, locationListener)
    }
}
