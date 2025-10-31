package io.ionic.libs.iongeolocationlib.controller

import android.app.Activity
import android.app.PendingIntent
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import androidx.core.util.Consumer
import app.cash.turbine.test
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.LocationSettingsResult
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import io.ionic.libs.iongeolocationlib.controller.helper.IONGLOCBuildConfig
import io.ionic.libs.iongeolocationlib.controller.helper.IONGLOCFallbackHelper
import io.ionic.libs.iongeolocationlib.controller.helper.IONGLOCGoogleServicesHelper
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
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test


@OptIn(ExperimentalCoroutinesApi::class)
class IONGLOCControllerTest {

    private val fusedLocationProviderClient = mockk<FusedLocationProviderClient>()
    private val activityResultLauncher = mockk<ActivityResultLauncher<IntentSenderRequest>>()
    private val googleApiAvailability = mockk<GoogleApiAvailability>()
    private val locationSettingsClient = mockk<SettingsClient>()
    private val locationManager = mockk<LocationManager>()
    private val connectivityManager = mockk<ConnectivityManager>()
    private val googleServicesHelper = spyk(
        IONGLOCGoogleServicesHelper(
            locationManager,
            connectivityManager,
            fusedLocationProviderClient,
            activityResultLauncher
        )
    )
    private val fallbackHelper = spyk(IONGLOCFallbackHelper(locationManager, connectivityManager))

    private val mockAndroidLocation = mockkLocation()
    private val locationSettingsTask = mockk<Task<LocationSettingsResponse>>(relaxed = true)
    private val currentLocationTask = mockk<Task<Location?>>(relaxed = true)
    private val voidTask = mockk<Task<Void>>(relaxed = true)

    private lateinit var sut: IONGLOCController
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationListenerCompat: LocationListenerCompat

    @Before
    fun setUp() {
        mockkStatic(GoogleApiAvailability::class)
        every { GoogleApiAvailability.getInstance() } returns googleApiAvailability
        mockkStatic(LocationServices::class)
        every { LocationServices.getSettingsClient(any()) } returns locationSettingsClient
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        mockkObject(IONGLOCBuildConfig)
        every { IONGLOCBuildConfig.getAndroidSdkVersionCode() } returns Build.VERSION_CODES.VANILLA_ICE_CREAM
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.d(any(), any(), any()) } returns 0
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk<Looper>()
        mockkStatic(LocationManagerCompat::class)

        sut = IONGLOCController(
            fusedLocationClient = fusedLocationProviderClient,
            locationManager = locationManager,
            connectivityManager = connectivityManager,
            activityLauncher = activityResultLauncher,
            googleServicesHelper = googleServicesHelper,
            fallbackHelper = fallbackHelper
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(LocationManagerCompat::class)
        unmockkStatic(Looper::class)
        unmockkStatic(Log::class)
        unmockkObject(IONGLOCBuildConfig)
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
        unmockkStatic(LocationServices::class)
        unmockkStatic(GoogleApiAvailability::class)
    }

    // region getCurrentLocation tests
    @Test
    fun `given all conditions check out, when getCurrentLocation is called, a location is returned`() =
        runTest {
            givenSuccessConditions()

            val result = sut.getCurrentPosition(mockk<Activity>(), locationOptions)

            assertTrue(result.isSuccess)
            assertEquals(locationResult, result.getOrNull())
        }

    @Test
    fun `given negative timeout in getCurrentLocation, IONGLOCInvalidTimeoutException is returned`() =
        runTest {
            // nothing to setup in this test

            val result =
                sut.getCurrentPosition(mockk<Activity>(), locationOptions.copy(timeout = -1))

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IONGLOCException.IONGLOCInvalidTimeoutException)
        }

    @Test
    fun `given null location is returned, when getCurrentLocation is called, IONGLOCLocationRetrievalTimeoutException is returned`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks
            coEvery { currentLocationTask.await() } returns null

            val result = sut.getCurrentPosition(mockk<Activity>(), locationOptions)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IONGLOCException.IONGLOCLocationRetrievalTimeoutException)
        }

    @Test
    fun `given play services not available with resolvable error, when getCurrentLocation is called, IONGLOCGoogleServicesException is returned with resolvable=true`() =
        runTest {
            givenPlayServicesNotAvailableWithResolvableError()

            val result = sut.getCurrentPosition(mockk<Activity>(), locationOptions)

            assertTrue(result.isFailure)
            result.exceptionOrNull().let { exception ->
                assertTrue(exception is IONGLOCException.IONGLOCGoogleServicesException)
                assertTrue((exception as IONGLOCException.IONGLOCGoogleServicesException).resolvable)
            }
        }

    @Test
    fun `given play services not available with un-resolvable error, when getCurrentLocation is called, IONGLOCGoogleServicesException is returned with resolvable=false`() =
        runTest {
            givenPlayServicesNotAvailableWithUnResolvableError()

            val result = sut.getCurrentPosition(mockk<Activity>(), locationOptions)

            assertTrue(result.isFailure)
            result.exceptionOrNull().let { exception ->
                assertTrue(exception is IONGLOCException.IONGLOCGoogleServicesException)
                assertFalse((exception as IONGLOCException.IONGLOCGoogleServicesException).resolvable)
            }
        }

    @Test
    fun `given user resolves location settings, when getCurrentLocation is called, the location is returned`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks
            givenResolvableApiException(Activity.RESULT_OK)

            val result = sut.getCurrentPosition(mockk<Activity>(), locationOptions)
            testScheduler.advanceTimeBy(DELAY)

            assertTrue(result.isSuccess)
            assertEquals(locationResult, result.getOrNull())
        }

    @Test
    fun `given user does not resolve location settings, when getCurrentLocation is called, IONGLOCRequestDeniedException returned`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks
            givenResolvableApiException(Activity.RESULT_CANCELED)

            val result = sut.getCurrentPosition(mockk<Activity>(), locationOptions)
            testScheduler.advanceTimeBy(DELAY)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IONGLOCException.IONGLOCRequestDeniedException)
        }

    @Test
    fun `given location settings check fails, when getCurrentLocation is called, IONGLOCSettingsException is returned`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks
            val error = RuntimeException()
            coEvery { locationSettingsTask.await() } throws error

            val result = sut.getCurrentPosition(mockk<Activity>(), locationOptions)

            assertTrue(result.isFailure)
            result.exceptionOrNull().let { exception ->
                assertTrue(exception is IONGLOCException.IONGLOCSettingsException)
                assertEquals(
                    error,
                    (exception as IONGLOCException.IONGLOCSettingsException).cause
                )
            }
        }
    // endregion getCurrentLocation tests

    // region addWatch tests
    @Test
    fun `given all conditions check out, when addWatch is called, locations are returned in flow`() =
        runTest {
            givenSuccessConditions()

            sut.addWatch(mockk<Activity>(), locationOptions, "1").test {
                // to wait until locationListenerCompat is instantiated, but not long enough for timeout to trigger
                advanceTimeBy(locationOptionsWithFallback.timeout / 2)
                emitLocationsGMS(listOf(mockAndroidLocation))
                var result = awaitItem()
                assertTrue(result.isSuccess)
                assertEquals(listOf(locationResult), result.getOrNull())


                emitLocationsGMS(
                    listOf(
                        mockkLocation { every { time } returns 1234L },
                        mockkLocation { every { time } returns 12345L },
                        mockkLocation { every { time } returns 123456L }
                    )
                )
                result = awaitItem()
                assertEquals(
                    listOf(
                        locationResult.copy(timestamp = 1234L),
                        locationResult.copy(timestamp = 12345L),
                        locationResult.copy(timestamp = 123456L)
                    ),
                    result.getOrNull()
                )
            }
        }

    @Test
    fun `given play services not available, when addWatch is called, IONGLOCGoogleServicesException is returned`() =
        runTest {
            givenPlayServicesNotAvailableWithResolvableError()

            sut.addWatch(mockk<Activity>(), locationOptions, "1").test {
                val result = awaitItem()

                assertTrue(result.isFailure)
                result.exceptionOrNull().let { exception ->
                    assertTrue(exception is IONGLOCException.IONGLOCGoogleServicesException)
                    assertTrue((exception as IONGLOCException.IONGLOCGoogleServicesException).resolvable)
                }
                awaitComplete()
            }
        }

    @Test
    fun `given user resolves location settings, when addWatch is called, the location is returned in flow`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks
            givenResolvableApiException(Activity.RESULT_OK)

            sut.addWatch(mockk<Activity>(), locationOptions, "1").test {
                // to wait until locationListenerCompat is instantiated, but not long enough for timeout to trigger
                advanceTimeBy(locationOptionsWithFallback.timeout / 2)
                emitLocationsGMS(listOf(mockAndroidLocation))
                val result = awaitItem()

                assertTrue(result.isSuccess)
                assertEquals(listOf(locationResult), result.getOrNull())
                expectNoEvents()
            }
        }

    @Test
    fun `given user does not resolve location settings, when addWatch is called, IONGLOCRequestDeniedException returned`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks
            givenResolvableApiException(Activity.RESULT_CANCELED)

            sut.addWatch(mockk<Activity>(), locationOptions, "1").test {
                testScheduler.advanceTimeBy(DELAY)
                val result = awaitItem()

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is IONGLOCException.IONGLOCRequestDeniedException)
                awaitComplete()
            }
        }

    @Test
    fun `given location settings check fails, when addWatch is called, IONGLOCSettingsException is returned`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks
            val error = RuntimeException()
            coEvery { locationSettingsTask.await() } throws error

            sut.addWatch(mockk<Activity>(), locationOptions, "1").test {
                testScheduler.advanceTimeBy(DELAY)
                val result = awaitItem()

                assertTrue(result.isFailure)
                result.exceptionOrNull().let { exception ->
                    assertTrue(exception is IONGLOCException.IONGLOCSettingsException)
                    assertEquals(
                        error,
                        (exception as IONGLOCException.IONGLOCSettingsException).cause
                    )
                }
                awaitComplete()
            }
        }

    @Test
    fun `given there are no location updates, when addWatch is called, IONGLOCLocationRetrievalTimeoutException is returned`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks

            sut.addWatch(mockk<Activity>(), locationOptions, "1").test {
                testScheduler.advanceUntilIdle()
                val result = awaitItem()

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is IONGLOCException.IONGLOCLocationRetrievalTimeoutException)
                awaitComplete()
            }
        }
    // endregion addWatch tests

    // region clearWatch tests
    @Test
    fun `given watch was added, when clearWatch is called, true is returned`() = runTest {
        val watchId = "id"
        givenSuccessConditions()
        sut.addWatch(mockk<Activity>(), locationOptions, watchId).test {
            // to wait until locationListenerCompat is instantiated, but not long enough for timeout to trigger
            advanceTimeBy(locationOptionsWithFallback.timeout / 2)

            val result = sut.clearWatch(watchId)

            assertTrue(result)
            expectNoEvents()
        }
        verify { fusedLocationProviderClient.removeLocationUpdates(locationCallback) }
    }

    @Test
    fun `given watch not added added, when clearWatch is called, false is returned`() = runTest {
        val watchId = "id"
        givenSuccessConditions()

        val result = sut.clearWatch(watchId)

        assertFalse(result)
        verify(inverse = true) { fusedLocationProviderClient.removeLocationUpdates(any<LocationCallback>()) }
    }

    @Test
    fun `given clearWatch called, when addWatch is called, the location is not emitted in flow`() =
        runTest {
            val watchId = "id"
            givenSuccessConditions()
            sut.clearWatch(watchId)

            sut.addWatch(mockk<Activity>(), locationOptions, watchId).test {
                // to wait until locationListenerCompat is instantiated, but not long enough for timeout to trigger
                advanceTimeBy(locationOptionsWithFallback.timeout / 2)

                emitLocationsGMS(listOf(mockAndroidLocation))

                ensureAllEventsConsumed()
            }
        }
    // endregion clearWatch tests

    // region fallback tests
    @Test
    fun `given location settings check fails but enableLocationManagerFallback=true and there is cached data, when getCurrentLocation is called, result is returned`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks
            coEvery { locationSettingsTask.await() } throws RuntimeException()
            val currentTime = System.currentTimeMillis()
            every { locationManager.getLastKnownLocation(any()) } returns mockkLocation {
                every { time } returns currentTime
            }

            val result = sut.getCurrentPosition(mockk<Activity>(), locationOptionsWithFallback)

            assertTrue(result.isSuccess)
            assertEquals(locationResult.copy(timestamp = currentTime), result.getOrNull())
            coVerify(inverse = true) {
                // only getLastKnownLocation, no location update requested
                LocationManagerCompat.requestLocationUpdates(
                    any(),
                    any(),
                    any<LocationRequestCompat>(),
                    any(),
                    any<Looper>()
                )
            }
        }

    @Test
    fun `given location settings check fails with resolvableError but enableLocationManagerFallback=true but cached data is older, when getCurrentLocation is called, result is returned`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks
            givenResolvableApiException(Activity.RESULT_OK)
            every { locationManager.getLastKnownLocation(any()) } returns mockkLocation {
                every { time } returns System.currentTimeMillis() - (60_000L * 60_000L)
            }

            val deferred =
                async { sut.getCurrentPosition(mockk<Activity>(), locationOptionsWithFallback) }
            runCurrent() // to wait until locationListenerCompat is instantiated, can't use advanceUntilIdle because that would trigger the timeout
            locationListenerCompat.onLocationChanged(mockAndroidLocation)
            val result = deferred.await()

            assertTrue(result.isSuccess)
            assertEquals(locationResult, result.getOrNull())
            coVerify {
                // to confirm that listener has been removed by the end of getCurrentPosition
                LocationManagerCompat.removeUpdates(locationManager, locationListenerCompat)
            }
            // to confirm that the correct quality was passed, based on the fact that
            // 1. there is no network provider and 2. options#enableHighAccuracy=true
            val slot = slot<LocationRequestCompat>()
            coVerify {
                // only getLastKnownLocation, no location update requested
                LocationManagerCompat.requestLocationUpdates(
                    any(),
                    any(),
                    capture(slot),
                    any(),
                    any<Looper>()
                )
            }
            assertEquals(
                LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY,
                slot.captured.quality
            )
        }

    @Test
    fun `given all preconditions pass and enableLocationManagerFallback=true, when getCurrentLocation is called, the fallback is not called`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks

            sut.getCurrentPosition(mockk<Activity>(), locationOptionsWithFallback)

            coVerify(inverse = true) {
                fallbackHelper.getCurrentLocation(any())
            }
        }

    @Test
    fun `given location settings check fails with resolvableError, location is off, and enableLocationManagerFallback=true, when getCurrentLocation is called, the fallback is not called`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks
            givenResolvableApiException(Activity.RESULT_OK)
            every { LocationManagerCompat.isLocationEnabled(any()) } returns false

            sut.getCurrentPosition(mockk<Activity>(), locationOptionsWithFallback)

            coVerify(inverse = true) {
                fallbackHelper.getCurrentLocation(any())
            }
        }

    @Test
    fun `given fallback is being used but requestLocationUpdates does not notify listener, when getCurrentLocation is called, IONGLOCLocationRetrievalTimeoutException is returned`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks
            coEvery { locationSettingsTask.await() } throws RuntimeException()
            every { LocationManagerCompat.isLocationEnabled(any()) } returns false

            val deferred =
                async { sut.getCurrentPosition(mockk<Activity>(), locationOptionsWithFallback) }
            advanceTimeBy(locationOptionsWithFallback.timeout * 2)
            val result = deferred.await()

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IONGLOCException.IONGLOCLocationRetrievalTimeoutException)
        }

    @Test
    fun `given SETTINGS_CHANGE_UNAVAILABLE error and network+location disabled and enableLocationManagerFallback=true, when getCurrentLocation is called, IONGLOCLocationAndNetworkDisabledException is returned`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks
            coEvery { locationSettingsTask.await() } throws mockk<ApiException> {
                every { message } returns "8502: SETTINGS_CHANGE_UNAVAILABLE"
            }
            every { LocationManagerCompat.isLocationEnabled(any()) } returns false

            val result = sut.getCurrentPosition(mockk<Activity>(), locationOptionsWithFallback)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IONGLOCException.IONGLOCLocationAndNetworkDisabledException)
        }

    @Test
    fun `given play services not available but enableLocationManagerFallback=true, when addWatch is called, locations returned in flow`() =
        runTest {
            givenSuccessConditions()
            givenPlayServicesNotAvailableWithResolvableError()

            sut.addWatch(mockk<Activity>(), locationOptionsWithFallback, "1").test {
                // to wait until locationListenerCompat is instantiated, but not long enough for timeout to trigger
                advanceTimeBy(locationOptionsWithFallback.timeout / 2)
                emitLocationsFallback(listOf(mockAndroidLocation))
                var result = awaitItem()
                assertTrue(result.isSuccess)
                assertEquals(listOf(locationResult), result.getOrNull())


                emitLocationsFallback(
                    listOf(
                        mockkLocation { every { time } returns 1234L },
                        mockkLocation { every { time } returns 12345L },
                    )
                )
                result = awaitItem()
                assertEquals(
                    listOf(
                        locationResult.copy(timestamp = 1234L),
                        locationResult.copy(timestamp = 12345L),
                    ),
                    result.getOrNull()
                )
            }
        }

    @Test
    fun `given play services not available but enableLocationManagerFallback=true and there is cached location, when addWatch is called, cached location returned in flow`() =
        runTest {
            givenSuccessConditions()
            givenPlayServicesNotAvailableWithUnResolvableError()
            val currentTime = System.currentTimeMillis()
            every { locationManager.getLastKnownLocation(any()) } returns mockkLocation {
                every { time } returns currentTime
            }

            sut.addWatch(mockk<Activity>(), locationOptionsWithFallback, "1").test {
                testScheduler.advanceUntilIdle()

                val result = awaitItem()
                assertTrue(result.isSuccess)
                assertEquals(
                    listOf(locationResult.copy(timestamp = currentTime)),
                    result.getOrNull()
                )
                expectNoEvents()
            }
        }

    @Test
    fun `given fallback is being used but there are no location updates, when addWatch is called, IONGLOCLocationRetrievalTimeoutException is returned`() =
        runTest {
            givenSuccessConditions()
            givenPlayServicesNotAvailableWithUnResolvableError()

            sut.addWatch(mockk<Activity>(), locationOptionsWithFallback, "1").test {
                testScheduler.advanceUntilIdle()
                val result = awaitItem()

                assertTrue(result.isFailure)
                assertTrue(result.exceptionOrNull() is IONGLOCException.IONGLOCLocationRetrievalTimeoutException)
                awaitComplete()
            }
        }

    @Test
    fun `given all preconditions pass and enableLocationManagerFallback=true, when addWatch is called, the fallback is not called`() =
        runTest {
            givenSuccessConditions() // to instantiate mocks

            sut.addWatch(mockk<Activity>(), locationOptionsWithFallback, "1").test {
                // to wait until locationListenerCompat is instantiated, but not long enough for timeout to trigger
                advanceTimeBy(locationOptionsWithFallback.timeout / 2)
                emitLocationsGMS(listOf(mockAndroidLocation))
                assertTrue(awaitItem().isSuccess)
            }

            coVerify(inverse = true) {
                fallbackHelper.requestLocationUpdates(any(), any())
            }
        }

    @Test
    fun `given watch was added via fallback, when clearWatch is called, true is returned`() =
        runTest {
            val watchId = "id"
            givenSuccessConditions()
            givenPlayServicesNotAvailableWithUnResolvableError()
            sut.addWatch(mockk<Activity>(), locationOptionsWithFallback, watchId).test {
                // to wait until locationListenerCompat is instantiated, but not long enough for timeout to trigger
                advanceTimeBy(locationOptionsWithFallback.timeout / 2)
                emitLocationsFallback(listOf(mockAndroidLocation))
                awaitItem()

                val result = sut.clearWatch(watchId)

                assertTrue(result)
                expectNoEvents()
            }
            verify { LocationManagerCompat.removeUpdates(any(), locationListenerCompat) }
        }
    // endregion fallback tests

    // region utils
    private fun givenSuccessConditions() {
        every { googleApiAvailability.isGooglePlayServicesAvailable(any()) } returns ConnectionResult.SUCCESS
        every { locationSettingsClient.checkLocationSettings(any()) } returns locationSettingsTask
        coEvery { locationSettingsTask.await() } returns LocationSettingsResponse(
            LocationSettingsResult(
                Status.RESULT_SUCCESS,
                null
            )
        )

        every {
            fusedLocationProviderClient.getCurrentLocation(any<CurrentLocationRequest>(), any())
        } returns currentLocationTask
        coEvery { currentLocationTask.await() } returns mockAndroidLocation
        every {
            fusedLocationProviderClient.requestLocationUpdates(
                any(),
                any<LocationCallback>(),
                any()
            )
        } answers {
            locationCallback = args[1] as LocationCallback
            voidTask
        }
        every { fusedLocationProviderClient.removeLocationUpdates(any<LocationCallback>()) } returns voidTask

        every { connectivityManager.activeNetwork } returns null
        every { LocationManagerCompat.hasProvider(any(), any()) } returns true
        every { LocationManagerCompat.isLocationEnabled(any()) } returns true
        every { locationManager.getLastKnownLocation(any()) } returns null
        val consumerSlot = slot<Consumer<Location>>()
        every {
            LocationManagerCompat.getCurrentLocation(
                any(),
                any(),
                any<CancellationSignal>(),
                any(),
                capture(consumerSlot)
            )
        } answers {
            consumerSlot.captured.accept(mockAndroidLocation)
        }
        every {
            LocationManagerCompat.requestLocationUpdates(
                any(),
                any(),
                any<LocationRequestCompat>(),
                any(),
                any<Looper>()
            )
        } answers {
            locationListenerCompat = args[3] as LocationListenerCompat
        }
        every {
            LocationManagerCompat.removeUpdates(any(), any())
        } just runs
    }

    private fun givenPlayServicesNotAvailableWithResolvableError() {
        every { googleApiAvailability.isGooglePlayServicesAvailable(any()) } returns ConnectionResult.RESOLUTION_REQUIRED
        every { googleApiAvailability.isUserResolvableError(any()) } returns true
        every {
            googleApiAvailability.getErrorDialog(any<Activity>(), any(), any())
        } returns null
    }

    private fun givenPlayServicesNotAvailableWithUnResolvableError() {
        every { googleApiAvailability.isGooglePlayServicesAvailable(any()) } returns ConnectionResult.API_DISABLED
        every { googleApiAvailability.isUserResolvableError(any()) } returns false
    }

    private fun CoroutineScope.givenResolvableApiException(resultCode: Int) {
        coEvery { locationSettingsTask.await() } throws mockk<ResolvableApiException> {
            every { resolution } returns mockk<PendingIntent>(relaxed = true)
        }
        coEvery { activityResultLauncher.launch(any()) } coAnswers {
            launch {
                delay(DELAY) // simulate user delay in turning on location settings
                sut.onResolvableExceptionResult(resultCode)
            }
        }
    }

    private fun mockkLocation(overrideDefaultMocks: Location.() -> Unit = {}): Location =
        mockk<Location>(relaxed = true) {
            every { latitude } returns 1.0
            every { longitude } returns 2.0
            every { altitude } returns 3.0
            every { accuracy } returns 0.5f
            every { verticalAccuracyMeters } returns 1.5f
            every { bearing } returns 4.0f
            every { speed } returns 0.2f
            every { time } returns 1L
            overrideDefaultMocks()
        }

    private fun emitLocationsGMS(locationList: List<Location>) {
        locationCallback.onLocationResult(
            mockk<LocationResult>(relaxed = true) {
                every { locations } returns locationList.toMutableList()
            }
        )
    }

    private fun emitLocationsFallback(locationList: List<Location>) {
        if (locationList.size == 1) {
            locationListenerCompat.onLocationChanged(locationList.first())
        } else {
            locationListenerCompat.onLocationChanged(locationList)
        }
    }
    // endregion utils

    companion object {
        private const val DELAY = 3_000L

        private val locationOptions = IONGLOCLocationOptions(
            timeout = 60_000,
            maximumAge = 30_000,
            enableHighAccuracy = true,
            minUpdateInterval = 2000L,
            enableLocationManagerFallback = false
        )

        private val locationOptionsWithFallback =
            locationOptions.copy(enableLocationManagerFallback = true)

        private val locationResult = IONGLOCLocationResult(
            latitude = 1.0,
            longitude = 2.0,
            altitude = 3.0,
            accuracy = 0.5f,
            altitudeAccuracy = 1.5f,
            heading = 4.0f,
            speed = 0.2f,
            timestamp = 1L
        )
    }
}