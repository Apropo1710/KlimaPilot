package de.klimapilot.app

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class EnergyMathTest {
    private val now = LocalDateTime.of(2026, 11, 20, 9, 15)
    private val buero = EnergyUnitData(
        airconId = "a", name = "Klimaanlage Büro", room = "Büro",
        hourly = mapOf("2026-11-20T08" to 0.25, "2026-11-20T09" to 0.5, "2026-11-19T09" to 1.0),
        daily = mapOf("2026-11-20" to 0.75, "2026-11-19" to 2.0, "2026-11-16" to 1.0, "2026-10-31" to 3.0, "2025-12-31" to 4.0),
        lastPollAt = null, lastError = null,
    )
    private val schlafzimmer = buero.copy(airconId = "b", hourly = mapOf("2026-11-20T09" to 0.25), daily = mapOf("2026-11-20" to 0.25))

    @Test
    fun hoursCoverTheLast24HoursEndingNow() {
        val bars = EnergyMath.bars(EnergyPeriod.HOURS, listOf(buero), now)
        assertEquals(24, bars.size)
        assertEquals("09", bars.last().label)
        assertEquals(0.5, bars.last().kWh, 1e-9)
        assertEquals(0.25, bars[22].kWh, 1e-9)
        // 2026-11-19T09 is exactly 24 h ago and therefore outside the window.
        assertEquals(0.75, bars.sumOf { it.kWh }, 1e-9)
    }

    @Test
    fun unitsAreSummed() {
        val bars = EnergyMath.bars(EnergyPeriod.DAYS, listOf(buero, schlafzimmer), now)
        assertEquals(14, bars.size)
        assertEquals(1.0, bars.last().kWh, 1e-9)
        assertEquals(2.0, bars[12].kWh, 1e-9)
    }

    @Test
    fun weeksStartOnMonday() {
        val bars = EnergyMath.bars(EnergyPeriod.WEEKS, listOf(buero), now)
        assertEquals("KW47", bars.last().label)
        // Monday 16.11. to Sunday 22.11.
        assertEquals(3.75, bars.last().kWh, 1e-9)
    }

    @Test
    fun monthsAndYears() {
        val months = EnergyMath.bars(EnergyPeriod.MONTHS, listOf(buero), now)
        assertEquals(12, months.size)
        assertEquals(3.75, months.last().kWh, 1e-9)
        assertEquals(3.0, months[10].kWh, 1e-9)
        val years = EnergyMath.bars(EnergyPeriod.YEARS, listOf(buero), now)
        assertEquals(listOf("2025", "2026"), years.map { it.label })
        assertEquals(4.0, years.first().kWh, 1e-9)
        assertEquals(6.75, years.last().kWh, 1e-9)
    }

    @Test
    fun todayAddsWhatTheUnitCountedSinceTheLastRecording() {
        val polled = now.minusMinutes(3).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().toString()
        val unit = buero.copy(lastRunKwh = 2.25, lastPollAt = polled)
        assertEquals(0.75 + 0.25, EnergyMath.todayKwh(unit, 2.5, now), 1e-9)
        // A new run started since the last recording: the live counter counts in full.
        assertEquals(0.75 + 0.25, EnergyMath.todayKwh(unit, 0.25, now), 1e-9)
        // Last recording from yesterday: only what was recorded today.
        val old = unit.copy(lastPollAt = now.minusDays(1).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().toString())
        assertEquals(0.75, EnergyMath.todayKwh(old, 2.5, now), 1e-9)
    }
}
