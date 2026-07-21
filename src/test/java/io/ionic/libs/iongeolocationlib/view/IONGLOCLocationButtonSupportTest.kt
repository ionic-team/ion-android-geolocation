package io.ionic.libs.iongeolocationlib.view

import android.app.Activity
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class IONGLOCLocationButtonSupportTest {
    private val activity = mockk<Activity>()

    @After
    fun tearDown() {
        IONGLOCLocationButtonRegistry.unregister(activity)
    }

    @Test
    fun `last-known fix accepts the explicit age boundary`() {
        val now = 500_000_000_000L
        val maximumAgeNanos = MAX_LAST_KNOWN_LOCATION_AGE_MILLIS * 1_000_000L

        assertTrue(isRecentLocationFix(now, now))
        assertTrue(isRecentLocationFix(now - maximumAgeNanos, now))
        assertFalse(isRecentLocationFix(now - maximumAgeNanos - 1L, now))
    }

    @Test
    fun `last-known fix rejects invalid and future timestamps`() {
        assertFalse(isRecentLocationFix(0L, 10L))
        assertFalse(isRecentLocationFix(11L, 10L))
    }

    @Test
    fun `permission requester is scoped to its Activity`() {
        val otherActivity = mockk<Activity>()
        val requester = IONGLOCLocationButtonPermissionRequester { }

        IONGLOCLocationButtonRegistry.register(activity, requester)

        assertSame(requester, IONGLOCLocationButtonRegistry.permissionRequester(activity))
        assertNull(IONGLOCLocationButtonRegistry.permissionRequester(otherActivity))
    }

    @Test
    fun `unregister removes the Activity permission requester`() {
        val requester = IONGLOCLocationButtonPermissionRequester { }
        IONGLOCLocationButtonRegistry.register(activity, requester)

        IONGLOCLocationButtonRegistry.unregister(activity)

        assertNull(IONGLOCLocationButtonRegistry.permissionRequester(activity))
    }
}
