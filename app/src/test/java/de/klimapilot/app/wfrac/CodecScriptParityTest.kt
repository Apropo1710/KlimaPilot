package de.klimapilot.app.wfrac

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The ioBroker script (scripts/iobroker/klimapilot.js) carries its own copy of the frame
 * encoder for schedules. These frames were produced by that script; the app must encode the
 * same states to exactly the same bytes, so both stay in step.
 */
class CodecScriptParityTest {
    private fun state(power: Boolean, mode: Mode, temp: Double, airFlow: Int, ud: Int, lr: Int, entrust: Boolean,
                      coolHot: Boolean, modelNr: Int, modelNrRaw: Int, vacant: Boolean, selfClean: Boolean) =
        AirconState(power, mode, temp, airFlow, ud, lr, entrust, coolHot, modelNr, modelNrRaw, vacant, selfClean)

    @Test
    fun encodesLikeTheIoBrokerScript() {
        assertEquals(
            "AACzqav/AAAAAAATDgAAAAAAAf/////ccQAAESEr/wAAAAAAAwQAAAAAAAH/////oIE=",
            Codec.encode(state(true, Mode.HEAT, 21.5, 2, 3, 4, true, true, 0, 0, false, false))
        )
        assertEquals(
            "AADqj7D/AAAIAAAQmwAAAAAAAf/////LMAMASAcw/wAACAAAAJEAAAAAAAH/////qm8=",
            Codec.encode(state(false, Mode.COOL, 24.0, 0, 0, 0, false, false, 2, 3, false, true))
        )
        assertEquals(
            "AACnjqD/AAAAAAEWigAAAAAAAf////8EtwEABQYg/wAAAAABBoAAAAAAAAH/////cyI=",
            Codec.encode(state(true, Mode.DRY, 16.0, 4, 1, 7, false, true, 1, 1, true, false))
        )
        assertEquals(
            "AACvuLz/AAAAAAARDgAAAAAAAf////+UBUAADTA8/wAAAAAAAQQAAAAAAAH/////MDQ=",
            Codec.encode(state(true, Mode.FAN, 30.0, 1, 4, 2, true, true, -1, 64, false, false))
        )
    }
}
