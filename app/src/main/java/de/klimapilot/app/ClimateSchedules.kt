package de.klimapilot.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
)

internal data class ScheduleRun(val key: String, val at: String?, val done: Boolean, val result: String?)

internal data class ScheduleData(
    val enabled: Boolean,
    val items: List<ClimateSchedule>,
    val status: Map<String, ScheduleRun>,
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
        )
    }

    fun configToJson(enabled: Boolean, items: List<ClimateSchedule>) = JSONObject()
        .put("enabled", enabled)
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
        return buildList {
            add("Ein")
            s.mode?.let { add(modeLabels[it] ?: it) }
            s.targetTemp?.let { add("%.1f °C".format(Locale.GERMANY, it)) }
            s.fanSpeed?.let { add("Lüfter " + fanLabels.getOrElse(it) { "?" }) }
        }.joinToString(" · ")
    }

    fun profileSettings(p: ClimateProfile) = ScheduleSettings(p.mode, p.targetTemp, p.fanSpeed, p.verticalSwing, p.horizontalSwing, p.threeDAuto)

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

    private fun session(address: String, callback: (ScheduleData?, String?) -> Unit, block: (IoBrokerSocket, (ScheduleData?, String?) -> Unit) -> Unit) {
        val url = address.trim().trimEnd('/')
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) { callback(null, "Keine ioBroker-Adresse hinterlegt."); return }
        val socket = IoBrokerSocket(url, "name=klimapilot.schedules")
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

    private fun readJson(socket: IoBrokerSocket, id: String, next: (JSONObject?) -> Unit) {
        socket.emit("getState", id, IoBrokerAck { args ->
            val raw = (args.getOrNull(1) as? JSONObject)?.opt("val") as? String
            next(raw?.let { runCatching { JSONObject(it) }.getOrNull() })
        })
    }

    private fun read(socket: IoBrokerSocket, next: (ScheduleData) -> Unit) {
        readJson(socket, CONFIG_ID) { config ->
            readJson(socket, STATUS_ID) { status ->
                val (enabled, items) = ScheduleJson.configFromJson(config)
                next(ScheduleData(enabled, items, ScheduleJson.statusFromJson(status)))
            }
        }
    }

    fun load(address: String, callback: (ScheduleData?, String?) -> Unit) = session(address, callback) { socket, finish ->
        read(socket) { finish(it, null) }
    }

    /** Reads the current schedules, applies [change] and writes them back only if something changed. */
    fun update(address: String, change: (Boolean, List<ClimateSchedule>) -> Pair<Boolean, List<ClimateSchedule>>, callback: (ScheduleData?, String?) -> Unit) =
        session(address, callback) { socket, finish ->
            read(socket) { current ->
                val (enabled, items) = change(current.enabled, current.items)
                if (enabled == current.enabled && items == current.items) { finish(current, null); return@read }
                val definition = JSONObject().put("type", "state")
                    .put("common", JSONObject().put("name", "KlimaPilot – Zeitpläne (werden von KlimaPilot geschrieben)").put("type", "string").put("role", "json").put("read", true).put("write", true))
                    .put("native", JSONObject())
                socket.emit("setObject", CONFIG_ID, definition, IoBrokerAck { objectArgs ->
                    if (objectArgs.firstOrNull()?.takeUnless { it == JSONObject.NULL } != null) { finish(null, "Zeitpläne konnten nicht gespeichert werden."); return@IoBrokerAck }
                    val value = ScheduleJson.configToJson(enabled, items).toString()
                    socket.emit("setState", CONFIG_ID, JSONObject().put("val", value).put("ack", false), IoBrokerAck { stateArgs ->
                        if (stateArgs.firstOrNull()?.takeUnless { it == JSONObject.NULL } != null) finish(null, "Zeitpläne konnten nicht gespeichert werden.")
                        else finish(ScheduleData(enabled, items, current.status), null)
                    })
                })
            }
        }

    companion object {
        const val ROOT = "0_userdata.0.KlimaPilot.schedules"
        const val CONFIG_ID = "$ROOT.config"
        const val STATUS_ID = "$ROOT.status"
        private const val PREF_VISIBLE = "schedules_visible"

        fun visible(context: Context) = context.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE).getBoolean(PREF_VISIBLE, true)
        fun setVisible(context: Context, value: Boolean) =
            context.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE).edit().putBoolean(PREF_VISIBLE, value).apply()
    }
}

// -------------------------------------------------------------------------------------------
// UI
// -------------------------------------------------------------------------------------------

private val SBlue = Color(0xFF1769AA)
private val SLight = Color(0xFFE3EEF7)
private val SText = Color(0xFF172334)
private val SMuted = Color(0xFF63748A)
private val SDark = Color(0xFF123B60)

@Composable
private fun Pill(label: String, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick),
        color = if (selected) SBlue else SLight,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            label, Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            color = if (selected) Color.White else SDark.copy(alpha = if (enabled) 1f else 0.4f),
            fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SectionLabel(text: String) = Text(text.uppercase(), color = SMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)

/** The schedule tile of one unit on the main page. Hidden when schedules are switched off. */
@Composable
internal fun ScheduleSection(device: UserDevice?, currentState: AirconState?) {
    if (device == null || device.demoMode || device.wfRacAirconId.isBlank()) return
    val context = LocalContext.current
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
                // A profile used by a schedule was changed here: hand its current values to ioBroker.
                if (ScheduleMath.withProfiles(result.items, fresh) != result.items) {
                    io.update(address, { enabled, items -> enabled to ScheduleMath.withProfiles(items, fresh) }) { updated, _ ->
                        if (updated != null) data = updated
                    }
                }
            }
            delay(60_000)
        }
    }

    fun change(transform: (List<ClimateSchedule>) -> List<ClimateSchedule>) {
        busy = true
        profiles = loadProfiles()
        val fresh = profiles
        io.update(address, { enabled, items -> enabled to ScheduleMath.withProfiles(transform(items), fresh) }) { result, err ->
            busy = false
            if (result != null) { data = result; error = null } else error = err
        }
    }

    val current = data
    if (current != null && !current.enabled) return
    val now = LocalDateTime.now()
    val mine = current?.items.orEmpty().filter { it.airconId == device.wfRacAirconId }
        .sortedWith(compareBy({ ScheduleMath.nextRun(it, now, current?.status?.get(it.id)) == null }, { ScheduleMath.nextRun(it, now, current?.status?.get(it.id)) }))
    val next = mine.mapNotNull { item -> ScheduleMath.nextRun(item, now, current?.status?.get(item.id))?.let { item to it } }.minByOrNull { it.second }

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
            mine.forEach { item ->
                val run = current?.status?.get(item.id)
                val failed = run != null && !run.done && run.result != null && run.result != "ok"
                Surface(color = SLight.copy(alpha = 0.55f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { editing = item }) {
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
                            colors = SwitchDefaults.colors(checkedTrackColor = SBlue)
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
            profiles = profiles,
            currentState = currentState,
            onDismiss = { creating = false; editing = null },
            onDelete = editing?.let { e -> { change { list -> list.filterNot { it.id == e.id } }; editing = null } },
            onSave = { saved ->
                change { list -> if (list.any { it.id == saved.id }) list.map { if (it.id == saved.id) saved else it } else list + saved }
                creating = false; editing = null
            }
        )
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
    var fan by remember { mutableStateOf(base?.fanSpeed ?: currentState?.airFlow ?: 0) }
    var vertical by remember { mutableStateOf(base?.verticalSwing ?: currentState?.windUD ?: 0) }
    var horizontal by remember { mutableStateOf(base?.horizontalSwing ?: currentState?.windLR ?: 0) }
    var threeD by remember { mutableStateOf(base?.threeDAuto ?: currentState?.entrust ?: false) }
    val supportsVaneLR = currentState?.let { Codec.modelHasVaneLR(it.modelNrRaw) } != false

    val onceAt = onceDay.atTime(time)
    val valid = when (kind) {
        "weekly" -> days.isNotEmpty()
        else -> onceAt.isAfter(LocalDateTime.now().minusMinutes(1))
    } && (action == "off" || !useProfile || profileId != null)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF2F6FA)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (existing == null) "Neuer Zeitplan" else "Zeitplan bearbeiten", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = SText, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Wöchentlich", kind == "weekly", Modifier.weight(1f)) { kind = "weekly" }
                    Pill("Einmalig", kind == "once", Modifier.weight(1f)) { kind = "once" }
                }

                // Time: a large, tappable clock face.
                Surface(color = Color.White, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable { showTimePicker = true }) {
                    Column(Modifier.padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%02d:%02d".format(Locale.ROOT, time.hour, time.minute), fontSize = 56.sp, fontWeight = FontWeight.Bold, color = SBlue)
                        Text("Uhrzeit antippen zum Ändern", color = SMuted, fontSize = 12.sp)
                    }
                }

                if (kind == "weekly") {
                    SectionLabel("Tage")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEachIndexed { index, label ->
                            val day = index + 1
                            Pill(label, day in days, Modifier.weight(1f)) { days = if (day in days) days - day else days + day }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Täglich", days == (1..7).toSet()) { days = (1..7).toSet() }
                        Pill("Mo–Fr", days == (1..5).toSet()) { days = (1..5).toSet() }
                        Pill("Sa–So", days == setOf(6, 7)) { days = setOf(6, 7) }
                    }
                } else {
                    SectionLabel("Tag")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val today = LocalDate.now()
                        listOf("Heute" to today, "Morgen" to today.plusDays(1), "Übermorgen" to today.plusDays(2)).forEach { (label, day) ->
                            Pill(label, onceDay == day, Modifier.weight(1f)) { onceDay = day }
                        }
                    }
                    SectionLabel("Oder in …")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30L to "30 Min.", 60L to "1 Std.", 120L to "2 Std.", 240L to "4 Std.", 480L to "8 Std.").forEach { (minutes, label) ->
                            Pill(label, false) {
                                val target = LocalDateTime.now().plusMinutes(minutes).withSecond(0).withNano(0)
                                onceDay = target.toLocalDate(); time = target.toLocalTime()
                            }
                        }
                    }
                    Text("Schaltet ${ScheduleMath.whenLabel(onceAt, LocalDateTime.now())}", color = if (valid || action == "off") SMuted else MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }

                SectionLabel("Aktion")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Einschalten", action == "on", Modifier.weight(1f)) { action = "on" }
                    Pill("Ausschalten", action == "off", Modifier.weight(1f)) { action = "off" }
                }

                AnimatedVisibility(action == "on") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("Profil", useProfile, Modifier.weight(1f), enabled = profiles.isNotEmpty()) { useProfile = true }
                            Pill("Eigene Werte", !useProfile, Modifier.weight(1f)) { useProfile = false }
                        }
                        if (useProfile && profiles.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                profiles.forEach { p -> Pill(p.name, profileId == p.id) { profileId = p.id } }
                            }
                            profiles.firstOrNull { it.id == profileId }?.let { Text(it.summary(), color = SMuted, fontSize = 12.sp) }
                        } else {
                            SectionLabel("Betriebsmodus")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("AUTO" to "Auto", "COOL" to "Kühlen", "HEAT" to "Heizen", "FAN" to "Lüfter", "DRY" to "Trocknen").forEach { (value, label) ->
                                    Pill(label, mode == value, Modifier.weight(1f)) { mode = value }
                                }
                            }
                            SectionLabel("Solltemperatur")
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                RoundButton("−") { temp = (temp - 0.5).coerceAtLeast(16.0) }
                                Text("%.1f °C".format(Locale.GERMANY, temp), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = SText, modifier = Modifier.width(150.dp), textAlign = TextAlign.Center)
                                RoundButton("+") { temp = (temp + 0.5).coerceAtMost(30.0) }
                            }
                            SectionLabel("Lüfter")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ScheduleMath.fanLabels.forEachIndexed { index, label -> Pill(label, fan == index, Modifier.weight(1f)) { fan = index } }
                            }
                            SectionLabel("Vertikal")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Auto", "Ganz oben", "Mitte oben", "Mitte unten", "Ganz unten").forEachIndexed { index, label -> Pill(label, vertical == index) { vertical = index } }
                            }
                            if (supportsVaneLR) {
                                SectionLabel("Horizontal")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Auto", "Links außen", "Links", "Mitte links", "Mitte", "Mitte rechts", "Rechts", "Rechts außen").forEachIndexed { index, label -> Pill(label, horizontal == index) { horizontal = index } }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("3D-Auto-Luftstrom", color = SText, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                    Switch(threeD, { threeD = it }, colors = SwitchDefaults.colors(checkedTrackColor = SBlue))
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        val profile = profiles.firstOrNull { it.id == profileId }
                        val usingProfile = action == "on" && useProfile && profile != null
                        onSave(
                            ClimateSchedule(
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
                        )
                    },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SBlue)
                ) { Text("Speichern", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                if (onDelete != null) {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().height(48.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)) {
                        Text("Zeitplan löschen", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = { TextButton(onClick = { time = LocalTime.of(pickerState.hour, pickerState.minute); showTimePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Abbrechen") } },
            text = { TimePicker(state = pickerState) }
        )
    }
}

@Composable
private fun RoundButton(label: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.size(52.dp).clip(CircleShape).clickable(onClick = onClick), color = Color.White, shape = CircleShape, shadowElevation = 2.dp) {
        Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 26.sp, color = SBlue, fontWeight = FontWeight.Bold) }
    }
}

/** Settings tile: switches schedules on or off for both phones and hides the schedule tiles. */
@Composable
internal fun ScheduleSettingsContent() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE) }
    val io = remember { ScheduleIoBroker() }
    var on by remember { mutableStateOf(ScheduleIoBroker.visible(context)) }
    var status by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Zeitpläne verwenden", color = SText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(if (on) "Zeitpläne laufen über ioBroker." else "Zeitpläne sind angehalten und ausgeblendet.", color = SMuted, fontSize = 11.sp)
        }
        Switch(on, { value ->
            on = value
            ScheduleIoBroker.setVisible(context, value)
            status = "Wird gespeichert …"
            io.update(prefs.getString("iobroker_server", null).orEmpty(), { _, items -> value to items }) { result, err ->
                status = if (result != null) "" else "Nur auf diesem Handy umgestellt: ${err ?: "ioBroker nicht erreichbar"}"
            }
        }, colors = SwitchDefaults.colors(checkedTrackColor = SBlue))
    }
    if (status.isNotBlank()) Text(status, color = SMuted, fontSize = 11.sp)
}
