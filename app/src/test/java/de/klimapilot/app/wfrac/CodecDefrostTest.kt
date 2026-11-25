package de.klimapilot.app.wfrac

import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecDefrostTest {
    /** Frame with the 18-byte content block at offset 21; c2 = power/mode, c9 = operation status. */
    private fun frame(c2: Int, c9: Int, c8: Int = 0): String {
        val content = ByteArray(18)
        content[2] = c2.toByte()
        content[8] = c8.toByte()
        content[9] = c9.toByte()
        val bytes = ByteArray(21) + content + ByteArray(1) + ByteArray(2)
        return Base64.getEncoder().encodeToString(bytes)
    }

    @Test
    fun defrostBitWhileHeatingIsDefrost() {
        val state = Codec.decode(frame(c2 = 0x01 or Mode.HEAT.receive, c9 = 0x01))
        assertTrue(state.defrosting)
        assertFalse(state.compressorRunning)
    }

    @Test
    fun defrostWithCompressorRunningInReverse() {
        val state = Codec.decode(frame(c2 = 0x01 or Mode.HEAT.receive, c9 = 0x03))
        assertTrue(state.defrosting)
        assertTrue(state.compressorRunning)
    }

    @Test
    fun noDefrostWithoutBitOrWhenNotHeating() {
        assertFalse(Codec.decode(frame(c2 = 0x01 or Mode.HEAT.receive, c9 = 0x02)).defrosting)
        assertFalse(Codec.decode(frame(c2 = 0x01 or Mode.COOL.receive, c9 = 0x01)).defrosting)
        assertFalse(Codec.decode(frame(c2 = 0x00 or Mode.HEAT.receive, c9 = 0x01)).defrosting)
    }

    @Test
    fun autoModeCountsAsHeatingWhenCoolHotJudgeSaysHeat() {
        assertTrue(Codec.decode(frame(c2 = 0x01 or Mode.AUTO.receive, c9 = 0x01, c8 = 0x00)).defrosting)
        assertFalse(Codec.decode(frame(c2 = 0x01 or Mode.AUTO.receive, c9 = 0x01, c8 = 0x08)).defrosting)
    }
}
