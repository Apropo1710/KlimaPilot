package de.klimapilot.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomTempModelTest {
    private val json = """
        {"using":"model","samples":12,"offset":3.4,"scenario":"Heizen, Pause lang",
         "scenarios":["Aus","Heizen, Pause lang"],"coverage":{"Heizen, Pause lang":4},
         "errorModel":0.31,"errorMean":0.62,"minForModel":8,
         "last":{"id":"s1","at":"2026-11-24T06:44:00.000Z","thermo":18.2,"raw":21.5},
         "input":{"id":"x","ok":true,"message":"Messwert gespeichert."}}
    """.trimIndent()

    @Test
    fun parsesAndUsesFreshOffsetOnly() {
        val now = 1_800_000_000_000L
        val model = RoomTempModel.parse(JSONObject(json), now)
        assertEquals(12, model.samples)
        assertEquals(4, model.coverage["Heizen, Pause lang"])
        assertEquals(18.2, model.last!!.thermo, 1e-9)
        assertEquals(3.4, model.currentOffset(now + 60_000)!!, 1e-9)
        // Stale (script stopped or unit unreachable): fixed correction applies instead.
        assertNull(model.currentOffset(now + RoomTempModel.STALE_MS + 1))
    }

    @Test
    fun nullOffsetAndTooFewReadingsGiveNoLearnedValue() {
        val now = 1_800_000_000_000L
        assertNull(RoomTempModel.parse(JSONObject("""{"using":"mean","samples":4,"offset":null}"""), now).currentOffset(now))
        assertNull(RoomTempModel.parse(JSONObject("""{"using":"none","samples":1,"offset":2.0}"""), now).currentOffset(now))
    }

    @Test
    fun thermometerDigitsGetTheCommaBeforeTheLastDigit() {
        assertEquals(17.5, thermometerValue("175")!!, 1e-9)
        assertEquals(17.0, thermometerValue("17")!!, 1e-9)
        assertNull(thermometerValue("1"))
        assertNull(thermometerValue("450"))
    }
}
