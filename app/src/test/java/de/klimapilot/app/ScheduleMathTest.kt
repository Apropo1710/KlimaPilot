package de.klimapilot.app

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleMathTest {
    // Friday, 20.11.2026, 17:30
    private val now = LocalDateTime.of(2026, 11, 20, 17, 30)
    private fun weekly(days: List<Int>, time: String, enabled: Boolean = true) =
        ClimateSchedule(id = "w", airconId = "a", enabled = enabled, kind = "weekly", days = days, time = time, action = "off")

    @Test
    fun weeklyFindsTheNextMatchingDay() {
        assertEquals(LocalDateTime.of(2026, 11, 20, 22, 0), ScheduleMath.nextRun(weekly((1..5).toList(), "22:00"), now))
        // Friday's 06:30 has passed; weekdays only -> Monday.
        assertEquals(LocalDateTime.of(2026, 11, 23, 6, 30), ScheduleMath.nextRun(weekly((1..5).toList(), "06:30"), now))
        assertEquals(LocalDateTime.of(2026, 11, 21, 9, 0), ScheduleMath.nextRun(weekly(listOf(6, 7), "09:00"), now))
        // Same weekday, earlier time -> next week.
        assertEquals(LocalDateTime.of(2026, 11, 27, 17, 0), ScheduleMath.nextRun(weekly(listOf(5), "17:00"), now))
        assertNull(ScheduleMath.nextRun(weekly(listOf(5), "22:00", enabled = false), now))
    }

    @Test
    fun onceIsGoneAfterItRan() {
        val at = now.plusHours(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val item = ClimateSchedule(id = "o", airconId = "a", kind = "once", at = at, action = "on")
        assertEquals(now.plusHours(1), ScheduleMath.nextRun(item, now))
        assertNull(ScheduleMath.nextRun(item, now, ScheduleRun("once:$at", null, true, "ok")))
    }

    @Test
    fun labels() {
        assertEquals("Täglich", ScheduleMath.daysLabel((1..7).toList()))
        assertEquals("Mo–Fr", ScheduleMath.daysLabel(listOf(5, 4, 3, 2, 1)))
        assertEquals("Sa–So", ScheduleMath.daysLabel(listOf(6, 7)))
        assertEquals("Mo, Mi, Fr", ScheduleMath.daysLabel(listOf(1, 3, 5)))
        assertEquals("heute 22:00", ScheduleMath.whenLabel(LocalDateTime.of(2026, 11, 20, 22, 0), now))
        assertEquals("morgen 06:30", ScheduleMath.whenLabel(LocalDateTime.of(2026, 11, 21, 6, 30), now))
        assertEquals("Ausschalten", ScheduleMath.actionLabel(weekly(listOf(1), "06:00")))
        val custom = weekly(listOf(1), "06:00").copy(action = "on", settings = ScheduleSettings(mode = "HEAT", targetTemp = 21.5, fanSpeed = 0))
        assertEquals("Ein · Heizen · 21,5 °C · Lüfter Auto", ScheduleMath.actionLabel(custom))
    }

    @Test
    fun profileValuesFollowTheProfile() {
        val profile = ClimateProfile("p", "d", "Schlafen", true, "HEAT", 19.0, 1, 0, null, null, 0L)
        val item = weekly(listOf(1), "22:00").copy(action = "on", profileId = "p", profileName = "Alt", settings = ScheduleSettings(mode = "COOL"))
        val updated = ScheduleMath.withProfiles(listOf(item), listOf(profile)).single()
        assertEquals("Schlafen", updated.profileName)
        assertEquals(ScheduleSettings("HEAT", 19.0, 1, 0, null, null), updated.settings)
        // Unchanged when already in step.
        assertEquals(listOf(updated), ScheduleMath.withProfiles(listOf(updated), listOf(profile)))
    }

    @Test
    fun finishedOneTimeTimersAreSwitchedOff() {
        val once = ClimateSchedule(id = "o", airconId = "a", kind = "once", at = 1000L, action = "off")
        val weekly = weekly(listOf(1), "06:00")
        val status = mapOf("o" to ScheduleRun("once:1000", null, true, "ok"), "w" to ScheduleRun("2026-11-16T06:00", null, true, "ok"))
        val result = ScheduleMath.disableFinished(listOf(once, weekly), status)
        assertEquals(false, result[0].enabled)
        assertEquals(true, result[1].enabled)
        // Not yet run, or failed: stays on.
        assertEquals(true, ScheduleMath.disableFinished(listOf(once), mapOf("o" to ScheduleRun("once:1000", null, false, "Zeitüberschreitung"))).single().enabled)
    }

    @Test
    fun quickTimersAreRemovedOnceRunOrExpired() {
        val now = 10_000_000_000L
        val ran = ClimateSchedule("q1", "a", kind = "once", at = now - 60_000, action = "off", quick = true)
        val pending = ClimateSchedule("q2", "a", kind = "once", at = now + 60_000, action = "off", quick = true)
        val expired = ClimateSchedule("q3", "a", kind = "once", at = now - 8 * 3600_000L, action = "off", quick = true)
        val status = mapOf("q1" to ScheduleRun("once:${now - 60_000}", null, true, "ok"))
        assertEquals(listOf("q2"), ScheduleMath.disableFinished(listOf(ran, pending, expired), status, now).map { it.id })
    }

    @Test
    fun everyThirdWeekRunsOnlyInItsWeeks() {
        val item = ClimateSchedule("r", "a", kind = "weekly", days = listOf(1, 2, 3, 4, 5), time = "10:00", action = "on", everyWeeks = 3, weekAnchor = "2026-11-30")
        assertEquals(false, ScheduleMath.activeInWeek(item, java.time.LocalDate.of(2026, 11, 23)))
        assertEquals(true, ScheduleMath.activeInWeek(item, java.time.LocalDate.of(2026, 12, 6)))
        assertEquals(false, ScheduleMath.activeInWeek(item, java.time.LocalDate.of(2026, 12, 7)))
        assertEquals(true, ScheduleMath.activeInWeek(item, java.time.LocalDate.of(2026, 12, 21)))
        assertEquals(true, ScheduleMath.activeInWeek(item, java.time.LocalDate.of(2026, 11, 9)))
        // Next run from Monday 23.11. is Monday 30.11., not earlier.
        val next = ScheduleMath.nextRun(item, java.time.LocalDateTime.of(2026, 11, 23, 12, 0))
        assertEquals(java.time.LocalDateTime.of(2026, 11, 30, 10, 0), next)
    }

    @Test
    fun sameTimeConflictOnlyForExactlyTheSameMoment() {
        fun w(id: String, days: List<Int>, time: String, aircon: String = "a", enabled: Boolean = true, every: Int = 1, anchor: String? = null) =
            ClimateSchedule(id = id, airconId = aircon, enabled = enabled, kind = "weekly", days = days, time = time, action = "on",
                everyWeeks = every, weekAnchor = anchor)
        val night = w("n", (1..5).toList(), "22:00")
        // Same time, shared weekday -> conflict.
        assertEquals("n", ScheduleMath.sameTimeConflict(w("x", listOf(5, 6), "22:00"), listOf(night))?.id)
        // Different minute, other days, other air conditioner or switched off -> no conflict.
        assertNull(ScheduleMath.sameTimeConflict(w("x", listOf(1), "22:01"), listOf(night)))
        assertNull(ScheduleMath.sameTimeConflict(w("x", listOf(6, 7), "22:00"), listOf(night)))
        assertNull(ScheduleMath.sameTimeConflict(w("x", listOf(1), "22:00", aircon = "b"), listOf(night)))
        assertNull(ScheduleMath.sameTimeConflict(w("x", listOf(1), "22:00"), listOf(night.copy(enabled = false))))
        // Every 2 weeks in alternating weeks never meet; same week does.
        val evenWeek = w("e", listOf(1), "06:00", every = 2, anchor = "2026-11-23")
        assertNull(ScheduleMath.sameTimeConflict(w("x", listOf(1), "06:00", every = 2, anchor = "2026-11-30"), listOf(evenWeek)))
        assertEquals("e", ScheduleMath.sameTimeConflict(w("x", listOf(1), "06:00", every = 2, anchor = "2026-12-07"), listOf(evenWeek))?.id)
        // A one-time schedule on a Friday 22:00 meets the weekday schedule.
        val at = LocalDateTime.of(2026, 11, 27, 22, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val once = ClimateSchedule(id = "o", airconId = "a", kind = "once", at = at, action = "on")
        assertEquals("n", ScheduleMath.sameTimeConflict(once, listOf(night))?.id)
    }
}
