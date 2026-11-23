package de.klimapilot.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/** One line of the diagnostics log, from the ioBroker script or from this phone. */
internal data class DiagEntry(val at: Long, val source: String, val subject: String, val event: String, val detail: String) {
    fun line(): String {
        val time = DateTimeFormatter.ofPattern("dd.MM. HH:mm").format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))
        return "$time  $source · $subject: $event" + if (detail.isNotBlank()) " ($detail)" else ""
    }

    companion object {
        fun fromJson(item: JSONObject): DiagEntry? {
            val at = item.optString("at").let { raw -> runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull() }
                ?: item.optLong("atMs", 0L).takeIf { it > 0L } ?: return null
            return DiagEntry(at, item.optString("source"), item.optString("subject"), item.optString("event"), item.optString("detail"))
        }
    }
}

/**
 * Diagnostics on this phone. While switched on, the app notes when a unit becomes unreachable or
 * reachable again and when ioBroker cannot be reached. The switch itself lives in ioBroker
 * (so the script logs too); this keeps a local copy so logging works without a connection.
 */
internal object AppDiagnostics {
    private const val PREFS = "klimapilot_diagnostics"
    private const val MAX_ENTRIES = 300
    private const val KEEP_MS = 14L * 24 * 3600 * 1000
    private var context: Context? = null
    private val lastReach = mutableMapOf<String, Pair<Boolean, Long>>()

    fun init(context: Context) { this.context = context.applicationContext }

    private fun onWlan(): Boolean {
        val manager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return true
        val caps = runCatching { manager.getNetworkCapabilities(manager.activeNetwork) }.getOrNull() ?: return false
        return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun prefs() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val enabled: Boolean get() = prefs()?.getBoolean("enabled", false) == true

    fun setEnabledLocal(value: Boolean) {
        val prefs = prefs() ?: return
        if (prefs.getBoolean("enabled", false) == value) return
        prefs.edit().putBoolean("enabled", value).apply()
        lastReach.clear()
        lastSeen.clear()
        loggedDown.clear()
        log("Handy", if (value) "Diagnose eingeschaltet" else "Diagnose ausgeschaltet", "", force = true)
    }

    @Synchronized
    fun log(subject: String, event: String, detail: String, force: Boolean = false) {
        val prefs = prefs() ?: return
        if (!force && !enabled) return
        val now = System.currentTimeMillis()
        val list = entries().filter { it.at >= now - KEEP_MS } + DiagEntry(now, "Handy", subject, event, detail)
        val array = JSONArray()
        list.takeLast(MAX_ENTRIES).forEach {
            array.put(JSONObject().put("atMs", it.at).put("source", it.source).put("subject", it.subject).put("event", it.event).put("detail", it.detail))
        }
        prefs.edit().putString("entries", array.toString()).apply()
    }

    fun entries(): List<DiagEntry> {
        val raw = prefs()?.getString("entries", null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(DiagEntry::fromJson) }
    }

    @Synchronized
    fun clearLocal() { prefs()?.edit()?.remove("entries")?.commit() }

    private val lastSeen = mutableMapOf<String, Long>()

    /**
     * Called by every check of a unit or of ioBroker; only changes are logged. If the phone has
     * not looked for a while (app closed, other room), the earlier state is forgotten, so no
     * outage is reported for time in which the phone simply did not check. Short gaps (under
     * [MIN_OUTAGE_MS], typically the phone's own WLAN dozing) are not logged at all: an outage is
     * written once it has lasted that long, and only then is its end written too.
     */
    @Synchronized
    fun reach(subject: String, ok: Boolean, error: String? = null, gapMs: Long = 3 * 60_000L) {
        if (!enabled) return
        // Off the home WLAN (mobile data) the units and ioBroker are out of reach anyway: nothing to log.
        if (!onWlan()) return
        val now = System.currentTimeMillis()
        val seenBefore = lastSeen[subject]
        lastSeen[subject] = now
        if (seenBefore == null || now - seenBefore > gapMs) { lastReach.remove(subject); loggedDown.remove(subject) }
        val previous = lastReach[subject]
        if (ok) {
            lastReach[subject] = true to now
            if (previous != null && !previous.first && loggedDown.remove(subject)) {
                log(subject, "wieder erreichbar", "nach ${((now - previous.second) / 60_000).coerceAtLeast(1)} min")
            }
            return
        }
        val downSince = if (previous != null && !previous.first) previous.second else now
        lastReach[subject] = false to downSince
        if (subject !in loggedDown && now - downSince >= MIN_OUTAGE_MS) {
            loggedDown.add(subject)
            val since = java.text.SimpleDateFormat("HH:mm", java.util.Locale.GERMANY).format(java.util.Date(downSince))
            log(subject, "nicht erreichbar", "seit $since – " + error.orEmpty())
        }
    }

    private val loggedDown = mutableSetOf<String>()
    private const val MIN_OUTAGE_MS = 2 * 60_000L
}

/** Switch and log of the ioBroker side (states under 0_userdata.0.KlimaPilot.diagnostics). */
internal class DiagnosticsIoBroker {
    private val main = Handler(Looper.getMainLooper())

    data class Result(val enabled: Boolean, val entries: List<DiagEntry>)

    private fun session(address: String, callback: (Result?, String?) -> Unit, block: (IoBrokerLease, (Result?, String?) -> Unit) -> Unit) {
        val url = address.trim().trimEnd('/')
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) { callback(null, "Keine ioBroker-Adresse hinterlegt."); return }
        val socket = IoBrokerLease(url)
        val done = AtomicBoolean(false)
        val finish: (Result?, String?) -> Unit = { data, error ->
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

    private fun read(socket: IoBrokerLease, next: (Result) -> Unit) {
        readJson(socket, CONFIG_ID) { config ->
            readJson(socket, LOG_ID) { log ->
                val array = log?.optJSONArray("entries") ?: JSONArray()
                val entries = (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(DiagEntry::fromJson) }
                next(Result(config?.optBoolean("enabled", false) == true, entries))
            }
        }
    }

    private fun write(socket: IoBrokerLease, id: String, name: String, value: JSONObject, done: (Boolean) -> Unit) {
        val definition = JSONObject().put("type", "state")
            .put("common", JSONObject().put("name", name).put("type", "string").put("role", "json").put("read", true).put("write", true))
            .put("native", JSONObject())
        socket.emit("setObject", id, definition, IoBrokerAck { objectArgs ->
            if (objectArgs.firstOrNull()?.takeUnless { it == JSONObject.NULL } != null) { done(false); return@IoBrokerAck }
            socket.emit("setState", id, JSONObject().put("val", value.toString()).put("ack", false), IoBrokerAck { stateArgs ->
                done(stateArgs.firstOrNull()?.takeUnless { it == JSONObject.NULL } == null)
            })
        })
    }

    fun load(address: String, callback: (Result?, String?) -> Unit) = session(address, callback) { socket, finish ->
        read(socket) { finish(it, null) }
    }

    fun setEnabled(address: String, enabled: Boolean, callback: (Result?, String?) -> Unit) = session(address, callback) { socket, finish ->
        val value = JSONObject().put("enabled", enabled).put("updatedAt", System.currentTimeMillis())
        write(socket, CONFIG_ID, "KlimaPilot – Diagnose an/aus (wird von KlimaPilot geschrieben)", value) { ok ->
            if (!ok) finish(null, "Diagnose konnte in ioBroker nicht gespeichert werden.") else read(socket) { finish(it, null) }
        }
    }

    fun clear(address: String, callback: (Result?, String?) -> Unit) = session(address, callback) { socket, finish ->
        write(socket, LOG_ID, "KlimaPilot – Diagnoseprotokoll", JSONObject().put("entries", JSONArray())) { ok ->
            if (!ok) finish(null, "Protokoll konnte nicht geleert werden.") else read(socket) { finish(it, null) }
        }
    }

    companion object {
        const val CONFIG_ID = "0_userdata.0.KlimaPilot.diagnostics.config"
        const val LOG_ID = "0_userdata.0.KlimaPilot.diagnostics.log"
    }
}

@Composable
internal fun DiagnosticsSettingsCard(expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE) }
    val address = prefs.getString("iobroker_server", null).orEmpty()
    val io = remember { DiagnosticsIoBroker() }
    var on by remember { mutableStateOf(AppDiagnostics.enabled) }
    var remote by remember { mutableStateOf<List<DiagEntry>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf(false) }
    // The phone's own entries are not Compose state; this counter makes the list re-read them.
    var localVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(expanded, refresh) {
        if (!expanded) return@LaunchedEffect
        io.load(address) { result, error ->
            if (result != null) {
                on = result.enabled
                AppDiagnostics.setEnabledLocal(result.enabled)
                remote = result.entries
                status = ""
            } else status = error.orEmpty()
        }
    }
    val entries = remember(remote, localVersion, refresh) { dedupeEntries(remote + AppDiagnostics.entries()) }

    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3EEF7))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().clickable { onExpandedChange(!expanded) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Diagnose", color = SText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(if (on) "Läuft – Verbindungsausfälle werden protokolliert." else "Aus", color = SMuted, fontSize = 11.sp)
                }
                Text(if (expanded) "⌃" else "⌄", color = SMuted, fontSize = 20.sp)
            }
            if (expanded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Verbindungen protokollieren", color = SText, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.padding(end = 6.dp).size(26.dp).clip(CircleShape).clickable { showInfo = true }, contentAlignment = Alignment.Center) {
                        Text("ⓘ", color = SMuted, fontSize = 15.sp)
                    }
                    Switch(on, { value ->
                        on = value
                        AppDiagnostics.setEnabledLocal(value)
                        status = "Wird gespeichert …"
                        io.setEnabled(address, value) { result, error ->
                            if (result != null) { remote = result.entries; status = "" }
                            else status = error.orEmpty()
                        }
                    }, colors = blueSwitchColors())
                }
                if (status.isNotBlank()) Text(status, color = SMuted, fontSize = 11.sp)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (entries.isEmpty()) Text("Noch keine Einträge.", color = SMuted, fontSize = 12.sp)
                    entries.take(60).forEach { entry ->
                        Text(entry.line(), color = if (entry.event == "nicht erreichbar") Color(0xFFB3261E) else SText, fontSize = 11.sp, lineHeight = 14.sp)
                    }
                    if (entries.size > 60) Text("… ${entries.size - 60} ältere Einträge (beim Kopieren enthalten)", color = SMuted, fontSize = 11.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { refresh++ }, modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text("Aktualisieren", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = {
                            val text = entries.joinToString("\n") { it.line() }.ifBlank { "Keine Einträge." }
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("KlimaPilot Diagnose", text))
                            status = "Protokoll kopiert – du kannst es jetzt einfügen und mir schicken."
                        },
                        modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text("Kopieren", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = {
                            AppDiagnostics.clearLocal()
                            remote = emptyList()
                            localVersion++
                            status = "Protokoll geleert."
                            io.clear(address) { result, error ->
                                remote = result?.entries ?: remote
                                localVersion++
                                status = error ?: "Protokoll geleert."
                            }
                        },
                        modifier = Modifier.weight(1f).height(38.dp), contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text("Leeren", fontSize = 12.sp) }
                }
            }
        }
    }

    if (showInfo) {
        KlimaPilotAlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("OK") } },
            title = { Text("Diagnose") },
            text = {
                Text(
                    "Eingeschaltet prüft das ioBroker-Skript jede Minute, ob die Klimaanlagen im Heimnetz erreichbar sind, " +
                        "und schreibt jeden Ausfall mit Beginn, Dauer und Fehler ins Protokoll. Die App notiert zusätzlich, " +
                        "was dieses Handy sieht. Einträge werden 14 Tage aufbewahrt. Zum Auswerten „Kopieren“ und mir schicken."
                )
            }
        )
    }
}

/** Newest first; the same message from the same source within 3 min is shown only once. */
internal fun dedupeEntries(all: List<DiagEntry>): List<DiagEntry> {
    val kept = mutableListOf<DiagEntry>()
    for (entry in all.sortedBy { it.at }) {
        val twin = kept.lastOrNull {
            it.source == entry.source && it.subject == entry.subject && it.event == entry.event && it.detail == entry.detail
        }
        if (twin != null && entry.at - twin.at < 3 * 60_000L) continue
        kept += entry
    }
    return kept.sortedByDescending { it.at }
}
