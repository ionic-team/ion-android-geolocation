package io.ionic.libs.iongeolocationlib.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IONGLOCLocationButtonIslandTest {

    @Test
    fun `when long property is a valid number at or above minimum, value is returned`() {
        val params = mapOf<String, Any?>("timeout" to 5_000L)

        assertEquals(5_000L, params.long("timeout", minimum = 1))
    }

    @Test
    fun `when long property is missing, null is returned`() {
        val params = emptyMap<String, Any?>()

        assertNull(params.long("timeout", minimum = 1))
    }

    @Test
    fun `when long property is below minimum, null is returned`() {
        val params = mapOf<String, Any?>("timeout" to -1L)

        assertNull(params.long("timeout", minimum = 1))
    }

    @Test
    fun `when long property is zero and minimum is one, null is returned`() {
        val params = mapOf<String, Any?>("timeout" to 0L)

        assertNull(params.long("timeout", minimum = 1))
    }

    @Test
    fun `when long property is zero and minimum is zero, zero is returned`() {
        val params = mapOf<String, Any?>("maximumAge" to 0L)

        assertEquals(0L, params.long("maximumAge", minimum = 0))
    }

    @Test
    fun `when boolean property is true, true is returned`() {
        val params = mapOf<String, Any?>("enableLocationFallback" to true)

        assertEquals(true, params.boolean("enableLocationFallback"))
    }

    @Test
    fun `when boolean property is false, false is returned`() {
        val params = mapOf<String, Any?>("enableLocationFallback" to false)

        assertEquals(false, params.boolean("enableLocationFallback"))
    }

    @Test
    fun `when boolean property is missing, null is returned`() {
        val params = emptyMap<String, Any?>()

        assertNull(params.boolean("enableLocationFallback"))
    }
}
