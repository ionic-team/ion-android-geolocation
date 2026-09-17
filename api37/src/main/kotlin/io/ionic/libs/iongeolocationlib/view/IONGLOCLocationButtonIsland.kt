package io.ionic.libs.iongeolocationlib.view

import android.content.Context
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.toColorInt
import androidx.core.locationbutton.LocationButton
import io.ionic.libs.iongeolocationlib.controller.IONGLOCController
import io.ionic.libs.iongeolocationlib.model.IONGLOCLocationResult
import io.ionic.libs.ionnativeislandslib.NativeIsland
import io.ionic.libs.ionnativeislandslib.NativeIslandEventEmitting

class IONGLOCLocationButtonIsland(
    private val context: Context,
    activity: Activity,
    controller: IONGLOCController,
    errorCodeMapper: ((Throwable) -> String?)? = null,
    positionMapper: ((IONGLOCLocationResult) -> Map<String, Any?>)? = null,
) : NativeIsland, NativeIslandEventEmitting {

    companion object {
        @JvmStatic
        fun requiresUnobscuredSurface() = Build.VERSION.SDK_INT >= 37
    }

    override var eventSink: ((String, Map<String, Any?>) -> Unit)? = null

    private val density = context.resources.displayMetrics.density
    private val delegate = IONGLOCLocationButtonDelegate(
        activity,
        controller,
        errorCodeMapper,
        positionMapper,
    ) { name, payload ->
        eventSink?.invoke(name, payload)
    }

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
            delegate.permissionGranted(granted)
        }
        setOnRequestPermissionsListener {
            delegate.requestPreciseLocation()
        }
        setOnErrorListener { error ->
            delegate.emitError(error.message ?: "location button session failed")
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

    override fun onResume() = delegate.onResume()

    override fun onPause() = delegate.onPause()

    override fun onDestroy() {
        delegate.onDestroy()
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
        params.long("timeout", minimum = 1)?.let { delegate.timeout = it }
        params.long("maximumAge", minimum = 0)?.let { delegate.maximumAge = it }
        params.boolean("enableLocationFallback")?.let { delegate.enableLocationManagerFallback = it }
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

internal fun Map<String, Any?>.long(name: String, minimum: Long): Long? {
    val value = this[name] as? Number ?: return null
    val long = value.toLong()
    return if (long >= minimum) long else null
}

internal fun Map<String, Any?>.boolean(name: String): Boolean? = this[name] as? Boolean
