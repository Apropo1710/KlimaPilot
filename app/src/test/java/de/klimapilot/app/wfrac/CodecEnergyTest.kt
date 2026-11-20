package de.klimapilot.app.wfrac

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodecEnergyTest {
    private fun blob(vararg segments: Int): String {
        val bytes = ByteArray(21 + 18 + 1) + segments.map { it.toByte() }.toByteArray() + ByteArray(2)
        return Base64.getEncoder().encodeToString(bytes)
    }

    @Test
    fun decodesRunEnergyInQuarterKwhSteps() {
        val state = Codec.decode(blob(0x94, 0x10, 0x0a, 0x01))
        assertEquals((0x010a) * 0.25, state.runEnergyKwh!!, 0.0001)
    }

    @Test
    fun runEnergyIsNullWhenNotReported() {
        assertNull(Codec.decode(blob(0x90, 0x10, 0x05, 0x00)).runEnergyKwh)
    }
}
