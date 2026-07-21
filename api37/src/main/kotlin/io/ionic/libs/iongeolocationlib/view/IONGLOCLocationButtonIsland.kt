package io.ionic.libs.iongeolocationlib.view

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.location.LocationManagerCompat
import androidx.core.locationbutton.LocationButton
import io.ionic.libs.ionnativeislandslib.NativeIsland
import io.ionic.libs.ionnativeislandslib.NativeIslandEventEmitting

class IONGLOCLocationButtonIsland(
    private val context: Context,
    private val activity: Activity,
) : NativeIsland, NativeIslandEventEmitting {

    companion object {
        @JvmStatic
        fun requiresUnobscuredSurface() = Build.VERSION.SDK_INT >= 37
    }

    override var eventSink: ((String, Map<String, Any?>) -> Unit)? = null

    private val density = context.resources.displayMetrics.density
    private var resumed = false
    private var destroyed = false
    private var permissionRequestPending = false
    private var permissionRequestGeneration = 0L
    private var pendingPermissionResult: Boolean? = null
    private var currentFetch: CancellationSignal? = null

    private var textType = "precise-location"
    private var backgroundColor = Color.rgb(11, 87, 208)
    private var textColor = Color.WHITE
    private var iconTint = Color.WHITE
    private var strokeColor = Color.BLACK
    private var cornerRadius = 22f * density
    private var pressedCornerRadius = 12f * density
    private var strokeWidth = 0f
    private var clickablePadding = 6f * density

    private val button = LocationButton(context).apply {
        id = View.generateViewId()
        parentActivity = activity
        minimumWidth = minimumTouchTarget
        minimumHeight = minimumTouchTarget
        setOnPermissionResultListener { granted ->
            receivePermissionResult(granted)
        }
        setOnRequestPermissionsListener {
            requestFallbackPermission()
        }
        setOnErrorListener { error ->
            emitError(error.message ?: "location button session failed")
        }
    }

    private val buttonHost = object : FrameLayout(context) {
        private var attachPending = false

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            attachButtonWhenReady()
        }

        override fun onVisibilityAggregated(isVisible: Boolean) {
            super.onVisibilityAggregated(isVisible)
            if (isVisible) attachButtonWhenReady()
        }

        override fun onSizeChanged(
            width: Int,
            height: Int,
            oldWidth: Int,
            oldHeight: Int,
        ) {
            super.onSizeChanged(width, height, oldWidth, oldHeight)
            attachButtonWhenReady()
        }

        private fun attachButtonWhenReady() {
            if (
                attachPending ||
                button.parent != null ||
                width < minimumTouchTarget ||
                height < minimumTouchTarget
            ) return

            attachPending = true
            postOnAnimation {
                attachPending = false
                if (
                    button.parent == null &&
                    isAttachedToWindow &&
                    isShown &&
                    width >= minimumTouchTarget &&
                    height >= minimumTouchTarget
                ) {
                    addView(
                        button,
                        LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
            }
        }
    }

    init {
        renderButton()
    }

    override val view: View get() = buttonHost

    override fun create(properties: Map<String, Any?>) = applyConfig(properties)

    override fun update(properties: Map<String, Any?>) = applyConfig(properties)

    override fun onResume() {
        if (destroyed) return
        resumed = true
        pendingPermissionResult?.let { granted ->
            pendingPermissionResult = null
            deliverPermissionResult(granted)
        }
    }

    override fun onPause() {
        resumed = false
        cancelLocationFetch()
    }

    override fun onDestroy() {
        destroyed = true
        resumed = false
        cancelOutstandingWork()
        button.setOnPermissionResultListener(null)
        button.setOnRequestPermissionsListener(null)
        button.setOnErrorListener(null)
        button.parentActivity = null
        buttonHost.removeAllViews()
    }

    private fun applyConfig(params: Map<String, Any?>) {
        params.string("textType")?.let {
            require(it in LOCATION_BUTTON_TEXT_TYPES) {
                "unsupported Location Button text type"
            }
            textType = it
        }
        params.color("backgroundColor")?.let { backgroundColor = it }
        params.color("textColor")?.let { textColor = it }
        params.color("iconTint")?.let { iconTint = it }
        params.color("strokeColor")?.let { strokeColor = it }
        params.dimension("cornerRadius", 0.0, 68.0)?.let { cornerRadius = it }
        params.dimension("pressedCornerRadius", 0.0, 68.0)?.let {
            pressedCornerRadius = it
        }
        params.dimension("strokeWidth", 0.0, 3.0)?.let { strokeWidth = it }
        params.dimension("clickablePadding", 4.0, 8.0)?.let {
            clickablePadding = it
        }
        renderButton()
    }

    private fun renderButton() {
        button.setTextType(LOCATION_BUTTON_TEXT_TYPES.getValue(textType))
        button.setBackgroundColor(backgroundColor)
        button.setTextColor(textColor)
        button.setIconTint(iconTint)
        button.setStrokeColor(strokeColor)
        button.setCornerRadius(cornerRadius)
        button.setPressedCornerRadius(pressedCornerRadius)
        button.setStrokeWidth(strokeWidth.toInt())

        val padding = clickablePadding.toInt()
        button.setPadding(padding, padding, padding, padding)
    }

    private fun requestFallbackPermission() {
        if (!resumed || destroyed || permissionRequestPending || currentFetch != null) return

        if (
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
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
                    val fineGranted =
                        preciseGranted &&
                            ContextCompat.checkSelfPermission(
                                activity,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ) == PackageManager.PERMISSION_GRANTED
                    receivePermissionResult(fineGranted)
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

    private fun receivePermissionResult(granted: Boolean) {
        activity.runOnUiThread {
            if (destroyed) return@runOnUiThread
            if (resumed) {
                deliverPermissionResult(granted)
            } else {
                pendingPermissionResult = granted
            }
        }
    }

    private fun deliverPermissionResult(granted: Boolean) {
        emit("grant", mapOf("granted" to granted))
        if (granted) fetchPosition()
    }

    @SuppressLint("MissingPermission")
    private fun fetchPosition() {
        if (!resumed || destroyed || currentFetch != null) return
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            emitError("location manager unavailable")
            return
        }
        val enabledProviders = manager.getProviders(true)
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull(enabledProviders::contains)
            ?: enabledProviders.firstOrNull()
        if (provider == null) {
            emitError("no enabled location provider")
            return
        }

        val cancellation = CancellationSignal()
        currentFetch = cancellation
        try {
            LocationManagerCompat.getCurrentLocation(
                manager,
                provider,
                cancellation,
                ContextCompat.getMainExecutor(context),
            ) { location: Location? ->
                if (
                    destroyed ||
                    !resumed ||
                    currentFetch !== cancellation ||
                    cancellation.isCanceled
                ) {
                    return@getCurrentLocation
                }
                currentFetch = null
                val fix = location ?: newestRecentLastKnownLocation(manager, enabledProviders)
                if (fix == null) {
                    emitError("no recent location fix")
                } else {
                    emitPosition(fix)
                }
            }
        } catch (error: Exception) {
            if (currentFetch === cancellation) currentFetch = null
            emitError(error.message ?: "location fetch failed")
        }
    }

    @SuppressLint("MissingPermission")
    private fun newestRecentLastKnownLocation(
        manager: LocationManager,
        providers: List<String>,
    ): Location? {
        val nowElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        return providers
            .mapNotNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (_: Exception) {
                    null
                }
            }
            .filter { isRecentLocationFix(it.elapsedRealtimeNanos, nowElapsedRealtimeNanos) }
            .maxByOrNull(Location::getElapsedRealtimeNanos)
    }

    private fun cancelOutstandingWork() {
        permissionRequestPending = false
        permissionRequestGeneration += 1
        pendingPermissionResult = null
        cancelLocationFetch()
    }

    private fun cancelLocationFetch() {
        currentFetch?.cancel()
        currentFetch = null
    }

    private fun emitPosition(location: Location) {
        emit(
            "position",
            mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "accuracy" to location.accuracy.toDouble(),
                "timestamp" to location.time,
            ),
        )
    }

    private fun emitError(reason: String) {
        emit("buttonError", mapOf("reason" to reason))
    }

    private fun emit(name: String, payload: Map<String, Any?>) {
        if (!destroyed) eventSink?.invoke(name, payload)
    }

    private fun Map<String, Any?>.string(name: String): String? {
        val value = this[name] ?: return null
        require(value is String) { "$name must be a string" }
        return value
    }

    private fun Map<String, Any?>.color(name: String): Int? {
        val value = string(name) ?: return null
        require(HEX_COLOR.matches(value)) { "$name must use #RRGGBB" }
        return value.toColorInt()
    }

    private fun Map<String, Any?>.dimension(
        name: String,
        minimum: Double,
        maximum: Double,
    ): Float? {
        val value = this[name] ?: return null
        require(value is Number) { "$name must be a number" }
        val cssPixels = value.toDouble()
        require(cssPixels.isFinite() && cssPixels in minimum..maximum) {
            "$name must be between $minimum and $maximum CSS pixels"
        }
        return (cssPixels * density).toFloat()
    }

    private val minimumTouchTarget: Int get() = (48f * density).toInt()
}

private val LOCATION_BUTTON_TEXT_TYPES = mapOf(
    "precise-location" to LocationButton.TEXT_TYPE_PRECISE_LOCATION,
    "use-precise-location" to LocationButton.TEXT_TYPE_USE_PRECISE_LOCATION,
    "share-precise-location" to LocationButton.TEXT_TYPE_SHARE_PRECISE_LOCATION,
    "near-my-precise-location" to LocationButton.TEXT_TYPE_NEAR_MY_PRECISE_LOCATION,
    "near-your-precise-location" to LocationButton.TEXT_TYPE_NEAR_YOUR_PRECISE_LOCATION,
    "none" to LocationButton.TEXT_TYPE_NONE,
)

private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")
