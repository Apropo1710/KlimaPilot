package de.klimapilot.app

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/**
 * Household season: winter, transition (spring/autumn) or summer. It decides which schedules are
 * shown and carried out (a schedule lists one or more seasons) and which climate profiles are
 * offered. A profile is assigned to winter, transition, summer or all year ("all"); without an
 * explicit assignment it follows its mode (heating = winter, cooling = summer). In the transition
 * season every profile is offered, since both heating and cooling happen then.
 *
 * Chosen by the admin in the settings. Stored in ioBroker (schedules.config, read by the script)
 * and cached per phone; the phone-to-phone sync carries it when ioBroker is out of reach. The
 * newer change wins (`updatedAt`).
 */
internal object ProfileSeason {
    const val WINTER = "winter"
    const val SUMMER = "summer"
    const val TRANSITION = "transition"
    const val ALL = "all"
    val VALUES = setOf(WINTER, TRANSITION, SUMMER, ALL)
    /** Values the household season itself can take (no "all"). */
    val HOUSEHOLD = listOf(WINTER, TRANSITION, SUMMER)

    /** "für …" wording: den Winter, die Übergangszeit, den Sommer. */
    fun phrase(season: String): String = when (season) {
        WINTER -> "den Winter"
        TRANSITION -> "die Übergangszeit"
        SUMMER -> "den Sommer"
        else -> "das ganze Jahr"
    }

    fun label(season: String): String = when (season) {
        WINTER -> "Winter"
        TRANSITION -> "Übergang"
        SUMMER -> "Sommer"
        else -> "Ganzjährig"
    }

    private const val PREF = "profile_season"
    private const val PREF_AT = "profile_season_updated_at"

    fun fromMode(mode: String?): String = when (mode) {
        "HEAT" -> WINTER
        "COOL" -> SUMMER
        else -> ALL
    }

    fun of(profile: ClimateProfile): String = profile.season?.takeIf { it in VALUES } ?: fromMode(profile.mode)

    /** Whether the profile is offered while [season] is selected. */
    fun matches(profile: ClimateProfile, season: String): Boolean =
        season == TRANSITION || of(profile).let { it == ALL || it == season }

    private fun prefs(context: Context) = context.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE)

    /** Selected season; before the first choice a sensible default from the calendar (Oct–Apr = winter). */
    fun current(context: Context): String = prefs(context).getString(PREF, null)?.takeIf { it in HOUSEHOLD }
        ?: calendarDefault()

    /** Same rule as the ioBroker script uses when no season was ever chosen. */
    fun calendarDefault(date: LocalDate = LocalDate.now()): String = if (date.monthValue in 5..9) SUMMER else WINTER

    fun updatedAt(context: Context): Long = prefs(context).getLong(PREF_AT, 0L)

    fun set(context: Context, season: String, changedAt: Long = System.currentTimeMillis()) {
        require(season in HOUSEHOLD)
        prefs(context).edit().putString(PREF, season).putLong(PREF_AT, changedAt).apply()
    }

    /**
     * Brings this phone and ioBroker together after reading schedules.config: a newer season from
     * ioBroker is taken over (returns true); a newer local choice is returned as [push] so the
     * caller writes it to ioBroker.
     */
    fun reconcile(context: Context, remoteSeason: String?, remoteAt: Long): Reconcile {
        val localAt = updatedAt(context)
        return when {
            remoteSeason in HOUSEHOLD && remoteAt > localAt -> {
                val changed = remoteSeason != current(context)
                set(context, remoteSeason!!, remoteAt)
                Reconcile(adopted = changed, push = null)
            }
            localAt > remoteAt || (localAt > 0L && remoteSeason !in HOUSEHOLD) -> Reconcile(adopted = false, push = current(context) to localAt)
            else -> Reconcile(adopted = false, push = null)
        }
    }

    data class Reconcile(val adopted: Boolean, val push: Pair<String, Long>?)

    fun peerJson(context: Context): JSONObject? {
        val at = prefs(context).getLong(PREF_AT, 0L).takeIf { it > 0L } ?: return null
        return JSONObject().put("season", current(context)).put("updatedAt", at)
    }

    /** Takes the other phone's choice if it is newer than ours. */
    fun applyPeer(context: Context, remote: JSONObject): Boolean {
        val at = remote.optLong("updatedAt", 0L)
        val season = remote.optString("season")
        if (at <= prefs(context).getLong(PREF_AT, 0L) || season !in HOUSEHOLD) return false
        set(context, season, at)
        return true
    }
}
