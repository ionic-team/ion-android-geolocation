package io.ionic.libs.iongeolocationlib.view

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.core.graphics.drawable.DrawableCompat
import io.ionic.libs.iongeolocationlib.R
import io.ionic.libs.iongeolocationlib.controller.IONGLOCController
import io.ionic.libs.iongeolocationlib.model.IONGLOCLocationResult
import io.ionic.libs.ionnativeislandslib.NativeIsland
import io.ionic.libs.ionnativeislandslib.NativeIslandEventEmitting

/**
 * AppCompat implementation of `os.locationButton`. It requests precise
 * permission through its adapter and delegates location retrieval to
 * [IONGLOCController.getCurrentPosition].
 */
class IONGLOCLocationButtonIsland(
    private val context: Context,
    activity: Activity,
    controller: IONGLOCController,
    errorCodeMapper: ((Throwable) -> String?)? = null,
    positionMapper: ((IONGLOCLocationResult) -> Map<String, Any?>)? = null,
) : NativeIsland, NativeIslandEventEmitting {

    companion object {
        @JvmStatic
        fun requiresUnobscuredSurface() = false
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

    private val iconView = AppCompatImageView(context)
    private val labelView = AppCompatTextView(context).apply {
        gravity = Gravity.CENTER
        includeFontPadding = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val button = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        minimumWidth = minimumTouchTarget
        minimumHeight = minimumTouchTarget
        setOnClickListener { delegate.requestPreciseLocation() }
        addView(iconView, LinearLayout.LayoutParams(20f.dp, 20f.dp))
        addView(labelView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 20f.dp))
    }

    init {
        renderButton()
    }

    override val view: View get() = button

    override fun create(properties: Map<String, Any?>) = applyConfig(properties)

    override fun update(properties: Map<String, Any?>) = applyConfig(properties)

    override fun onResume() = delegate.onResume()

    override fun onPause() = delegate.onPause()

    override fun onDestroy() {
        delegate.onDestroy()
        button.setOnClickListener(null)
    }

    private fun applyConfig(params: Map<String, Any?>) {
        params.string("textType")?.let {
            require(it in LOCATION_BUTTON_TEXT) {
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
        val label = LOCATION_BUTTON_TEXT.getValue(textType)
        labelView.text = if (textType == "none") "" else label
        button.contentDescription = label
        labelView.setTextColor(textColor)

        val padding = clickablePadding.toInt()
        button.setPadding(padding, padding, padding, padding)
        button.backgroundTintList = null
        button.background = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                buttonBackground(
                    color = ColorUtils.blendARGB(backgroundColor, Color.BLACK, 0.12f),
                    radius = pressedCornerRadius,
                ),
            )
            addState(
                intArrayOf(),
                buttonBackground(backgroundColor, cornerRadius),
            )
        }

        val icon = requireNotNull(
            AppCompatResources.getDrawable(context, R.drawable.iongloc_ic_location),
        ).mutate()
        DrawableCompat.setTint(icon, iconTint)
        iconView.setImageDrawable(icon)
        labelView.layoutParams =
            (labelView.layoutParams as LinearLayout.LayoutParams).apply {
                marginStart = if (textType == "none") 0 else 8f.dp
            }
    }

    private fun buttonBackground(color: Int, radius: Float) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
            setStroke(strokeWidth.toInt(), strokeColor)
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
    private val Float.dp: Int get() = (this * density).toInt()
}

private val LOCATION_BUTTON_TEXT = mapOf(
    "precise-location" to "Precise location",
    "use-precise-location" to "Use precise location",
    "share-precise-location" to "Share precise location",
    "near-my-precise-location" to "Near my precise location",
    "near-your-precise-location" to "Near your precise location",
    "none" to "Share location",
)

private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

internal fun Map<String, Any?>.long(name: String, minimum: Long): Long? {
    val value = this[name] as? Number ?: return null
    val long = value.toLong()
    return if (long >= minimum) long else null
}

internal fun Map<String, Any?>.boolean(name: String): Boolean? = this[name] as? Boolean
