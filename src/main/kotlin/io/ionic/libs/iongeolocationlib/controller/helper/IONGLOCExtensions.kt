package io.ionic.libs.iongeolocationlib.controller.helper

import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.location.LocationManagerCompat
import io.ionic.libs.iongeolocationlib.model.IONGLOCException
import io.ionic.libs.iongeolocationlib.model.IONGLOCLocationResult

/**
 * @return true if there's any active network capability that could be used to improve location, false otherwise.
 */
internal fun hasNetworkEnabledForLocationPurposes(
    locationManager: LocationManager,
    connectivityManager: ConnectivityManager
) = LocationManagerCompat.hasProvider(locationManager, LocationManager.NETWORK_PROVIDER) &&
        connectivityManager.activeNetwork?.let { network ->
            connectivityManager.getNetworkCapabilities(network)?.let { capabilities ->
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                        (IONGLOCBuildConfig.getAndroidSdkVersionCode() >= Build.VERSION_CODES.O &&
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE))
            }
        } ?: false

/**
 * Returns a Result object containing an IONGLOCException.IONGLOCGoogleServicesException exception with the given
 * resolvable and message values
 * @param resolvable whether or not the exception is resolvable
 * @param message message to include in the exception
 * @return Result object with the exception to return
 *
 */
internal fun sendResultWithGoogleServicesException(
    resolvable: Boolean,
    message: String
): Result<Unit> {
    return Result.failure(
        IONGLOCException.IONGLOCGoogleServicesException(
            resolvable = resolvable,
            message = message
        )
    )
}

/**
 * Extension function to convert Location object into OSLocationResult object
 * @return OSLocationResult object
 */
internal fun Location.toOSLocationResult(): IONGLOCLocationResult = IONGLOCLocationResult(
    latitude = this.latitude,
    longitude = this.longitude,
    altitude = this.altitude,
    accuracy = this.accuracy,
    altitudeAccuracy = if (IONGLOCBuildConfig.getAndroidSdkVersionCode() >= Build.VERSION_CODES.O) this.verticalAccuracyMeters else null,
    heading = this.bearing,
    speed = this.speed,
    timestamp = this.time
)