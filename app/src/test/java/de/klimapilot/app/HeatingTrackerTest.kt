package de.klimapilot.app

import org.junit.Assert.assertEquals
import org.junit.Test

class HeatingTrackerTest {
    @Test
    fun correctionFollowsTheSensor() {
        val key = "test-" + System.nanoTime()
        // Seen first while off: no extra.
        assertEquals(0.0, HeatingTracker.correction(key, 20.0, heating = false, offset = 3.0, unitOn = false), 1e-9)
        // Compressor starts: grows with the rise, never above the set value.
        assertEquals(1.0, HeatingTracker.correction(key, 21.0, heating = true, offset = 3.0, unitOn = true), 1e-9)
        assertEquals(3.0, HeatingTracker.correction(key, 24.0, heating = true, offset = 3.0, unitOn = true), 1e-9)
        assertEquals(3.0, HeatingTracker.correction(key, 25.0, heating = true, offset = 3.0, unitOn = true), 1e-9)
        // Compressor pauses, unit still heating: kept although the sensor falls.
        assertEquals(3.0, HeatingTracker.correction(key, 23.0, heating = false, offset = 3.0, unitOn = true), 1e-9)
        // Unit off: shrinks with the fall.
        assertEquals(2.0, HeatingTracker.correction(key, 22.0, heating = false, offset = 3.0, unitOn = false), 1e-9)
        assertEquals(0.0, HeatingTracker.correction(key, 19.5, heating = false, offset = 3.0, unitOn = false), 1e-9)
        // Seen first switched on in heating mode with the compressor pausing: full.
        assertEquals(3.0, HeatingTracker.correction(key + "b", 23.0, heating = false, offset = 3.0, unitOn = true), 1e-9)
    }
}
