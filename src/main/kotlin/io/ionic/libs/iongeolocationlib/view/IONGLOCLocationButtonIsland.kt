package io.ionic.libs.iongeolocationlib.view

import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.core.locationbutton.compose.LocationButton
import androidx.core.locationbutton.compose.LocationButtonTextType
import io.ionic.libs.iongeolocationlib.controller.IONGLOCController
import io.ionic.libs.iongeolocationlib.model.IONGLOCException
import io.ionic.libs.iongeolocationlib.model.IONGLOCLocationResult
import io.ionic.libs.ionnativeislandslib.NativeIsland
import io.ionic.libs.ionnativeislandslib.NativeIslandEventEmitting
import io.ionic.libs.ionnativeislandslib.NativeIslandSurfaceClip

class IONGLOCLocationButtonIsland(
    private val activity: Activity,
    controller: IONGLOCController,
    errorCodeMapper: ((Throwable) -> String?)? = null,
    positionMapper: ((IONGLOCLocationResult) -> Map<String, Any?>)? = null,
) : NativeIsland, NativeIslandEventEmitting, NativeIslandSurfaceClip {

    companion object {
        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.CINNAMON_BUN)
        @JvmStatic
        fun requiresUnobscuredSurface() = Build.VERSION.SDK_INT >= 37
    }

    override var eventSink: ((String, Map<String, Any?>) -> Unit)? = null

    private val delegate = IONGLOCLocationButtonDelegate(
        activity,
        controller,
        errorCodeMapper,
        positionMapper,
    ) { name, payload ->
        eventSink?.invoke(name, payload)
    }

    // Sizes are in dp, which equals CSS px: the runtime lays the host out from CSS px.
    private data class ButtonConfig(
        val textType: String = "precise-location",
        val backgroundColor: Int = Color.rgb(11, 87, 208),
        val textColor: Int = Color.WHITE,
        val iconTint: Int = Color.WHITE,
        val strokeColor: Int = Color.BLACK,
        val cornerRadius: Float = 16f,
        val strokeWidth: Float = 0f,
    )

    private var config by mutableStateOf(ButtonConfig())
    private var visibleRect: Rect? = null
    private var clipObserver: ViewTreeObserver.OnGlobalLayoutListener? = null

    private val button = ComposeView(activity).apply {
        setContent {
            val current = config
            LocationButton(
                modifier = Modifier.fillMaxSize(),
                backgroundColor = ComposeColor(current.backgroundColor),
                strokeColor = ComposeColor(current.strokeColor),
                strokeWidth = current.strokeWidth.dp,
                cornerRadius = current.cornerRadius.dp,
                pressedCornerRadius = current.cornerRadius.dp,
                iconTint = ComposeColor(current.iconTint),
                textType = LOCATION_BUTTON_TEXT_TYPES.getValue(current.textType),
                textColor = ComposeColor(current.textColor),
                onError = { error ->
                    delegate.emitButtonError(
                        IONGLOCException.IONGLOCLocationButtonException(
                            error.message ?: "location button session failed",
                            error,
                        ),
                    )
                },
                onPermissionResult = { granted -> delegate.permissionGranted(granted) },
            )
        }
    }

    private val buttonHost = object : FrameLayout(activity) {
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
                    // The SurfaceView is created by the composition, after the first layout.
                    clipObserver = ViewTreeObserver.OnGlobalLayoutListener { applySurfaceClip() }
                        .also { button.viewTreeObserver.addOnGlobalLayoutListener(it) }
                    applySurfaceClip()
                }
            }
        }
    }

    override val view: View get() = buttonHost

    override fun setVisibleRect(rect: Rect?) {
        if (visibleRect == rect) return
        visibleRect = rect?.let(::Rect)
        applySurfaceClip()
    }

    private fun applySurfaceClip() {
        if (button.clipBounds != visibleRect) button.clipBounds = visibleRect
        val surface = button.findSurfaceView()
        if (surface != null && surface.clipBounds != visibleRect) surface.clipBounds = visibleRect
    }

    override fun create(properties: Map<String, Any?>) = applyConfig(properties)

    override fun update(properties: Map<String, Any?>) = applyConfig(properties)

    override fun onResume() = delegate.onResume()

    override fun onPause() = delegate.onPause()

    override fun onDestroy() {
        delegate.onDestroy()
        clipObserver?.let { observer ->
            if (button.viewTreeObserver.isAlive) button.viewTreeObserver.removeOnGlobalLayoutListener(observer)
        }
        clipObserver = null
        button.disposeComposition()
        buttonHost.removeAllViews()
    }

    private fun applyConfig(params: Map<String, Any?>) {
        // Parse and validate every property first (each throws on an invalid value), then apply them together,
        // so an invalid update changes nothing.
        val textType = params.string("textType")?.also {
            require(it in LOCATION_BUTTON_TEXT_TYPES) { "unsupported Location Button text type" }
        }
        val newConfig = config.copy(
            textType = textType ?: config.textType,
            backgroundColor = params.color("backgroundColor") ?: config.backgroundColor,
            textColor = params.color("textColor") ?: config.textColor,
            iconTint = params.color("iconTint") ?: config.iconTint,
            strokeColor = params.color("strokeColor") ?: config.strokeColor,
            cornerRadius = params.dimension("cornerRadius", 0.0, 68.0) ?: config.cornerRadius,
            strokeWidth = params.dimension("strokeWidth", 0.0, 3.0) ?: config.strokeWidth,
        )
        val timeout = params.long("timeout", minimum = 1)
        val maximumAge = params.long("maximumAge", minimum = 0)
        val enableLocationFallback = params.boolean("enableLocationFallback")

        config = newConfig
        timeout?.let { delegate.timeout = it }
        maximumAge?.let { delegate.maximumAge = it }
        enableLocationFallback?.let { delegate.enableLocationManagerFallback = it }
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
        return cssPixels.toFloat()
    }

    private val minimumTouchTarget: Int
        get() = (48f * button.resources.displayMetrics.density).toInt()

    private fun View.findSurfaceView(): SurfaceView? {
        if (this is SurfaceView) return this
        if (this is ViewGroup) {
            for (i in 0 until childCount) getChildAt(i).findSurfaceView()?.let { return it }
        }
        return null
    }
}

private val LOCATION_BUTTON_TEXT_TYPES = mapOf(
    "precise-location" to LocationButtonTextType.PreciseLocation,
    "use-precise-location" to LocationButtonTextType.UsePreciseLocation,
    "share-precise-location" to LocationButtonTextType.SharePreciseLocation,
    "near-my-precise-location" to LocationButtonTextType.NearMyPreciseLocation,
    "near-your-precise-location" to LocationButtonTextType.NearYourPreciseLocation,
    "none" to LocationButtonTextType.None,
)

private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

internal fun Map<String, Any?>.long(name: String, minimum: Long): Long? {
    val value = this[name] ?: return null
    require(value is Number) { "$name must be a number" }
    val number = value.toDouble()
    require(number.isFinite() && number >= minimum) { "$name must be at least $minimum" }
    return value.toLong()
}

internal fun Map<String, Any?>.boolean(name: String): Boolean? {
    val value = this[name] ?: return null
    require(value is Boolean) { "$name must be a boolean" }
    return value
}
