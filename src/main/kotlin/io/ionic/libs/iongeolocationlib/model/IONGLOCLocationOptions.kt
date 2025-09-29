package io.ionic.libs.iongeolocationlib.model

/**
 * Data class representing the options passed to getCurrentPosition and watchPosition
 */
data class IONGLOCLocationOptions(
    val timeout: Long,
    val maximumAge: Long,
    val enableHighAccuracy: Boolean,
    val enableLocationManagerFallback: Boolean,
    val minUpdateInterval: Long? = null,
)
