package de.klimapilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HintRulesTest {
    private val now = Instant.parse("2026-11-21T20:00:00Z").toEpochMilli()

    private fun unit(runDays: Int = 0, lastPollAt: String? = "2026-11-21T19:55:00Z", lastError: String? = null) = EnergyUnitData(
        airconId = "a1", name = "Klimaanlage Büro", room = "Büro", hourly = emptyMap(), daily = emptyMap(),
        lastPollAt = lastPollAt, lastError = lastError, runDays = runDays
    )

    private fun snapshot(units: List<EnergyUnitData>, schedules: List<ClimateSchedule> = emptyList(), status: Map<String, ScheduleRun> = emptyMap()) =
        HintSnapshot(units, FilterConfig(), schedules, status)

    @Test
    fun filterDueOnceWithKeyBoundToTheLastCleaning() {
        val findings = HintRules.findings(snapshot(listOf(unit(runDays = 14))), now)
        assertEquals(listOf("filter:a1:AIR:0"), findings.map { it.key })
    }

    @Test
    fun offlineOnlyAfterAnHourWithError() {
        assertTrue(HintRules.findings(snapshot(listOf(unit(lastError = "x"))), now).none { it.kind == HintKind.OFFLINE })
        val old = unit(lastPollAt = "2026-11-21T18:30:00Z", lastError = "connect ECONNREFUSED")
        assertEquals(listOf(HintKind.OFFLINE), HintRules.findings(snapshot(listOf(old)), now).map { it.kind })
    }

    @Test
    fun scheduleFailureOnlyAfterRetryWindow() {
        val item = ClimateSchedule("s1", "a1", kind = "weekly", days = listOf(6), time = "20:30", action = "off")
        val fresh = mapOf("s1" to ScheduleRun("2026-11-21T20:30", "2026-11-21T19:55:00Z", false, "timeout"))
        assertTrue(HintRules.findings(snapshot(listOf(unit()), listOf(item), fresh), now).none { it.kind == HintKind.SCHEDULE })
        val final = mapOf("s1" to ScheduleRun("2026-11-21T20:30", "2026-11-21T19:40:00Z", false, "timeout"))
        assertEquals(listOf("schedule:s1:2026-11-21T20:30"), HintRules.findings(snapshot(listOf(unit()), listOf(item), final), now).filter { it.kind == HintKind.SCHEDULE }.map { it.key })
    }
}
