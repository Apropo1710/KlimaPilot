package de.klimapilot.app

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/**
 * Summer/winter view of the climate profiles. A profile is assigned to winter, summer or both
 * ("all"); without an explicit assignment it follows its mode (heating = winter, cooling = summer).
 * The selected season applies to the whole household and travels with the phone-to-phone sync.
 */
internal object ProfileSeason {
    const val WINTER = "winter"
    const val SUMMER = "summer"
    const val ALL = "all"
    val VALUES = setOf(WINTER, SUMMER, ALL)

    private const val PREF = "profile_season"
    private const val PREF_AT = "profile_season_updated_at"

    fun fromMode(mode: String?): String = when (mode) {
        "HEAT" -> WINTER
        "COOL" -> SUMMER
        else -> ALL
    }

    fun of(profile: ClimateProfile): String = profile.season?.takeIf { it in VALUES } ?: fromMode(profile.mode)

    /** Whether the profile is offered while [season] is selected. */
    fun matches(profile: ClimateProfile, season: String): Boolean = of(profile).let { it == ALL || it == season }

    private fun prefs(context: Context) = context.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE)

    /** Selected season; before the first choice a sensible default from the calendar (Oct–Apr = winter). */
    fun current(context: Context): String = prefs(context).getString(PREF, null)?.takeIf { it == WINTER || it == SUMMER }
        ?: if (LocalDate.now().monthValue in 5..9) SUMMER else WINTER

    fun set(context: Context, season: String, changedAt: Long = System.currentTimeMillis()) {
        require(season == WINTER || season == SUMMER)
        prefs(context).edit().putString(PREF, season).putLong(PREF_AT, changedAt).apply()
    }

    fun peerJson(context: Context): JSONObject? {
        val at = prefs(context).getLong(PREF_AT, 0L).takeIf { it > 0L } ?: return null
        return JSONObject().put("season", current(context)).put("updatedAt", at)
    }

    /** Takes the other phone's choice if it is newer than ours. */
    fun applyPeer(context: Context, remote: JSONObject): Boolean {
        val at = remote.optLong("updatedAt", 0L)
        val season = remote.optString("season")
        if (at <= prefs(context).getLong(PREF_AT, 0L) || (season != WINTER && season != SUMMER)) return false
        set(context, season, at)
        return true
    }
}
