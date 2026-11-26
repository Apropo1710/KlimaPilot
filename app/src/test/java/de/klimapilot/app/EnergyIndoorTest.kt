package de.klimapilot.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EnergyIndoorTest {
    @Test
    fun roomTemperatureIsReadAndAveragedLikeOutside() {
        val unit = EnergyMath.parseUnit(JSONObject("""
            {"airconId":"a","hourly":{},"daily":{},
             "indoorHourly":{"2026-11-27T06":19.4,"2026-11-27T07":20.2},
             "indoorDaily":{"2026-11-27":19.8,"2026-11-28":20.4},
             "outdoorDaily":{"2026-11-27":-3.0}}
        """.trimIndent()))!!
        val day = LocalDate.of(2026, 11, 27)
        val hours = EnergyMath.bars(EnergyPeriod.DAY, listOf(unit), day, day)
        assertEquals(19.4, hours[6].indoor!!, 0.001)
        assertNull(hours[5].indoor)
        val week = EnergyMath.bars(EnergyPeriod.WEEK, listOf(unit), day, day.plusDays(1))
        assertEquals(19.8, week[4].indoor!!, 0.001) // Friday 27.11.
        assertEquals(-3.0, week[4].outdoor!!, 0.001)
        val month = EnergyMath.bars(EnergyPeriod.YEAR, listOf(unit), day, day.plusDays(1))
        assertEquals(20.1, month[10].indoor!!, 0.001) // November: mean of both days
    }
}
