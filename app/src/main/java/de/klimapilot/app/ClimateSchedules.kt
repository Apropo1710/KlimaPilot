package de.klimapilot.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.klimapilot.app.wfrac.AirconState
import de.klimapilot.app.wfrac.Codec
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

// -------------------------------------------------------------------------------------------
// Model. The same JSON is read by the ioBroker script (scripts/iobroker/klimapilot.js),
// which carries the schedules out, so the app need not be running.
// -------------------------------------------------------------------------------------------

internal data class ScheduleSettings(
    val mode: String? = null,
    val targetTemp: Double? = null,
    val fanSpeed: Int? = null,
    val verticalSwing: Int? = null,
    val horizontalSwing: Int? = null,
    val threeDAuto: Boolean? = null,
)

internal data class ClimateSchedule(
    val id: String,
    val airconId: String,
    val enabled: Boolean = true,
    /** "weekly" or "once". */
    val kind: String,
    /** ISO weekdays, Monday = 1 … Sunday = 7 (weekly). */
    val days: List<Int> = emptyList(),
    /** "HH:MM" (weekly). */
    val time: String = "",
    /** Epoch millis (once). */
    val at: Long? = null,
    /** "on" or "off". */
    val action: String,
    val profileId: String? = null,
    val profileName: String? = null,
    val settings: ScheduleSettings? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    /** Quick timer "off in …" from the schedules card: not listed, removed once it has run. */
    val quick: Boolean = false,
)

internal data class ScheduleRun(val key: String, val at: String?, val done: Boolean, val result: String?)

internal data class ScheduleData(
    val enabled: Boolean,
    val items: List<ClimateSchedule>,
    val status: Map<String, ScheduleRun>,
    /** Counts every write of schedules.config; guards against two phones writing at once. */
    val revision: Long = 0L,
)

internal object ScheduleJson {
    fun settingsToJson(s: ScheduleSettings) = JSONObject().apply {
        s.mode?.let { put("mode", it) }
        s.targetTemp?.let { put("targetTemp", it) }
        s.fanSpeed?.let { put("fanSpeed", it) }
        s.verticalSwing?.let { put("verticalSwing", it) }
        s.horizontalSwing?.let { put("horizontalSwing", it) }
        s.threeDAuto?.let { put("threeDAuto", it) }
    }

    private fun settingsFromJson(o: JSONObject) = ScheduleSettings(
        mode = o.optString("mode").ifBlank { null },
        targetTemp = o.optDouble("targetTemp", Double.NaN).takeIf { !it.isNaN() },
        fanSpeed = if (o.has("fanSpeed")) o.optInt("fanSpeed") else null,
        verticalSwing = if (o.has("verticalSwing")) o.optInt("verticalSwing") else null,
        horizontalSwing = if (o.has("horizontalSwing")) o.optInt("horizontalSwing") else null,
        threeDAuto = if (o.has("threeDAuto")) o.optBoolean("threeDAuto") else null,
    )

    fun itemToJson(i: ClimateSchedule) = JSONObject().apply {
        put("id", i.id); put("airconId", i.airconId); put("enabled", i.enabled); put("kind", i.kind)
        put("days", JSONArray(i.days)); put("time", i.time)
        i.at?.let { put("at", it) }
        put("action", i.action)
        i.profileId?.let { put("profileId", it) }
        i.profileName?.let { put("profileName", it) }
        i.settings?.let { put("settings", settingsToJson(it)) }
        put("updatedAt", i.updatedAt)
        if (i.quick) put("quick", true)
    }

    fun itemFromJson(o: JSONObject): ClimateSchedule? {
        val id = o.optString("id").ifBlank { return null }
        val days = o.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty()
        return ClimateSchedule(
            id = id,
            airconId = o.optString("airconId"),
            enabled = o.optBoolean("enabled", true),
            kind = o.optString("kind", "weekly"),
            days = days,
            time = o.optString("time"),
            at = if (o.has("at")) o.optLong("at") else null,
            action = o.optString("action", "on"),
            profileId = o.optString("profileId").ifBlank { null },
            profileName = o.optString("profileName").ifBlank { null },
            settings = o.optJSONObject("settings")?.let { settingsFromJson(it) },
            updatedAt = o.optLong("updatedAt", 0L),
            quick = o.optBoolean("quick", false),
        )
    }

    fun configToJson(enabled: Boolean, items: List<ClimateSchedule>, revision: Long = 0L) = JSONObject()
        .put("enabled", enabled)
        .put("revision", revision)
        .put("items", JSONArray().apply { items.forEach { put(itemToJson(it)) } })

    fun configFromJson(o: JSONObject?): Pair<Boolean, List<ClimateSchedule>> {
        if (o == null) return true to emptyList()
        val items = o.optJSONArray("items")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::itemFromJson) } }.orEmpty()
        return o.optBoolean("enabled", true) to items
    }

    fun statusFromJson(o: JSONObject?): Map<String, ScheduleRun> {
        if (o == null) return emptyMap()
        return o.keys().asSequence().mapNotNull { id ->
            val e = o.optJSONObject(id) ?: return@mapNotNull null
            id to ScheduleRun(e.optString("key"), e.optString("at").ifBlank { null }, e.optBoolean("done"), e.optString("result").ifBlank { null })
        }.toMap()
    }
}

internal object ScheduleMath {
    private val weekdayShort = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    private val modeLabels = mapOf("AUTO" to "Auto", "COOL" to "Kühlen", "HEAT" to "Heizen", "FAN" to "Lüfter", "DRY" to "Trocknen")
    val fanLabels = listOf("Auto", "Leise", "Niedrig", "Mittel", "Hoch")

    fun parseTime(time: String): LocalTime? = runCatching { LocalTime.parse(time) }.getOrNull()

    fun daysLabel(days: List<Int>): String {
        val sorted = days.distinct().sorted()
        return when (sorted) {
            (1..7).toList() -> "Täglich"
            (1..5).toList() -> "Mo–Fr"
            listOf(6, 7) -> "Sa–So"
            else -> sorted.joinToString(", ") { weekdayShort.getOrElse(it - 1) { "?" } }
        }
    }

    /** Next time this schedule fires, or null if it never will again. */
    fun nextRun(item: ClimateSchedule, now: LocalDateTime, run: ScheduleRun? = null): LocalDateTime? {
        if (!item.enabled) return null
        return when (item.kind) {
            "weekly" -> {
                val time = parseTime(item.time) ?: return null
                if (item.days.isEmpty()) return null
                (0..7).asSequence().map { now.toLocalDate().plusDays(it.toLong()).atTime(time) }
                    .firstOrNull { it.isAfter(now) && it.dayOfWeek.value in item.days }
            }
            "once" -> {
                val at = item.at ?: return null
                if (run?.done == true && run.key == "once:$at") return null
                val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault())
                if (dt.isBefore(now.minusHours(6))) null else dt
            }
            else -> null
        }
    }

    fun whenLabel(dt: LocalDateTime, now: LocalDateTime): String {
        val day = when (dt.toLocalDate()) {
            now.toLocalDate() -> "heute"
            now.toLocalDate().plusDays(1) -> "morgen"
            else -> "${weekdayShort[dt.dayOfWeek.value - 1]}, %02d.%02d.".format(Locale.ROOT, dt.dayOfMonth, dt.monthValue)
        }
        return "$day %02d:%02d".format(Locale.ROOT, dt.hour, dt.minute)
    }

    fun scheduleLabel(item: ClimateSchedule, now: LocalDateTime): String = when (item.kind) {
        "weekly" -> "${daysLabel(item.days)} · ${item.time}"
        else -> item.at?.let { "Einmalig · " + whenLabel(LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()), now) } ?: "Einmalig"
    }

    fun actionLabel(item: ClimateSchedule): String {
        if (item.action == "off") return "Ausschalten"
        item.profileName?.let { return "Ein · Profil „$it“" }
        val s = item.settings ?: return "Einschalten"
        return (listOf("Ein") + settingsLabel(s).split(" · ").filter { it.isNotBlank() }).joinToString(" · ")
    }

    /** Mode, temperature and fan in one line (no on/off: a schedule decides that itself). */
    fun settingsLabel(s: ScheduleSettings): String = buildList {
        s.mode?.let { add(modeLabels[it] ?: it) }
        s.targetTemp?.let { add("%.1f °C".format(Locale.GERMANY, it)) }
        s.fanSpeed?.let { add("Lüfter " + fanLabels.getOrElse(it) { "?" }) }
    }.joinToString(" · ")

    fun profileSettings(p: ClimateProfile) = ScheduleSettings(p.mode, p.targetTemp, p.fanSpeed, p.verticalSwing, p.horizontalSwing, p.threeDAuto)

    /** A one-time timer that has run is switched off; it stays listed until it is deleted. */
    fun disableFinished(items: List<ClimateSchedule>, status: Map<String, ScheduleRun>, now: Long = System.currentTimeMillis()): List<ClimateSchedule> = items.mapNotNull { item ->
        val run = status[item.id]
        val ran = item.kind == "once" && item.at != null && run?.done == true && run.key == "once:${item.at}"
        when {
            // Quick timers disappear once they have run, or once they are too old to be caught up.
            item.quick && (ran || (item.at != null && item.at < now - QUICK_EXPIRY_MS)) -> null
            ran && item.enabled -> item.copy(enabled = false, updatedAt = now)
            else -> item
        }
    }

    private const val QUICK_EXPIRY_MS = 7L * 3600 * 1000

    /** Keeps the copied profile values in step with the profiles, so the script applies the current ones. */
    fun withProfiles(items: List<ClimateSchedule>, profiles: List<ClimateProfile>): List<ClimateSchedule> = items.map { item ->
        val profile = item.profileId?.let { id -> profiles.firstOrNull { it.id == id } } ?: return@map item
        val settings = profileSettings(profile)
        if (item.settings == settings && item.profileName == profile.name) item
        else item.copy(settings = settings, profileName = profile.name)
    }
}

// -------------------------------------------------------------------------------------------
// ioBroker access (own short-lived connection, like the energy view).
// -------------------------------------------------------------------------------------------

internal class ScheduleIoBroker {
    private val main = Handler(Looper.getMainLooper())

    private fun session(address: String, callback: (ScheduleData?, String?) -> Unit, block: (IoBrokerLease, (ScheduleData?, String?) -> Unit) -> Unit) {
        val url = address.trim().trimEnd('/')
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) { callback(null, "Keine ioBroker-Adresse hinterlegt."); return }
        val socket = IoBrokerLease(url)
        val done = AtomicBoolean(false)
        val finish: (ScheduleData?, String?) -> Unit = { data, error ->
            if (done.compareAndSet(false, true)) {
                runCatching { socket.off() }
                runCatching { socket.disconnect() }
                main.post { callback(data, error) }
            }
        }
        main.postDelayed({ finish(null, "ioBroker antwortet nicht.") }, 15_000)
        socket.on("connect") {
            socket.emit("authenticate", IoBrokerAck { args ->
                if (args.firstOrNull() as? Boolean != true) finish(null, "ioBroker hat die Verbindung nicht authentifiziert.")
                else block(socket, finish)
            })
        }
        socket.on("connect_error") { args -> finish(null, "ioBroker nicht erreichbar: ${args.firstOrNull() ?: "Verbindung fehlgeschlagen"}") }
        socket.connect()
    }

    private fun readJson(socket: IoBrokerLease, id: String, next: (JSONObject?) -> Unit) {
        socket.emit("getState", id, IoBrokerAck { args ->
            val raw = (args.getOrNull(1) as? JSONObject)?.opt("val") as? String
            next(raw?.let { runCatching { JSONObject(it) }.getOrNull() })
        })
    }

    private fun read(socket: IoBrokerLease, next: (ScheduleData) -> Unit) {
        readJson(socket, CONFIG_ID) { config ->
            readJson(socket, STATUS_ID) { status ->
                val (enabled, items) = ScheduleJson.configFromJson(config)
                next(ScheduleData(enabled, items, ScheduleJson.statusFromJson(status), config?.optLong("revision", 0L) ?: 0L))
            }
        }
    }

    fun load(address: String, callback: (ScheduleData?, String?) -> Unit) = session(address, callback) { socket, finish ->
        read(socket) { finish(it, null) }
    }

    /** Reads the current schedules, applies [change] and writes them back only if something changed. */
    /**
     * Read – change – write. Right before writing, the revision is checked again; if the other
     * phone wrote in between, the change is applied once more to its fresh state (up to 3 times).
     */
    fun update(address: String, change: (Boolean, List<ClimateSchedule>) -> Pair<Boolean, List<ClimateSchedule>>, callback: (ScheduleData?, String?) -> Unit) =
        session(address, callback) { socket, finish ->
            fun attempt(round: Int) {
                read(socket) { current ->
                    val (enabled, items) = change(current.enabled, current.items)
                    if (enabled == current.enabled && items == current.items) { finish(current, null); return@read }
                    readJson(socket, CONFIG_ID) { latest ->
                        val latestRevision = latest?.optLong("revision", 0L) ?: 0L
                        if (latestRevision != current.revision && round < 3) { attempt(round + 1); return@readJson }
                        val revision = latestRevision + 1
                        val definition = JSONObject().put("type", "state")
                            .put("common", JSONObject().put("name", "KlimaPilot – Zeitpläne (werden von KlimaPilot geschrieben)").put("type", "string").put("role", "json").put("read", true).put("write", true))
                            .put("native", JSONObject())
                        socket.emit("setObject", CONFIG_ID, definition, IoBrokerAck { objectArgs ->
                            if (objectArgs.firstOrNull()?.takeUnless { it == JSONObject.NULL } != null) { finish(null, "Zeitpläne konnten nicht gespeichert werden."); return@IoBrokerAck }
                            val value = ScheduleJson.configToJson(enabled, items, revision).toString()
                            socket.emit("setState", CONFIG_ID, JSONObject().put("val", value).put("ack", false), IoBrokerAck { stateArgs ->
                                if (stateArgs.firstOrNull()?.takeUnless { it == JSONObject.NULL } != null) finish(null, "Zeitpläne konnten nicht gespeichert werden.")
                                else finish(ScheduleData(enabled, items, current.status, revision), null)
                            })
                        })
                    }
                }
            }
            attempt(1)
        }

    companion object {
        const val ROOT = "0_userdata.0.KlimaPilot.schedules"
        const val CONFIG_ID = "$ROOT.config"
        const val STATUS_ID = "$ROOT.status"
        private const val PREF_VISIBLE = "schedules_visible"
        private const val PREF_VISIBLE_AT = "schedules_visible_updated_at"

        private fun prefs(context: Context) = context.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE)
        fun visible(context: Context) = prefs(context).getBoolean(PREF_VISIBLE, true)

        /** Switched by the user; carried to the other phone by the phone-to-phone sync. */
        fun setVisible(context: Context, value: Boolean, changedAt: Long = System.currentTimeMillis()) =
            prefs(context).edit().putBoolean(PREF_VISIBLE, value).putLong(PREF_VISIBLE_AT, changedAt).apply()

        /** Snapshot part for the phone-to-phone sync, or null if never switched on this phone. */
        fun peerJson(context: Context): JSONObject? {
            val at = prefs(context).getLong(PREF_VISIBLE_AT, 0L).takeIf { it > 0L } ?: return null
            return JSONObject().put("visible", visible(context)).put("updatedAt", at)
        }

        /** Takes the other phone's switch if it was changed later than ours. */
        fun applyPeer(context: Context, remote: JSONObject): Boolean {
            val at = remote.optLong("updatedAt", 0L)
            if (at <= prefs(context).getLong(PREF_VISIBLE_AT, 0L) || !remote.has("visible")) return false
            setVisible(context, remote.optBoolean("visible", true), at)
            return true
        }
    }
}

// -------------------------------------------------------------------------------------------
// UI
// -------------------------------------------------------------------------------------------

internal val SBlue = Color(0xFF1769AA)
internal val SLight = Color(0xFFE3EEF7)
internal val SText = Color(0xFF172334)
internal val SMuted = Color(0xFF63748A)
internal val SDark = Color(0xFF123B60)

@Composable
private fun SectionLabel(text: String) = Text(text.uppercase(), color = SMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)

/** The schedule tile of one unit on the main page. Hidden when schedules are switched off. */
@Composable
internal fun ScheduleSection(device: UserDevice?, currentState: AirconState?) {
    if (device == null || device.demoMode || device.wfRacAirconId.isBlank()) return
    val context = LocalContext.current
    // Read so a switch arriving from the other phone takes effect right away.
    @Suppress("UNUSED_VARIABLE") val peerRevision = PeerSyncSignals.dataRevision
    if (!ScheduleIoBroker.visible(context)) return
    val prefs = remember { context.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE) }
    val address = prefs.getString("iobroker_server", null).orEmpty()
    val io = remember { ScheduleIoBroker() }
    var data by remember { mutableStateOf<ScheduleData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<ClimateSchedule?>(null) }
    var creating by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    fun loadProfiles(): List<ClimateProfile> =
        runCatching { ClimateProfileRepository(context).load() }.getOrDefault(emptyList()).filter { it.deviceId == device.id }
    var profiles by remember(device.id) { mutableStateOf(loadProfiles()) }

    LaunchedEffect(device.id, refresh) {
        while (true) {
            profiles = loadProfiles()
            val fresh = profiles
            io.load(address) { result, err ->
                if (result == null) { error = err; return@load }
                data = result; error = null
                // Hand changed profile values to ioBroker, and switch off one-time timers that have run.
                fun tidy(items: List<ClimateSchedule>, status: Map<String, ScheduleRun>) =
                    ScheduleMath.disableFinished(ScheduleMath.withProfiles(items, fresh), status)
                if (tidy(result.items, result.status) != result.items) {
                    io.update(address, { enabled, items -> enabled to tidy(items, result.status) }) { updated, _ ->
                        if (updated != null) data = updated
                    }
                }
            }
            delay(300_000)
        }
    }

    fun change(onDone: (Boolean) -> Unit = {}, transform: (List<ClimateSchedule>) -> List<ClimateSchedule>) {
        busy = true
        profiles = loadProfiles()
        val fresh = profiles
        io.update(address, { enabled, items -> enabled to ScheduleMath.withProfiles(transform(items), fresh) }) { result, err ->
            busy = false
            if (result != null) { data = result; error = null } else error = err
            onDone(result != null)
        }
    }

    // Saving an edited schedule: if the other phone changed or deleted it since the editor was
    // opened, ask before overwriting instead of silently replacing its change.
    var conflict by remember { mutableStateOf<ClimateSchedule?>(null) }
    fun saveItem(saved: ClimateSchedule, openedAt: Long?, force: Boolean) {
        var clash = false
        change(onDone = { ok -> if (ok && clash) conflict = saved }) { list ->
            val fresh = list.firstOrNull { it.id == saved.id }
            clash = !force && openedAt != null && (fresh == null || fresh.updatedAt != openedAt)
            when {
                clash -> list
                fresh != null -> list.map { if (it.id == saved.id) saved else it }
                else -> list + saved
            }
        }
    }

    val current = data
    if (current != null && !current.enabled) return
    val now = LocalDateTime.now()
    // Fixed order as stored in ioBroker; changed by holding and dragging an entry.
    val all = current?.items.orEmpty().filter { it.airconId == device.wfRacAirconId }
    val quickTimer = all.firstOrNull { it.quick && it.enabled && (it.at ?: 0L) > System.currentTimeMillis() - 60_000L }
    val mine = all.filterNot { it.quick }
    val baseIds = mine.map { it.id }
    var localOrder by remember { mutableStateOf<List<String>?>(null) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(baseIds) { if (dragId == null) localOrder = null }
    val shown = (localOrder ?: baseIds).mapNotNull { id -> mine.firstOrNull { it.id == id } } +
        mine.filter { localOrder != null && it.id !in localOrder!! }
    val gapPx = with(LocalDensity.current) { 10.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val currentBaseIds by rememberUpdatedState(baseIds)
    val currentBusy by rememberUpdatedState(busy)
    fun finishDrag() {
        val order = localOrder
        dragId = null
        dragOffset = 0f
        if (order == null || order == currentBaseIds) { localOrder = null; return }
        change { list ->
            val byId = list.associateBy { it.id }
            val queue = order.filter { it in byId }.toMutableList()
            list.map { if (it.id in order && queue.isNotEmpty()) byId.getValue(queue.removeAt(0)) else it }
        }
    }
    val currentFinish by rememberUpdatedState(::finishDrag)
    val next = (mine + listOfNotNull(quickTimer)).mapNotNull { item -> ScheduleMath.nextRun(item, now, current?.status?.get(item.id))?.let { item to it } }.minByOrNull { it.second }

    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("Zeitpläne")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            current == null && error != null -> "ioBroker nicht erreichbar"
                            current == null -> "Wird geladen …"
                            next != null -> "Nächster: ${ScheduleMath.whenLabel(next.second, now)} → ${ScheduleMath.actionLabel(next.first)}"
                            mine.isEmpty() -> "Noch keine Zeitpläne"
                            else -> "Kein anstehender Termin"
                        },
                        color = SText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    modifier = Modifier.size(40.dp).clip(CircleShape).clickable(enabled = current != null && !busy) { creating = true },
                    color = if (current != null) SBlue else SLight, shape = CircleShape
                ) { Box(contentAlignment = Alignment.Center) { Text("+", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) } }
            }
            // Quick timer: switch off in … without the editor; one per unit.
            if (current != null && (quickTimer != null || currentState?.power == true)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (quickTimer != null) {
                        val at = java.time.Instant.ofEpochMilli(quickTimer.at ?: 0L).atZone(java.time.ZoneId.systemDefault())
                        Text("⏱ Aus um %02d:%02d".format(at.hour, at.minute), color = SBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Chip("Abbrechen", false) { if (!busy) change { list -> list.filterNot { it.id == quickTimer.id } } }
                    } else {
                        Text("⏱ Aus in", color = SMuted, fontSize = 13.sp)
                        listOf(30 to "30 min", 60 to "1 h", 120 to "2 h", 180 to "3 h").forEach { (minutes, label) ->
                            Chip(label, false) {
                                if (busy) return@Chip
                                val airconId = device.wfRacAirconId
                                change { list ->
                                    list.filterNot { it.quick && it.airconId == airconId } + ClimateSchedule(
                                        id = java.util.UUID.randomUUID().toString(), airconId = airconId, kind = "once",
                                        at = System.currentTimeMillis() + minutes * 60_000L, action = "off", quick = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
            shown.forEach { item ->
                val run = current?.status?.get(item.id)
                val failed = run != null && !run.done && run.result != null && run.result != "ok"
                val dragging = dragId == item.id
                Surface(
                    color = if (dragging) Color.White else SLight.copy(alpha = 0.55f), shape = RoundedCornerShape(16.dp),
                    shadowElevation = if (dragging) 6.dp else 0.dp,
                    modifier = Modifier.fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .onGloballyPositioned { heights[item.id] = it.size.height }
                        .pointerInput(item.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    if (currentBusy) return@detectDragGesturesAfterLongPress
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    localOrder = localOrder ?: currentBaseIds
                                    dragId = item.id
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    if (dragId != item.id) return@detectDragGesturesAfterLongPress
                                    change.consume()
                                    dragOffset += amount.y
                                    val order = (localOrder ?: return@detectDragGesturesAfterLongPress).toMutableList()
                                    val index = order.indexOf(item.id)
                                    if (dragOffset > 0 && index < order.lastIndex) {
                                        val step = (heights[order[index + 1]] ?: 0) + gapPx
                                        if (dragOffset > step / 2f) { order.add(index + 1, order.removeAt(index)); dragOffset -= step }
                                    } else if (dragOffset < 0 && index > 0) {
                                        val step = (heights[order[index - 1]] ?: 0) + gapPx
                                        if (-dragOffset > step / 2f) { order.add(index - 1, order.removeAt(index)); dragOffset += step }
                                    }
                                    localOrder = order
                                },
                                onDragEnd = { if (dragId == item.id) currentFinish() },
                                onDragCancel = { if (dragId == item.id) currentFinish() }
                            )
                        }
                        .clip(RoundedCornerShape(16.dp)).clickable { editing = item }
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ScheduleMath.scheduleLabel(item, now), color = if (item.enabled) SText else SMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(ScheduleMath.actionLabel(item), color = SMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (failed) Text("Letzter Versuch fehlgeschlagen: ${run!!.result}", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, maxLines = 2)
                            else if (item.kind == "once" && run?.done == true) Text("Erledigt", color = SMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = item.enabled, enabled = !busy,
                            onCheckedChange = { on -> change { list -> list.map { if (it.id == item.id) it.copy(enabled = on, updatedAt = System.currentTimeMillis()) else it } } },
                            colors = blueSwitchColors()
                        )
                    }
                }
            }
            if (error != null && current != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
        }
    }

    if (creating || editing != null) {
        ScheduleEditor(
            airconId = device.wfRacAirconId,
            existing = editing,
            // Read fresh on every opening, so a profile created a moment ago is offered right away.
            profiles = remember(creating, editing) { loadProfiles() },
            currentState = currentState,
            onDismiss = { creating = false; editing = null },
            onDelete = editing?.let { e -> { change { list -> list.filterNot { it.id == e.id } }; editing = null } },
            onSave = { saved ->
                saveItem(saved, editing?.updatedAt, force = false)
                creating = false; editing = null
            }
        )
    }

    conflict?.let { pending ->
        KlimaPilotAlertDialog(
            onDismissRequest = { conflict = null },
            title = { Text("Zeitplan wurde geändert") },
            text = { Text("Dieser Zeitplan wurde inzwischen auf dem anderen Handy geändert oder gelöscht. Deine Änderung trotzdem speichern?") },
            confirmButton = { TextButton(onClick = { conflict = null; saveItem(pending, null, force = true) }) { Text("Überschreiben") } },
            dismissButton = { TextButton(onClick = { conflict = null; refresh++ }) { Text("Verwerfen") } }
        )
    }
}

internal val SPage = Color(0xFFF2F6FA)
internal val SDivider = Color(0xFFEAF0F5)
internal val SSegmentBg = Color(0xFFE4EBF2)

/** Compact segmented control: the selected segment is a raised white tab. */
@Composable
internal fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Surface(color = SSegmentBg, shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(3.dp)) {
            options.forEachIndexed { index, label ->
                val active = index == selected
                Surface(
                    color = if (active) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = if (active) 1.dp else 0.dp,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { onSelect(index) }
                ) {
                    Text(
                        label, Modifier.padding(vertical = 8.dp), textAlign = TextAlign.Center,
                        color = if (active) SText else SMuted, fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1
                    )
                }
            }
        }
    }
}

/** Small tonal chip. */
@Composable
internal fun Chip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        color = if (selected) SBlue.copy(alpha = 0.12f) else Color.Transparent,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, if (selected) SBlue.copy(alpha = 0.55f) else Color(0xFFD5DFE9)),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(
            label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = if (selected) SBlue else SText.copy(alpha = if (enabled) 0.85f else 0.35f),
            fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1
        )
    }
}

@Composable
internal fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title.uppercase(), color = SMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 6.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) { content() }
        }
    }
}

@Composable
internal fun SettingRow(label: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = SText, fontSize = 14.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Blue instead of the theme's lilac for inactive tracks and switches. */
@Composable
internal fun blueSliderColors() = androidx.compose.material3.SliderDefaults.colors(
    thumbColor = SBlue, activeTrackColor = SBlue, inactiveTrackColor = SSegmentBg,
    activeTickColor = Color.White.copy(alpha = 0.7f), inactiveTickColor = SBlue.copy(alpha = 0.45f)
)

@Composable
internal fun blueSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White, checkedTrackColor = SBlue,
    uncheckedThumbColor = Color(0xFF9AAABB), uncheckedTrackColor = SSegmentBg, uncheckedBorderColor = Color(0xFFC9D5E1)
)

/** Full-screen dialogs: navigation bar in the page colour with dark buttons, like the main screen. */
@Composable
internal fun DialogNavigationBar(color: Color) {
    val view = androidx.compose.ui.platform.LocalView.current
    val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window ?: return
    androidx.compose.runtime.SideEffect {
        @Suppress("DEPRECATION")
        window.navigationBarColor = color.toArgbCompat()
        androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
    }
}

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
)

@Composable
internal fun RowDivider() = androidx.compose.material3.HorizontalDivider(color = SDivider, thickness = 1.dp)

@Composable
internal fun MiniRound(label: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape, color = Color.Transparent, border = BorderStroke(1.dp, Color(0xFFD5DFE9)),
        modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClick)
    ) { Box(contentAlignment = Alignment.Center) { Text(label, color = SBlue, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) } }
}

@Composable
internal fun Dropdown(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(options.getOrElse(selected) { "–" }, color = SBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("  ▾", color = SBlue, fontSize = 12.sp)
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, label ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label, fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal) },
                    onClick = { onSelect(index); open = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditor(
    airconId: String,
    existing: ClimateSchedule?,
    profiles: List<ClimateProfile>,
    currentState: AirconState?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (ClimateSchedule) -> Unit,
) {
    val now = remember { LocalDateTime.now() }
    var kind by remember { mutableStateOf(existing?.kind ?: "weekly") }
    var days by remember { mutableStateOf(existing?.days?.toSet() ?: (1..5).toSet()) }
    val initialTime = existing?.let { ScheduleMath.parseTime(it.time) }
        ?: existing?.at?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()).toLocalTime() }
        ?: LocalTime.of(now.hour, 0).plusHours(1)
    var time by remember { mutableStateOf(initialTime.withSecond(0).withNano(0)) }
    var onceDay by remember {
        mutableStateOf(existing?.at?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()).toLocalDate() }
            ?: if (initialTime.isAfter(now.toLocalTime())) now.toLocalDate() else now.toLocalDate().plusDays(1))
    }
    var showTimePicker by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf(existing?.action ?: "on") }
    var useProfile by remember { mutableStateOf(existing?.profileId != null || (existing == null && profiles.isNotEmpty())) }
    var profileId by remember { mutableStateOf(existing?.profileId ?: profiles.firstOrNull()?.id) }
    val base = existing?.settings
    var mode by remember { mutableStateOf(base?.mode ?: currentState?.mode?.name ?: "HEAT") }
    var temp by remember { mutableStateOf(base?.targetTemp ?: currentState?.presetTemp ?: 21.0) }
    var fan by remember { mutableStateOf(base?.fanSpeed ?: currentState?.airFlow?.takeIf { it >= 0 } ?: 0) }
    var vertical by remember { mutableStateOf(base?.verticalSwing ?: currentState?.windUD?.takeIf { it >= 0 } ?: 0) }
    var horizontal by remember { mutableStateOf(base?.horizontalSwing ?: currentState?.windLR?.takeIf { it >= 0 } ?: 0) }
    var threeD by remember { mutableStateOf(base?.threeDAuto ?: currentState?.entrust ?: false) }
    val supportsVaneLR = currentState?.let { Codec.modelHasVaneLR(it.modelNrRaw) } != false

    val onceAt = onceDay.atTime(time)
    val profile = profiles.firstOrNull { it.id == profileId }
    val onceInPast = kind == "once" && !onceAt.isAfter(LocalDateTime.now().minusMinutes(1))
    val valid = (if (kind == "weekly") days.isNotEmpty() else !onceInPast) &&
        (action == "off" || !useProfile || profile != null)

    fun build(): ClimateSchedule {
        val usingProfile = action == "on" && useProfile && profile != null
        return ClimateSchedule(
            id = existing?.id ?: UUID.randomUUID().toString(),
            airconId = airconId,
            enabled = existing?.enabled ?: true,
            kind = kind,
            days = if (kind == "weekly") days.sorted() else emptyList(),
            time = if (kind == "weekly") "%02d:%02d".format(Locale.ROOT, time.hour, time.minute) else "",
            at = if (kind == "once") onceAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() else null,
            action = action,
            profileId = if (usingProfile) profile!!.id else null,
            profileName = if (usingProfile) profile!!.name else null,
            settings = when {
                action == "off" -> null
                usingProfile -> ScheduleMath.profileSettings(profile!!)
                else -> ScheduleSettings(mode, temp, fan, vertical, if (supportsVaneLR) horizontal else null, if (supportsVaneLR) threeD else null)
            },
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogNavigationBar(SPage)
        Surface(Modifier.fillMaxSize(), color = SPage) {
          Column(Modifier.fillMaxSize()) {
            // Fixed header: close and save stay reachable while scrolling.
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Text("✕", color = SText, fontSize = 18.sp)
                }
                Text(
                    if (existing == null) "Neuer Zeitplan" else "Zeitplan", color = SText, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onSave(build()) }, enabled = valid) {
                    Text("Sichern", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (valid) SBlue else SMuted)
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                Segmented(listOf("Wöchentlich", "Einmalig"), if (kind == "weekly") 0 else 1) { kind = if (it == 0) "weekly" else "once" }

                // Hero: the time, with what happens at a glance.
                val caption = if (kind == "weekly") (if (days.isEmpty()) "Keine Tage gewählt" else ScheduleMath.daysLabel(days.toList()))
                    else ScheduleMath.whenLabel(onceAt, LocalDateTime.now()).substringBeforeLast(' ').replaceFirstChar { it.uppercase() }
                val preview = ScheduleMath.actionLabel(build())
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF1E7BC4), Color(0xFF12558C))))
                        .clickable { showTimePicker = true }
                        .padding(vertical = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(caption, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("%02d:%02d".format(Locale.ROOT, time.hour, time.minute), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light, letterSpacing = 2.sp)
                        Text(preview, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }

                if (kind == "weekly") {
                    Group("Wiederholen") {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEachIndexed { index, label ->
                                val day = index + 1
                                val active = day in days
                                Box(
                                    Modifier.size(38.dp).clip(CircleShape)
                                        .background(if (active) SBlue else Color(0xFFEFF3F7))
                                        .clickable { days = if (active) days - day else days + day },
                                    contentAlignment = Alignment.Center
                                ) { Text(label, color = if (active) Color.White else SText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                        RowDivider()
                        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("Täglich", days == (1..7).toSet()) { days = (1..7).toSet() }
                            Chip("Werktags", days == (1..5).toSet()) { days = (1..5).toSet() }
                            Chip("Wochenende", days == setOf(6, 7)) { days = setOf(6, 7) }
                        }
                    }
                } else {
                    Group("Wann") {
                        FlowRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val today = LocalDate.now()
                            listOf("Heute" to today, "Morgen" to today.plusDays(1), "Übermorgen" to today.plusDays(2)).forEach { (label, day) ->
                                Chip(label, onceDay == day) { onceDay = day }
                            }
                        }
                        RowDivider()
                        Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("In …", color = SMuted, fontSize = 12.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(30L to "30 Min.", 60L to "1 Std.", 120L to "2 Std.", 240L to "4 Std.", 480L to "8 Std.").forEach { (minutes, label) ->
                                    Chip(label, false) {
                                        val target = LocalDateTime.now().plusMinutes(minutes).withSecond(0).withNano(0)
                                        onceDay = target.toLocalDate(); time = target.toLocalTime()
                                    }
                                }
                            }
                        }
                        if (onceInPast) {
                            Text("Der Zeitpunkt liegt in der Vergangenheit.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }
                }

                Group("Aktion") {
                    Segmented(listOf("Einschalten", "Ausschalten"), if (action == "on") 0 else 1, Modifier.padding(vertical = 10.dp)) { action = if (it == 0) "on" else "off" }
                    AnimatedVisibility(action == "on") {
                        Column {
                            RowDivider()
                            Segmented(listOf("Profil", "Eigene Werte"), if (useProfile) 0 else 1, Modifier.padding(vertical = 10.dp)) {
                                useProfile = it == 0
                            }
                            if (useProfile && profiles.isEmpty()) {
                                Text("Für diese Anlage gibt es noch kein Klimaprofil.", color = SMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                            } else if (useProfile) {
                                FlowRow(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    profiles.forEach { p -> Chip(p.name, profileId == p.id) { profileId = p.id } }
                                }
                                profile?.let {
                                    Text(ScheduleMath.settingsLabel(ScheduleMath.profileSettings(it)), color = SMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
                                }
                            } else {
                                RowDivider()
                                Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Modus", color = SText, fontSize = 14.sp)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("AUTO" to "✦ Auto", "COOL" to "❄ Kühlen", "HEAT" to "☼ Heizen", "FAN" to "≈ Lüfter", "DRY" to "◇ Trocknen").forEach { (value, label) ->
                                            Chip(label, mode == value) { mode = value }
                                        }
                                    }
                                }
                                RowDivider()
                                SettingRow("Temperatur") {
                                    MiniRound("−") { temp = (temp - 0.5).coerceAtLeast(16.0) }
                                    Text("%.1f °C".format(Locale.GERMANY, temp), color = SText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.width(76.dp))
                                    MiniRound("+") { temp = (temp + 0.5).coerceAtMost(30.0) }
                                }
                                RowDivider()
                                Column(Modifier.padding(top = 10.dp, bottom = 2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Lüfter", color = SText, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                        Text(ScheduleMath.fanLabels.getOrElse(fan) { "–" }, color = SBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    }
                                    androidx.compose.material3.Slider(
                                        value = fan.toFloat(), onValueChange = { fan = Math.round(it) },
                                        valueRange = 0f..4f, steps = 3,
                                        colors = blueSliderColors()
                                    )
                                }
                                RowDivider()
                                SettingRow("Vertikal") {
                                    Dropdown(listOf("Auto", "Ganz oben", "Mitte oben", "Mitte unten", "Ganz unten"), vertical) { vertical = it }
                                }
                                if (supportsVaneLR) {
                                    RowDivider()
                                    SettingRow("Horizontal") {
                                        Dropdown(listOf("Auto", "Links außen", "Links", "Mitte links", "Mitte", "Mitte rechts", "Rechts", "Rechts außen"), horizontal) { horizontal = it }
                                    }
                                    RowDivider()
                                    SettingRow("3D-Auto-Luftstrom") {
                                        Switch(threeD, { threeD = it }, colors = blueSwitchColors())
                                    }
                                }
                            }
                        }
                    }
                }

                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Zeitplan löschen", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
          }
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        KlimaPilotAlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = { TextButton(onClick = { time = LocalTime.of(pickerState.hour, pickerState.minute); showTimePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Abbrechen") } },
            text = { TimePicker(state = pickerState) }
        )
    }
}

/** Settings tile: switches schedules on or off for both phones and hides the schedule tiles. */
@Composable
internal fun ScheduleSettingsContent() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE) }
    val io = remember { ScheduleIoBroker() }
    var on by remember(PeerSyncSignals.dataRevision) { mutableStateOf(ScheduleIoBroker.visible(context)) }
    var status by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Zeitpläne verwenden", color = SText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(if (on) "Zeitpläne laufen über ioBroker." else "Zeitpläne sind angehalten und auf beiden Handys ausgeblendet.", color = SMuted, fontSize = 11.sp)
        }
        Switch(on, { value ->
            on = value
            ScheduleIoBroker.setVisible(context, value)
            status = "Wird gespeichert …"
            io.update(prefs.getString("iobroker_server", null).orEmpty(), { _, items -> value to items }) { result, err ->
                status = if (result != null) "" else "ioBroker nicht erreichbar – das andere Handy übernimmt die Einstellung über die Handy-Synchronisierung."
            }
        }, colors = blueSwitchColors())
    }
    if (status.isNotBlank()) Text(status, color = SMuted, fontSize = 11.sp)
}
