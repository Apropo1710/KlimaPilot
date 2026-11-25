package de.klimapilot.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScheduleSeasonTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        context.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun item(seasons: List<String>? = null, quick: Boolean = false) = JSONObject()
        .put("id", "x").put("airconId", "a").put("kind", "weekly").put("time", "06:00").put("action", "on")
        .apply { if (quick) put("quick", true) }
        .apply { seasons?.let { put("seasons", org.json.JSONArray(it)) } }

    @Test
    fun schedulesFromBeforeSeasonsCountAsWinter() {
        val parsed = ScheduleJson.itemFromJson(item())!!
        assertEquals(listOf(ProfileSeason.WINTER), parsed.seasons)
        assertTrue(ScheduleMath.inSeason(parsed, ProfileSeason.WINTER))
        assertFalse(ScheduleMath.inSeason(parsed, ProfileSeason.SUMMER))
        assertFalse(ScheduleMath.inSeason(parsed, ProfileSeason.TRANSITION))
    }

    @Test
    fun severalSeasonsRoundTrip() {
        val parsed = ScheduleJson.itemFromJson(item(listOf("summer", "transition", "unbekannt")))!!
        assertEquals(listOf("summer", "transition"), parsed.seasons)
        assertTrue(ScheduleMath.inSeason(parsed, ProfileSeason.TRANSITION))
        assertFalse(ScheduleMath.inSeason(parsed, ProfileSeason.WINTER))
        val again = ScheduleJson.itemFromJson(ScheduleJson.itemToJson(parsed))!!
        assertEquals(parsed.seasons, again.seasons)
    }

    @Test
    fun quickTimersRunInEverySeason() {
        val parsed = ScheduleJson.itemFromJson(item(quick = true))!!
        ProfileSeason.HOUSEHOLD.forEach { assertTrue(ScheduleMath.inSeason(parsed, it)) }
        assertFalse(ScheduleJson.itemToJson(parsed).has("seasons"))
    }

    @Test
    fun configCarriesTheHouseholdSeason() {
        val json = ScheduleJson.configToJson(true, emptyList(), 3, ProfileSeason.TRANSITION, 1234L)
        assertEquals(ProfileSeason.TRANSITION to 1234L, ScheduleJson.seasonFromJson(json))
        val none = ScheduleJson.configToJson(true, emptyList(), 3)
        assertFalse(none.has("season"))
        assertNull(ScheduleJson.seasonFromJson(none).first)
    }

    @Test
    fun newerSeasonFromIoBrokerIsTakenOver() {
        ProfileSeason.set(context, ProfileSeason.WINTER, 100L)
        val result = ProfileSeason.reconcile(context, ProfileSeason.SUMMER, 200L)
        assertTrue(result.adopted)
        assertNull(result.push)
        assertEquals(ProfileSeason.SUMMER, ProfileSeason.current(context))
    }

    @Test
    fun newerLocalSeasonIsPushedToIoBroker() {
        ProfileSeason.set(context, ProfileSeason.TRANSITION, 300L)
        val result = ProfileSeason.reconcile(context, ProfileSeason.WINTER, 200L)
        assertFalse(result.adopted)
        assertEquals(ProfileSeason.TRANSITION to 300L, result.push)
        val missing = ProfileSeason.reconcile(context, null, 0L)
        assertEquals(ProfileSeason.TRANSITION to 300L, missing.push)
    }

    @Test
    fun nothingChosenAnywhereChangesNothing() {
        val result = ProfileSeason.reconcile(context, null, 0L)
        assertFalse(result.adopted)
        assertNull(result.push)
    }
}
