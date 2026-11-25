package de.klimapilot.app

import android.content.Context
import de.klimapilot.app.wfrac.AirconState
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Event journal of this phone: what happened in the last 48 hours, always on and kept only on the
 * phone. Records app open/close, every command sent to a unit (who triggered it, what changed,
 * result, lock time), changes seen at a unit that did not come from this phone (remote control,
 * other phone, schedule) and data taken over by the synchronisation. Meant for tracing afterwards
 * what really happened ("Kopieren" in the diagnostics card).
 */
internal object EventJournal {
    private const val FILE = "event_journal.jsonl"
    private const val KEEP_MS = 48L * 3600 * 1000
    /** The file is cut back to the last 48 h once it grows past this size. */
    private const val PRUNE_BYTES = 400_000L
    private var file: File? = null

    data class Entry(val at: Long, val category: String, val subject: String, val text: String) {
        fun line(): String {
            val time = DateTimeFormatter.ofPattern("dd.MM. HH:mm:ss").format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))
            return "$time  $category" + (if (subject.isNotBlank()) " · $subject" else "") + ": $text"
        }
    }

    fun init(context: Context) {
        if (file == null) file = File(context.applicationContext.filesDir, FILE)
        prune()
    }

    @Synchronized
    fun log(category: String, subject: String, text: String) {
        val target = file ?: return
        runCatching {
            val line = JSONObject().put("at", System.currentTimeMillis()).put("c", category).put("s", subject).put("t", text).toString()
            target.appendText(line + "\n")
            if (target.length() > PRUNE_BYTES) prune()
        }
    }

    /** Newest first, only the last 48 hours. */
    @Synchronized
    fun entries(): List<Entry> {
        val target = file ?: return emptyList()
        if (!target.exists()) return emptyList()
        val since = System.currentTimeMillis() - KEEP_MS
        return runCatching {
            target.readLines().mapNotNull { raw ->
                runCatching { JSONObject(raw) }.getOrNull()?.let { Entry(it.optLong("at"), it.optString("c"), it.optString("s"), it.optString("t")) }
            }.filter { it.at >= since }.reversed()
        }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun prune() {
        val target = file ?: return
        if (!target.exists()) return
        val kept = entries().reversed()
        runCatching {
            target.writeText(kept.joinToString("") { e ->
                JSONObject().put("at", e.at).put("c", e.category).put("s", e.subject).put("t", e.text).toString() + "\n"
            })
        }
    }

    @Synchronized
    fun clear() { runCatching { file?.delete() } }

    /** Readable difference of the values a user can set, e.g. "Soll 20,5 → 21,0 °C, Lüfter Auto → Hoch". */
    fun diff(before: AirconState?, after: AirconState?): String {
        if (after == null) return ""
        if (before == null) return describe(after)
        val parts = mutableListOf<String>()
        if (before.power != after.power) parts += if (after.power) "eingeschaltet" else "ausgeschaltet"
        if (before.mode != after.mode) parts += "Modus ${modeName(before)} → ${modeName(after)}"
        if (before.presetTemp != after.presetTemp) parts += "Soll ${temp(before.presetTemp)} → ${temp(after.presetTemp)} °C"
        if (before.airFlow != after.airFlow) parts += "Lüfter ${fan(before.airFlow)} → ${fan(after.airFlow)}"
        if (before.windUD != after.windUD) parts += "Lamelle oben/unten ${before.windUD} → ${after.windUD}"
        if (before.windLR != after.windLR) parts += "Lamelle links/rechts ${before.windLR} → ${after.windLR}"
        if (before.entrust != after.entrust) parts += "3D-Auto ${if (after.entrust) "an" else "aus"}"
        return parts.joinToString(", ")
    }

    fun describe(state: AirconState): String =
        (if (state.power) "an" else "aus") + ", ${modeName(state)}, Soll ${temp(state.presetTemp)} °C, Lüfter ${fan(state.airFlow)}"

    private fun temp(value: Double) = String.format(Locale.GERMANY, "%.1f", value)
    private fun modeName(state: AirconState) = when (state.mode?.name) {
        "HEAT" -> "Heizen"; "COOL" -> "Kühlen"; "DRY" -> "Trocknen"; "FAN" -> "Lüfter"; "AUTO" -> "Auto"; else -> "?"
    }
    private fun fan(value: Int) = when (value) { 0 -> "Auto"; 1 -> "Leise"; 2 -> "Niedrig"; 3 -> "Mittel"; 4 -> "Hoch"; else -> value.toString() }
}

/**
 * Who triggers the next command to a unit. Set right before the command is started (profile
 * switch, automatic take-over of a changed profile); read and reset when the command starts.
 * Without a value the command comes from operating the app directly.
 */
internal object CommandSource {
    private var pending: String? = null
    fun set(source: String) { pending = source }
    fun take(): String = (pending ?: "Bedienung").also { pending = null }
}
