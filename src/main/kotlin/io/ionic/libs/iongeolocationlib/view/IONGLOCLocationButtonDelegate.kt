package io.ionic.libs.iongeolocationlib.view

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.ionic.libs.iongeolocationlib.controller.IONGLOCController
import io.ionic.libs.iongeolocationlib.model.IONGLOCLocationOptions
import io.ionic.libs.iongeolocationlib.model.IONGLOCLocationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Non-View state and logic shared by both LocationButton islands: permission requesting,
 * `fetchPosition()` delegation, lifecycle-aware cancellation, and event emission.
 * Each island owns one instance and wires its own View/widget callbacks to it.
 */
internal class IONGLOCLocationButtonDelegate(
    private val activity: Activity,
    private val controller: IONGLOCController,
    private val errorCodeMapper: ((Throwable) -> String?)? = null,
    private val positionMapper: ((IONGLOCLocationResult) -> Map<String, Any?>)? = null,
    private val eventSink: (name: String, payload: Map<String, Any?>) -> Unit,
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var resumed = false
    private var destroyed = false
    private var permissionRequestPending = false
    private var permissionRequestGeneration = 0L
    private var pendingPermissionResult: Boolean? = null
    private var currentFetchJob: Job? = null

    var timeout = 10_000L
    var maximumAge = 0L
    var enableLocationManagerFallback = true

    fun onResume() {
        if (destroyed) return
        resumed = true
        pendingPermissionResult?.let { granted ->
            pendingPermissionResult = null
            deliverPermissionResult(granted)
        }
    }

    fun onPause() {
        resumed = false
        cancelLocationFetch()
    }

    fun onDestroy() {
        destroyed = true
        resumed = false
        cancelOutstandingWork()
        coroutineScope.cancel()
    }

    /** Runs the manual precise-location permission request flow, and fetches a position if granted. */
    fun requestPreciseLocation() {
        if (!resumed || destroyed || permissionRequestPending || currentFetchJob != null) return

        if (hasFineLocationPermission()) {
            deliverPermissionResult(true)
            return
        }

        val requester = IONGLOCLocationButtonRegistry.permissionRequester(activity)
        if (requester == null) {
            emitError("precise location permission requester unavailable")
            return
        }

        permissionRequestPending = true
        val generation = ++permissionRequestGeneration
        try {
            requester.requestPreciseLocation { preciseGranted ->
                activity.runOnUiThread {
                    if (
                        destroyed ||
                        !permissionRequestPending ||
                        generation != permissionRequestGeneration
                    ) {
                        return@runOnUiThread
                    }
                    permissionRequestPending = false
                    permissionGranted(preciseGranted && hasFineLocationPermission())
                }
            }
        } catch (error: Exception) {
            if (
                permissionRequestPending &&
                generation == permissionRequestGeneration &&
                !destroyed
            ) {
                permissionRequestPending = false
                emitError(error.message ?: "precise location permission request failed")
            }
        }
    }

    /** Entry point for a grant result that happened elsewhere (api37's native widget dialog). */
    fun permissionGranted(granted: Boolean) {
        activity.runOnUiThread {
            if (destroyed) return@runOnUiThread
            if (resumed) {
                deliverPermissionResult(granted)
            } else {
                pendingPermissionResult = granted
            }
        }
    }

    fun emitError(reason: String, code: String? = null) {
        emit("buttonError", mapOf("reason" to reason, "code" to code))
    }

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    private fun deliverPermissionResult(granted: Boolean) {
        emit("grant", mapOf("granted" to granted))
        if (granted) fetchPosition()
    }

    private fun fetchPosition() {
        if (!resumed || destroyed || currentFetchJob != null) return
        currentFetchJob = coroutineScope.launch {
            val options = IONGLOCLocationOptions(
                timeout = timeout,
                maximumAge = maximumAge,
                enableHighAccuracy = true, // fixed — see Decisions
                enableLocationManagerFallback = enableLocationManagerFallback,
            )
            val result = controller.getCurrentPosition(activity, options)
            currentFetchJob = null
            if (destroyed || !resumed) return@launch
            result.fold(
                onSuccess = { emitPosition(it) },
                onFailure = { emitError(it.message ?: "location fetch failed", errorCodeMapper?.invoke(it)) },
            )
        }
    }

    private fun cancelOutstandingWork() {
        permissionRequestPending = false
        permissionRequestGeneration += 1
        pendingPermissionResult = null
        cancelLocationFetch()
    }

    private fun cancelLocationFetch() {
        currentFetchJob?.cancel()
        currentFetchJob = null
    }

    private fun emitPosition(location: IONGLOCLocationResult) {
        emit("position", positionMapper?.invoke(location) ?: defaultPositionPayload(location))
    }

    /** Flat shape used when no consumer supplies its own [positionMapper]. */
    private fun defaultPositionPayload(location: IONGLOCLocationResult): Map<String, Any?> = mapOf(
        "latitude" to location.latitude,
        "longitude" to location.longitude,
        "altitude" to location.altitude,
        "accuracy" to location.accuracy.toDouble(),
        "altitudeAccuracy" to location.altitudeAccuracy,
        "heading" to location.heading,
        "speed" to location.speed,
        "timestamp" to location.timestamp,
        "magneticHeading" to location.magneticHeading,
        "trueHeading" to location.trueHeading,
        "headingAccuracy" to location.headingAccuracy,
        "course" to location.course,
    )

    private fun emit(name: String, payload: Map<String, Any?>) {
        if (!destroyed) eventSink(name, payload)
    }
}
