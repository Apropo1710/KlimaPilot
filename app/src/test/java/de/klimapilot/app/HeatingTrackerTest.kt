package de.klimapilot.app

import org.junit.Assert.assertEquals
import org.junit.Test

class HeatingTrackerTest {
    @Test
    fun correctionFollowsTheSensor() {
        val key = "test-" + System.nanoTime()
        // Seen first while resting: no correction.
        assertEquals(0.0, HeatingTracker.correction(key, 20.0, heating = false, offset = 3.0), 1e-9)
        // Compressor starts: grows with the rise, never above the set value.
        assertEquals(1.0, HeatingTracker.correction(key, 21.0, heating = true, offset = 3.0), 1e-9)
        assertEquals(3.0, HeatingTracker.correction(key, 24.0, heating = true, offset = 3.0), 1e-9)
        assertEquals(3.0, HeatingTracker.correction(key, 25.0, heating = true, offset = 3.0), 1e-9)
        // Compressor stops: shrinks with the fall; the shown value stays at 22 until the sensor has dropped 3 °C.
        assertEquals(2.0, HeatingTracker.correction(key, 24.0, heating = false, offset = 3.0), 1e-9)
        assertEquals(0.0, HeatingTracker.correction(key, 21.5, heating = false, offset = 3.0), 1e-9)
        // Seen first while heating: full correction.
        assertEquals(3.0, HeatingTracker.correction(key + "b", 23.0, heating = true, offset = 3.0), 1e-9)
    }
}
