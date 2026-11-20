package de.klimapilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSeasonTest {
    private fun profile(mode: String?, season: String? = null) =
        ClimateProfile("p", "d", "Test", true, mode, 20.0, 0, 0, null, null, 0L, season)

    @Test
    fun withoutAssignmentFollowsMode() {
        assertEquals(ProfileSeason.WINTER, ProfileSeason.of(profile("HEAT")))
        assertEquals(ProfileSeason.SUMMER, ProfileSeason.of(profile("COOL")))
        assertEquals(ProfileSeason.ALL, ProfileSeason.of(profile("FAN")))
        assertEquals(ProfileSeason.ALL, ProfileSeason.of(profile(null)))
    }

    @Test
    fun explicitAssignmentWins() {
        assertEquals(ProfileSeason.SUMMER, ProfileSeason.of(profile("HEAT", ProfileSeason.SUMMER)))
        assertEquals(ProfileSeason.WINTER, ProfileSeason.of(profile("HEAT", "unbekannt")))
    }

    @Test
    fun allYearProfilesShowInBothSeasons() {
        assertTrue(ProfileSeason.matches(profile("DRY"), ProfileSeason.WINTER))
        assertTrue(ProfileSeason.matches(profile("DRY"), ProfileSeason.SUMMER))
        assertTrue(ProfileSeason.matches(profile("HEAT"), ProfileSeason.WINTER))
        assertFalse(ProfileSeason.matches(profile("HEAT"), ProfileSeason.SUMMER))
    }
}
