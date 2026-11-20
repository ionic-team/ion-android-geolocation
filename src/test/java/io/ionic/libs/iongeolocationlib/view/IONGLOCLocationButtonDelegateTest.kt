package io.ionic.libs.iongeolocationlib.view

import android.app.Activity
import io.ionic.libs.iongeolocationlib.controller.IONGLOCController
import io.ionic.libs.iongeolocationlib.model.IONGLOCException
import io.ionic.libs.iongeolocationlib.model.IONGLOCLocationOptions
import io.ionic.libs.iongeolocationlib.model.IONGLOCLocationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private val events = mutableListOf<Pair<String, Map<String, Any?>>>()

    private lateinit var delegate: IONGLOCLocationButtonDelegate

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { activity.runOnUiThread(any()) } answers { firstArg<Runnable>().run() }

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
        Dispatchers.resetMain()
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
    fun `given permission is granted, getCurrentPosition receives the configured options`() {
        coEvery { controller.getCurrentPosition(any(), any()) } returns Result.success(sampleResult())
        delegate.timeout = 5_000L
        delegate.maximumAge = 60_000L
        delegate.enableLocationManagerFallback = false

        delegate.permissionGranted(true)
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
        val result = sampleResult()
        coEvery { controller.getCurrentPosition(any(), any()) } returns Result.success(result)

        delegate.permissionGranted(true)
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
        val result = sampleResult()
        coEvery { controller.getCurrentPosition(any(), any()) } returns Result.success(result)

        delegate.permissionGranted(true)
        testScheduler.advanceUntilIdle()

        assertEquals("grant" to mapOf("granted" to true), events[0])
        assertEquals(
            "position" to mapOf("timestamp" to result.timestamp, "coords" to mapOf("latitude" to result.latitude)),
            events[1],
        )
    }

    @Test
    fun `given no error code mapper is supplied, when the fetch fails, buttonError carries the default code`() {
        coEvery { controller.getCurrentPosition(any(), any()) } returns
            Result.failure(IONGLOCException.IONGLOCLocationRetrievalTimeoutException("timed out"))

        delegate.permissionGranted(true)
        testScheduler.advanceUntilIdle()

        assertEquals("grant" to mapOf("granted" to true), events[0])
        assertEquals(
            "buttonError" to mapOf("reason" to "timed out", "code" to "OS-PLUG-GLOC-0002"),
            events[1],
        )
    }

    @Test
    fun `given an error code mapper is supplied, when the fetch fails, buttonError carries the mapper's code`() {
        delegate = createDelegate(errorCodeMapper = { exception ->
            (exception as? IONGLOCException.IONGLOCLocationRetrievalTimeoutException)?.let { "OS-PLUG-GLOC-0010" }
        })
        delegate.onResume()
        coEvery { controller.getCurrentPosition(any(), any()) } returns
            Result.failure(IONGLOCException.IONGLOCLocationRetrievalTimeoutException("timed out"))

        delegate.permissionGranted(true)
        testScheduler.advanceUntilIdle()

        assertEquals("grant" to mapOf("granted" to true), events[0])
        assertEquals(
            "buttonError" to mapOf("reason" to "timed out", "code" to "OS-PLUG-GLOC-0010"),
            events[1],
        )
    }

    @Test
    fun `given an error code mapper is supplied but returns null for this exception, buttonError carries the default code`() {
        delegate = createDelegate(errorCodeMapper = { null })
        delegate.onResume()
        coEvery { controller.getCurrentPosition(any(), any()) } returns
            Result.failure(IONGLOCException.IONGLOCLocationRetrievalTimeoutException("timed out"))

        delegate.permissionGranted(true)
        testScheduler.advanceUntilIdle()

        assertEquals(
            "buttonError" to mapOf("reason" to "timed out", "code" to "OS-PLUG-GLOC-0002"),
            events[1],
        )
    }

    @Test
    fun `given the button reports an error, buttonError carries the mapper's code for it`() {
        delegate = createDelegate(errorCodeMapper = { exception ->
            (exception as? IONGLOCException.IONGLOCLocationButtonException)?.let { "OS-PLUG-GLOC-0019" }
        })

        delegate.emitButtonError(IONGLOCException.IONGLOCLocationButtonException("session closed"))

        assertEquals(
            listOf("buttonError" to mapOf("reason" to "session closed", "code" to "OS-PLUG-GLOC-0019")),
            events,
        )
    }

    @Test
    fun `given the button reports an error and no mapper is supplied, buttonError carries the default code`() {
        delegate.emitButtonError(IONGLOCException.IONGLOCLocationButtonException("session closed"))

        assertEquals(
            listOf("buttonError" to mapOf("reason" to "session closed", "code" to "OS-PLUG-GLOC-0002")),
            events,
        )
    }

    // endregion

    @Test
    fun `given permission is denied, grant(false) is emitted and no fetch happens`() {
        delegate.permissionGranted(false)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("grant" to mapOf<String, Any?>("granted" to false)), events)
        coVerify(exactly = 0) { controller.getCurrentPosition(any(), any()) }
    }

    // region re-entrancy guard

    @Test
    fun `given a fetch is already in flight, a second permissionGranted(true) does not start another fetch`() {
        coEvery { controller.getCurrentPosition(any(), any()) } coAnswers {
            delay(10_000)
            Result.success(sampleResult())
        }

        delegate.permissionGranted(true)
        testScheduler.runCurrent()

        delegate.permissionGranted(true)

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { controller.getCurrentPosition(any(), any()) }
    }

    @Test
    fun `given a fetch completes without suspending, a later permissionGranted(true) still fetches`() {
        // Main.immediate runs the launched block eagerly in production; an unconfined dispatcher reproduces it.
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val eagerDelegate = createDelegate()
        eagerDelegate.onResume()
        coEvery { controller.getCurrentPosition(any(), any()) } returns
            Result.failure(IONGLOCException.IONGLOCLocationRetrievalTimeoutException("fast failure"))

        eagerDelegate.permissionGranted(true)
        eagerDelegate.permissionGranted(true)

        coVerify(exactly = 2) { controller.getCurrentPosition(any(), any()) }
    }

    // endregion

    // region lifecycle

    @Test
    fun `given a fetch is suspended mid-flight, onPause does not cancel it and its position is delivered on resume`() {
        coEvery { controller.getCurrentPosition(any(), any()) } coAnswers {
            delay(10_000) // e.g. waiting for the "turn on location" dialog, which pauses the Activity
            Result.success(sampleResult())
        }

        delegate.permissionGranted(true)
        testScheduler.runCurrent() // reach the suspension point inside the launched coroutine

        delegate.onPause()
        testScheduler.advanceUntilIdle() // the fetch completes while paused

        assertTrue(events.none { it.first == "position" })

        delegate.onResume()

        assertEquals(1, events.count { it.first == "position" })
        coVerify(exactly = 1) { controller.getCurrentPosition(any(), any()) }
    }

    @Test
    fun `given a fetch fails while the island is paused, buttonError is delivered on resume`() {
        coEvery { controller.getCurrentPosition(any(), any()) } coAnswers {
            delay(10_000)
            Result.failure(IONGLOCException.IONGLOCLocationRetrievalTimeoutException("timed out"))
        }

        delegate.permissionGranted(true)
        testScheduler.runCurrent()
        delegate.onPause()
        testScheduler.advanceUntilIdle()

        assertTrue(events.none { it.first == "buttonError" })

        delegate.onResume()

        assertEquals(1, events.count { it.first == "buttonError" })
    }

    @Test
    fun `given a fetch finished while paused, onDestroy drops its result and a later onResume does not deliver it`() {
        coEvery { controller.getCurrentPosition(any(), any()) } coAnswers {
            delay(10_000)
            Result.success(sampleResult())
        }

        delegate.permissionGranted(true)
        testScheduler.runCurrent()
        delegate.onPause()
        testScheduler.advanceUntilIdle()

        delegate.onDestroy()
        delegate.onResume()

        assertTrue(events.none { it.first == "position" })
    }

    @Test
    fun `given a fetch is suspended mid-flight, onDestroy cancels it and a later onResume does not revive it`() {
        coEvery { controller.getCurrentPosition(any(), any()) } coAnswers {
            delay(10_000)
            Result.success(sampleResult())
        }

        delegate.permissionGranted(true)
        testScheduler.runCurrent()

        delegate.onDestroy()
        testScheduler.advanceUntilIdle()
        delegate.onResume()

        assertTrue(events.none { it.first == "position" })
    }

    @Test
    fun `given onDestroy has already been called, permissionGranted is a no-op`() {
        delegate.onDestroy()

        delegate.permissionGranted(true)

        assertTrue(events.isEmpty())
        coVerify(exactly = 0) { controller.getCurrentPosition(any(), any()) }
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
