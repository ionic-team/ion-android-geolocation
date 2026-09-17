package io.ionic.libs.iongeolocationlib.view

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.ionic.libs.iongeolocationlib.controller.IONGLOCController
import io.ionic.libs.iongeolocationlib.model.IONGLOCException
import io.ionic.libs.iongeolocationlib.model.IONGLOCLocationOptions
import io.ionic.libs.iongeolocationlib.model.IONGLOCLocationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IONGLOCLocationButtonDelegateTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)

    private val activity = mockk<Activity>()
    private val controller = mockk<IONGLOCController>()
    private val permissionRequester = mockk<IONGLOCLocationButtonPermissionRequester>()
    private val events = mutableListOf<Pair<String, Map<String, Any?>>>()

    private lateinit var delegate: IONGLOCLocationButtonDelegate

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(ContextCompat::class)
        mockkObject(IONGLOCLocationButtonRegistry)
        every { activity.runOnUiThread(any()) } answers { firstArg<Runnable>().run() }
        every { IONGLOCLocationButtonRegistry.permissionRequester(activity) } returns permissionRequester

        delegate = createDelegate()
        delegate.onResume()
    }

    private fun createDelegate(
        errorCodeMapper: ((Throwable) -> String?)? = null,
        positionMapper: ((IONGLOCLocationResult) -> Map<String, Any?>)? = null,
    ): IONGLOCLocationButtonDelegate =
        IONGLOCLocationButtonDelegate(activity, controller, errorCodeMapper, positionMapper) { name, payload ->
            events += name to payload
        }

    @After
    fun tearDown() {
        unmockkObject(IONGLOCLocationButtonRegistry)
        unmockkStatic(ContextCompat::class)
        Dispatchers.resetMain()
    }

    private fun grantedPermission() {
        every {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
    }

    private fun deniedPermission() {
        every {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_DENIED
    }

    private fun sampleResult() = IONGLOCLocationResult(
        latitude = 1.0,
        longitude = 2.0,
        altitude = 3.0,
        accuracy = 4f,
        altitudeAccuracy = 5f,
        heading = 6f,
        speed = 7f,
        timestamp = 8L,
        magneticHeading = 9f,
        trueHeading = 10f,
        headingAccuracy = 11f,
        course = 12f,
    )

    // region getCurrentPosition() options

    @Test
    fun `given permission already granted, when requestPreciseLocation is called, getCurrentPosition receives the configured options`() {
        grantedPermission()
        coEvery { controller.getCurrentPosition(any(), any()) } returns Result.success(sampleResult())
        delegate.timeout = 5_000L
        delegate.maximumAge = 60_000L
        delegate.enableLocationManagerFallback = false

        delegate.requestPreciseLocation()
        testScheduler.advanceUntilIdle()

        coVerify {
            controller.getCurrentPosition(
                activity,
                IONGLOCLocationOptions(
                    timeout = 5_000L,
                    maximumAge = 60_000L,
                    enableHighAccuracy = true,
                    enableLocationManagerFallback = false,
                ),
            )
        }
    }

    // endregion

    // region success/failure -> event mapping

    @Test
    fun `given no position mapper is supplied, when the fetch succeeds, position event carries the flat default shape`() {
        grantedPermission()
        val result = sampleResult()
        coEvery { controller.getCurrentPosition(any(), any()) } returns Result.success(result)

        delegate.requestPreciseLocation()
        testScheduler.advanceUntilIdle()

        assertEquals("grant" to mapOf("granted" to true), events[0])
        assertEquals(
            "position" to mapOf(
                "latitude" to result.latitude,
                "longitude" to result.longitude,
                "altitude" to result.altitude,
                "accuracy" to result.accuracy.toDouble(),
                "altitudeAccuracy" to result.altitudeAccuracy,
                "heading" to result.heading,
                "speed" to result.speed,
                "timestamp" to result.timestamp,
                "magneticHeading" to result.magneticHeading,
                "trueHeading" to result.trueHeading,
                "headingAccuracy" to result.headingAccuracy,
                "course" to result.course,
            ),
            events[1],
        )
    }

    @Test
    fun `given a position mapper is supplied, when the fetch succeeds, position event carries the mapper's shape`() {
        delegate = createDelegate(
            positionMapper = { location ->
                mapOf("timestamp" to location.timestamp, "coords" to mapOf("latitude" to location.latitude))
            },
        )
        delegate.onResume()
        grantedPermission()
        val result = sampleResult()
        coEvery { controller.getCurrentPosition(any(), any()) } returns Result.success(result)

        delegate.requestPreciseLocation()
        testScheduler.advanceUntilIdle()

        assertEquals("grant" to mapOf("granted" to true), events[0])
        assertEquals(
            "position" to mapOf("timestamp" to result.timestamp, "coords" to mapOf("latitude" to result.latitude)),
            events[1],
        )
    }

    @Test
    fun `given no error code mapper is supplied, when the fetch fails, buttonError carries a null code`() {
        grantedPermission()
        coEvery { controller.getCurrentPosition(any(), any()) } returns
            Result.failure(IONGLOCException.IONGLOCLocationRetrievalTimeoutException("timed out"))

        delegate.requestPreciseLocation()
        testScheduler.advanceUntilIdle()

        assertEquals("grant" to mapOf("granted" to true), events[0])
        assertEquals(
            "buttonError" to mapOf<String, Any?>("reason" to "timed out", "code" to null),
            events[1],
        )
    }

    @Test
    fun `given an error code mapper is supplied, when the fetch fails, buttonError carries the mapper's code`() {
        delegate = createDelegate(errorCodeMapper = { exception ->
            (exception as? IONGLOCException.IONGLOCLocationRetrievalTimeoutException)?.let { "OS-PLUG-GLOC-0010" }
        })
        delegate.onResume()
        grantedPermission()
        coEvery { controller.getCurrentPosition(any(), any()) } returns
            Result.failure(IONGLOCException.IONGLOCLocationRetrievalTimeoutException("timed out"))

        delegate.requestPreciseLocation()
        testScheduler.advanceUntilIdle()

        assertEquals("grant" to mapOf("granted" to true), events[0])
        assertEquals(
            "buttonError" to mapOf("reason" to "timed out", "code" to "OS-PLUG-GLOC-0010"),
            events[1],
        )
    }

    @Test
    fun `given an error code mapper is supplied but returns null for this exception, buttonError carries a null code`() {
        delegate = createDelegate(errorCodeMapper = { null })
        delegate.onResume()
        grantedPermission()
        coEvery { controller.getCurrentPosition(any(), any()) } returns
            Result.failure(IONGLOCException.IONGLOCLocationRetrievalTimeoutException("timed out"))

        delegate.requestPreciseLocation()
        testScheduler.advanceUntilIdle()

        assertEquals(
            "buttonError" to mapOf<String, Any?>("reason" to "timed out", "code" to null),
            events[1],
        )
    }

    @Test
    fun `given permission is denied by the requester, grant(false) is emitted and no fetch happens`() {
        deniedPermission()
        every { permissionRequester.requestPreciseLocation(any()) } answers {
            firstArg<(Boolean) -> Unit>().invoke(false)
        }

        delegate.requestPreciseLocation()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("grant" to mapOf<String, Any?>("granted" to false)), events)
        coVerify(exactly = 0) { controller.getCurrentPosition(any(), any()) }
    }

    @Test
    fun `given permission is not yet granted, when the requester eventually grants it, grant and position events are emitted`() {
        every {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        } returnsMany listOf(PackageManager.PERMISSION_DENIED, PackageManager.PERMISSION_GRANTED)
        every { permissionRequester.requestPreciseLocation(any()) } answers {
            firstArg<(Boolean) -> Unit>().invoke(true)
        }
        coEvery { controller.getCurrentPosition(any(), any()) } returns Result.success(sampleResult())

        delegate.requestPreciseLocation()
        testScheduler.advanceUntilIdle()

        assertEquals("grant" to mapOf("granted" to true), events[0])
    }

    @Test
    fun `given no permission requester is registered, buttonError is emitted`() {
        deniedPermission()
        every { IONGLOCLocationButtonRegistry.permissionRequester(activity) } returns null

        delegate.requestPreciseLocation()

        assertEquals(
            listOf(
                "buttonError" to mapOf<String, Any?>(
                    "reason" to "precise location permission requester unavailable",
                    "code" to null,
                ),
            ),
            events,
        )
    }

    @Test
    fun `given the requester throws, buttonError is emitted`() {
        deniedPermission()
        every { permissionRequester.requestPreciseLocation(any()) } throws RuntimeException("boom")

        delegate.requestPreciseLocation()

        assertEquals(
            listOf("buttonError" to mapOf<String, Any?>("reason" to "boom", "code" to null)),
            events,
        )
    }

    // endregion

    // region re-entrancy guard

    @Test
    fun `given a permission request is already pending, a second requestPreciseLocation call does not invoke the requester again`() {
        deniedPermission()
        every { permissionRequester.requestPreciseLocation(any()) } just runs // never resolves -> stays pending

        delegate.requestPreciseLocation()
        delegate.requestPreciseLocation()

        verify(exactly = 1) { permissionRequester.requestPreciseLocation(any()) }
    }

    @Test
    fun `given a fetch is already in flight, a second requestPreciseLocation call is a no-op`() {
        grantedPermission()
        coEvery { controller.getCurrentPosition(any(), any()) } coAnswers {
            delay(10_000)
            Result.success(sampleResult())
        }

        delegate.requestPreciseLocation()
        testScheduler.runCurrent()

        delegate.requestPreciseLocation()

        verify(exactly = 1) { ContextCompat.checkSelfPermission(any(), any()) }
    }

    // endregion

    // region lifecycle cancellation

    @Test
    fun `given a fetch is suspended mid-flight, onPause cancels it and no position event is ever emitted`() {
        grantedPermission()
        coEvery { controller.getCurrentPosition(any(), any()) } coAnswers {
            delay(10_000)
            Result.success(sampleResult())
        }

        delegate.requestPreciseLocation()
        testScheduler.runCurrent() // reach the suspension point inside the launched coroutine

        delegate.onPause()
        testScheduler.advanceUntilIdle() // let virtual time pass the delay if it were still alive

        assertTrue(events.none { it.first == "position" })
    }

    @Test
    fun `given a fetch is suspended mid-flight, onDestroy cancels it and a later onResume does not revive it`() {
        grantedPermission()
        coEvery { controller.getCurrentPosition(any(), any()) } coAnswers {
            delay(10_000)
            Result.success(sampleResult())
        }

        delegate.requestPreciseLocation()
        testScheduler.runCurrent()

        delegate.onDestroy()
        testScheduler.advanceUntilIdle()
        delegate.onResume()

        assertTrue(events.none { it.first == "position" })
    }

    @Test
    fun `given onDestroy has already been called, requestPreciseLocation is a no-op`() {
        delegate.onDestroy()

        delegate.requestPreciseLocation()

        assertTrue(events.isEmpty())
        verify(exactly = 0) { permissionRequester.requestPreciseLocation(any()) }
    }

    // endregion

    // region permissionGranted() external entry point

    @Test
    fun `given the island is resumed, permissionGranted delivers immediately`() {
        coEvery { controller.getCurrentPosition(any(), any()) } returns Result.success(sampleResult())

        delegate.permissionGranted(true)
        testScheduler.advanceUntilIdle()

        assertEquals("grant" to mapOf("granted" to true), events[0])
    }

    @Test
    fun `given the island is paused, permissionGranted defers delivery to the next onResume`() {
        delegate.onPause()
        coEvery { controller.getCurrentPosition(any(), any()) } returns Result.success(sampleResult())

        delegate.permissionGranted(true)
        testScheduler.advanceUntilIdle()

        assertTrue(events.isEmpty())

        delegate.onResume()
        testScheduler.advanceUntilIdle()

        assertEquals("grant" to mapOf("granted" to true), events[0])
    }

    // endregion
}
