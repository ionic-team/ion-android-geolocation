package io.ionic.libs.iongeolocationlib.controller

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import io.mockk.unmockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

class IONGLOCSensorHandlerTest {

    private lateinit var context: Context
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    private lateinit var magnetometer: Sensor
    private lateinit var sensorHandler: IONGLOCSensorHandler

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        sensorManager = mockk(relaxed = true)
        accelerometer = mockk(relaxed = true)
        magnetometer = mockk(relaxed = true)

        mockkStatic(SensorManager::class)
        // Mock getRotationMatrix to return true and fill the R matrix
        every { SensorManager.getRotationMatrix(any(), any(), any(), any()) } answers {
            val r = args[0] as FloatArray
            // Identity matrix for simplicity
            r[0] = 1f; r[4] = 1f; r[8] = 1f
            true
        }
        // Mock getOrientation to return a fixed azimuth (e.g., 90 degrees = 1.57 radians)
        every { SensorManager.getOrientation(any(), any()) } answers {
            val values = args[1] as FloatArray
            values[0] = 1.5708f // 90 degrees in radians
            values
        }

        every { context.getSystemService(Context.SENSOR_SERVICE) } returns sensorManager
        every { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) } returns accelerometer
        every { sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) } returns magnetometer

        sensorHandler = IONGLOCSensorHandler(context)
    }

    @org.junit.After
    fun tearDown() {
        io.mockk.unmockkStatic(SensorManager::class)
    }

    @Test
    fun `when start is called, sensors are registered`() {
        sensorHandler.start()

        verify { sensorManager.registerListener(sensorHandler, accelerometer, SensorManager.SENSOR_DELAY_UI) }
        verify { sensorManager.registerListener(sensorHandler, magnetometer, SensorManager.SENSOR_DELAY_UI) }
    }

    @Test
    fun `when stop is called, sensors are unregistered`() {
        sensorHandler.start()
        sensorHandler.stop()

        verify { sensorManager.unregisterListener(sensorHandler) }
    }

    @Test
    fun `when start is called multiple times, sensors are registered only once`() {
        sensorHandler.start()
        sensorHandler.start()

        verify(exactly = 1) { sensorManager.registerListener(sensorHandler, accelerometer, SensorManager.SENSOR_DELAY_UI) }
    }

    @Test
    fun `when stop is called but watchers remain, sensors are not unregistered`() {
        sensorHandler.start()
        sensorHandler.start()
        sensorHandler.stop()

        verify(exactly = 0) { sensorManager.unregisterListener(sensorHandler) }
    }

    @Test
    fun `when orientation is calculated, magneticHeading is updated`() {
        // Mock accelerometer data (gravity pointing down)
        val gravityEvent = createSensorEvent(Sensor.TYPE_ACCELEROMETER, floatArrayOf(0f, 0f, 9.8f))
        sensorHandler.onSensorChanged(gravityEvent)

        // Mock magnetometer data (pointing North)
        val geoEvent = createSensorEvent(Sensor.TYPE_MAGNETIC_FIELD, floatArrayOf(0f, 50f, 0f))
        sensorHandler.onSensorChanged(geoEvent)

        assertNotNull(sensorHandler.magneticHeading)
    }

    @Test
    fun `when location is updated, trueHeading is calculated`() {
        // Setup headings first
        val gravityEvent = createSensorEvent(Sensor.TYPE_ACCELEROMETER, floatArrayOf(0f, 0f, 9.8f))
        sensorHandler.onSensorChanged(gravityEvent)
        val geoEvent = createSensorEvent(Sensor.TYPE_MAGNETIC_FIELD, floatArrayOf(0f, 50f, 0f))
        sensorHandler.onSensorChanged(geoEvent)

        val location = mockk<Location>(relaxed = true)
        every { location.latitude } returns 37.7749 // San Francisco
        every { location.longitude } returns -122.4194
        every { location.altitude } returns 0.0

        sensorHandler.updateLocation(location)

        assertNotNull(sensorHandler.trueHeading)
        assertNotNull(sensorHandler.magneticHeading)
    }

    private fun createSensorEvent(sensorType: Int, values: FloatArray): SensorEvent {
        val sensorEvent = mockk<SensorEvent>(relaxed = true)
        val sensor = mockk<Sensor>(relaxed = true)
        every { sensor.type } returns sensorType
        
        // Use reflection to set values since setters are not available/mockable easily on final field
        val valuesField = SensorEvent::class.java.getField("values")
        valuesField.isAccessible = true
        // Set the mock's values field to our array. 
        // Note: In a real SensorEvent, this is a final float[], but mockk objects might behave differently.
        // Actually, we can just mock the property access if it's a property in Kotlin, but SensorEvent.values is a public field in Java.
        // MockK cannot easily mock public fields. We have to instantiate a real SensorEvent or use Unsafe.
        // Easier approach: Just rely on the fact that onSensorChanged reads event.values. 
        // We can't easily mock field access on a mock object.
        // Alternative: Create a real SensorEvent via reflection constructor if possible, or use a helper.
        // Limitation: SensorEvent has package-private constructor.
        
        // Let's try mocking the field access using every { } is not possible for fields.
        // The simplest way to test onSensorChanged logic logic involving values is to wrap the logic 
        // or accept that we can't fully unit test `onSensorChanged` without instrumentation or Robolectric.
        // However, we CAN write to the field of the mock object!
        
        valuesField.set(sensorEvent, values)
        
        val sensorField = SensorEvent::class.java.getField("sensor")
        sensorField.isAccessible = true
        sensorField.set(sensorEvent, sensor)
        
        return sensorEvent
    }
}
