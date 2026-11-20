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

    private val today = now.toLocalDate()

    @Test
    fun dayShowsTheHoursOfThatDay() {
        val bars = EnergyMath.bars(EnergyPeriod.DAY, listOf(buero), today, today)
        assertEquals(24, bars.size)
        assertEquals(0.25, bars[8].kWh, 1e-9)
        assertEquals(0.5, bars[9].kWh, 1e-9)
        assertEquals(1.0, EnergyMath.bars(EnergyPeriod.DAY, listOf(buero), today.minusDays(1), today)[9].kWh, 1e-9)
    }

    @Test
    fun weekRunsMondayToSundayAndSumsUnits() {
        val bars = EnergyMath.bars(EnergyPeriod.WEEK, listOf(buero, schlafzimmer), today, today)
        assertEquals(listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"), bars.map { it.label })
        assertEquals(1.0, bars[0].kWh, 1e-9)   // 16.11.
        assertEquals(2.0, bars[3].kWh, 1e-9)   // 19.11.
        assertEquals(1.0, bars[4].kWh, 1e-9)   // 20.11., both units
        assertEquals(java.time.LocalDate.of(2026, 11, 20), bars[4].drillDown)
        assertEquals("KW 47 · 16.11.–22.11.2026", EnergyMath.title(EnergyPeriod.WEEK, today))
    }

    @Test
    fun monthYearAndAll() {
        val month = EnergyMath.bars(EnergyPeriod.MONTH, listOf(buero), today, today)
        assertEquals(30, month.size)
        assertEquals(3.75, month.sumOf { it.kWh }, 1e-9)
        val year = EnergyMath.bars(EnergyPeriod.YEAR, listOf(buero), today, today)
        assertEquals(12, year.size)
        assertEquals(3.0, year[9].kWh, 1e-9)
        assertEquals(3.75, year[10].kWh, 1e-9)
        val all = EnergyMath.bars(EnergyPeriod.ALL, listOf(buero), today, today)
        assertEquals(listOf("2025", "2026"), all.map { it.label })
        assertEquals(6.75, all.last().kWh, 1e-9)
    }

    @Test
    fun navigationStopsAtTheCurrentPeriod() {
        assertEquals(false, EnergyMath.isBeforeCurrent(EnergyPeriod.WEEK, today, today))
        assertEquals(true, EnergyMath.isBeforeCurrent(EnergyPeriod.WEEK, EnergyMath.shift(EnergyPeriod.WEEK, today, -1), today))
        assertEquals(EnergyPeriod.MONTH, EnergyMath.finer(EnergyPeriod.YEAR))
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
