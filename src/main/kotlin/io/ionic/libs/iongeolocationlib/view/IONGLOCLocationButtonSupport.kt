package io.ionic.libs.iongeolocationlib.view

internal const val MAX_LAST_KNOWN_LOCATION_AGE_MILLIS = 2 * 60 * 1000L

/**
 * Last-known fixes are only a short fallback for a current-location request.
 * Elapsed realtime is monotonic and is not affected by wall-clock changes.
 */
internal fun isRecentLocationFix(
    locationElapsedRealtimeNanos: Long,
    nowElapsedRealtimeNanos: Long,
): Boolean {
    if (locationElapsedRealtimeNanos <= 0L) return false
    val ageNanos = nowElapsedRealtimeNanos - locationElapsedRealtimeNanos
    return ageNanos >= 0L &&
        ageNanos <= MAX_LAST_KNOWN_LOCATION_AGE_MILLIS * 1_000_000L
}
