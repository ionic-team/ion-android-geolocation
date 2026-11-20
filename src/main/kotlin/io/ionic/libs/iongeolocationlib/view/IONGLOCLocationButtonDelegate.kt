package io.ionic.libs.iongeolocationlib.view

import android.app.Activity
import io.ionic.libs.iongeolocationlib.controller.IONGLOCController
import io.ionic.libs.iongeolocationlib.model.IONGLOCLocationOptions
import io.ionic.libs.iongeolocationlib.model.IONGLOCLocationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Non-View state and logic of the LocationButton island: permission result handling,
 * `fetchPosition()` delegation, lifecycle-aware cancellation, and event emission.
 * The island owns one instance and wires the widget's callbacks to it.
 */
internal class IONGLOCLocationButtonDelegate(
    private val activity: Activity,
    private val controller: IONGLOCController,
    private val errorCodeMapper: ((Throwable) -> String?)? = null,
    private val positionMapper: ((IONGLOCLocationResult) -> Map<String, Any?>)? = null,
    private val eventSink: (name: String, payload: Map<String, Any?>) -> Unit,
) {
    private companion object {
        /** Used when the host plugin's error code mapper has no code for an error. */
        const val DEFAULT_ERROR_CODE = "OS-PLUG-GLOC-0002"
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var resumed = false
    private var destroyed = false
    private var pendingPermissionResult: Boolean? = null
    private var pendingFetchResult: Result<IONGLOCLocationResult>? = null
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
        pendingFetchResult?.let { result ->
            pendingFetchResult = null
            deliverFetchResult(result)
        }
    }

    fun onPause() {
        resumed = false
    }

    fun onDestroy() {
        destroyed = true
        resumed = false
        cancelOutstandingWork()
        coroutineScope.cancel()
    }

    /** Entry point for the permission result reported by the widget (system or library dialog). */
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
        emit("buttonError", mapOf("reason" to reason, "code" to (code ?: DEFAULT_ERROR_CODE)))
    }

    /** Reports an error of the button itself, with the host plugin's code for it when it has one. */
    fun emitButtonError(error: Throwable) {
        emitError(error.message ?: "location button session failed", errorCodeMapper?.invoke(error))
    }

    private fun deliverPermissionResult(granted: Boolean) {
        emit("grant", mapOf("granted" to granted))
        if (granted) fetchPosition()
    }

    private fun fetchPosition() {
        if (!resumed || destroyed || currentFetchJob?.isActive == true) return
        val job = coroutineScope.launch(start = CoroutineStart.LAZY) {
            val options = IONGLOCLocationOptions(
                timeout = timeout,
                maximumAge = maximumAge,
                enableHighAccuracy = true, // fixed: a precise-location button should not fetch imprecisely
                enableLocationManagerFallback = enableLocationManagerFallback,
            )
            val result = controller.getCurrentPosition(activity, options)
            if (destroyed) return@launch
            if (resumed) deliverFetchResult(result) else pendingFetchResult = result
        }
        currentFetchJob = job
        job.invokeOnCompletion { if (currentFetchJob === job) currentFetchJob = null }
        job.start()
    }

    private fun deliverFetchResult(result: Result<IONGLOCLocationResult>) {
        result.fold(
            onSuccess = { emitPosition(it) },
            onFailure = { emitError(it.message ?: "location fetch failed", errorCodeMapper?.invoke(it)) },
        )
    }

    private fun cancelOutstandingWork() {
        pendingPermissionResult = null
        pendingFetchResult = null
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
