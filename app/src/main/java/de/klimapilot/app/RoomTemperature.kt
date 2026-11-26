package de.klimapilot.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Learned room temperature. The ioBroker script stores every thermometer value entered in the app
 * together with the state of the unit (mode, compressor and how long it has been running or resting,
 * sensor trend, fan, outdoor temperature) and learns per unit how far the sensor is off
 * (`roomTemp.model`). The app takes that from the live sensor value; without a learned value
 * (too few readings, ioBroker unreachable or stale) the fixed correction of the device applies.
 */
internal data class RoomTempLastSample(val id: String, val atMillis: Long, val thermo: Double, val raw: Double)
internal data class RoomTempInputResult(val id: String, val ok: Boolean, val message: String)

internal data class RoomTempModel(
    val using: String,
    val samples: Int,
    val offset: Double?,
    val scenario: String,
    val scenarios: List<String>,
    val coverage: Map<String, Int>,
    val errorModel: Double?,
    val errorMean: Double?,
    val minForModel: Int,
    val last: RoomTempLastSample?,
    val input: RoomTempInputResult?,
    val updatedAtMillis: Long,
) {
    /** Learned offset if it is current (the script publishes every 1–5 minutes). */
    fun currentOffset(now: Long = System.currentTimeMillis()): Double? =
        offset?.takeIf { using != "none" && now - updatedAtMillis <= STALE_MS }

    fun describe(): String = when (using) {
        "model" -> "gelernt aus $samples Messwerten – trifft im Mittel ±${fmt(errorModel ?: 0.0)} °C"
        "mean" -> "Mittelwert aus $samples Messwerten" + (errorMean?.let { " (±${fmt(it)} °C)" } ?: "") +
            if (samples < minForModel) " – ab $minForModel Werten lernt das Skript die Lagen" else " – die Lagen bringen noch keine Verbesserung"
        else -> "noch zu wenige Messwerte ($samples von 3) – es gilt die feste Korrektur"
    }

    companion object {
        const val STALE_MS = 12 * 60_000L

        fun parse(o: JSONObject, updatedAtMillis: Long): RoomTempModel {
            val coverage = o.optJSONObject("coverage")?.let { c -> c.keys().asSequence().associateWith { c.optInt(it, 0) } }.orEmpty()
            val scenarios = o.optJSONArray("scenarios")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            val last = o.optJSONObject("last")?.let {
                RoomTempLastSample(it.optString("id"), parseIso(it.optString("at")), it.optDouble("thermo"), it.optDouble("raw"))
            }
            val input = o.optJSONObject("input")?.let { RoomTempInputResult(it.optString("id"), it.optBoolean("ok"), it.optString("message")) }
            return RoomTempModel(
                using = o.optString("using", "none"),
                samples = o.optInt("samples", 0),
                offset = o.optDouble("offset").takeIf { !it.isNaN() && !o.isNull("offset") },
                scenario = o.optString("scenario", ""),
                scenarios = scenarios,
                coverage = coverage,
                errorModel = o.optDouble("errorModel").takeIf { !it.isNaN() && !o.isNull("errorModel") },
                errorMean = o.optDouble("errorMean").takeIf { !it.isNaN() && !o.isNull("errorMean") },
                minForModel = o.optInt("minForModel", 8),
                last = last,
                input = input,
                updatedAtMillis = updatedAtMillis,
            )
        }

        fun parseIso(text: String): Long = runCatching { java.time.Instant.parse(text).toEpochMilli() }.getOrDefault(0L)
        fun fmt(v: Double): String = String.format(Locale.GERMANY, "%.1f", v)
    }
}

internal class RoomTempIoBroker {
    private val main = Handler(Looper.getMainLooper())

    private fun <T> session(address: String, callback: (T?, String?) -> Unit, block: (IoBrokerLease, (T?, String?) -> Unit) -> Unit) =
        IoBrokerRetry.wrap(callback) { cb -> sessionOnce(address, cb, block) }

    private fun <T> sessionOnce(address: String, callback: (T?, String?) -> Unit, block: (IoBrokerLease, (T?, String?) -> Unit) -> Unit) {
        val url = address.trim().trimEnd('/')
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) { callback(null, "Keine ioBroker-Adresse hinterlegt."); return }
        val socket = IoBrokerLease(url)
        val done = AtomicBoolean(false)
        val finish: (T?, String?) -> Unit = { data, error ->
            if (done.compareAndSet(false, true)) {
                runCatching { socket.off() }
                runCatching { socket.disconnect() }
                main.post { callback(data, error) }
            }
        }
        main.postDelayed({ finish(null, "ioBroker antwortet nicht.") }, 15_000)
        socket.on("connect") {
            socket.emit("authenticate", IoBrokerAck { args ->
                val authError = IoBrokerMessages.authError(args)
                if (authError != null) finish(null, authError)
                else block(socket, finish)
            })
        }
        socket.on("connect_error") { args -> finish(null, IoBrokerMessages.connectError(args)) }
        socket.connect()
    }

    /** Learned state per airconId. */
    fun load(address: String, callback: (Map<String, RoomTempModel>?, String?) -> Unit) =
        session<Map<String, RoomTempModel>>(address, callback) { socket, finish ->
            socket.emit("getState", MODEL_ID, IoBrokerAck { args ->
                val raw = (args.getOrNull(1) as? JSONObject)?.opt("val") as? String
                val root = raw?.let { runCatching { JSONObject(it) }.getOrNull() }
                if (root == null) { finish(emptyMap(), null); return@IoBrokerAck }
                val updated = RoomTempModel.parseIso(root.optString("updatedAt"))
                val units = root.optJSONObject("units")
                finish(units?.keys()?.asSequence()?.mapNotNull { id ->
                    units.optJSONObject(id)?.let { id to RoomTempModel.parse(it, updated) }
                }?.toMap().orEmpty(), null)
            })
        }

    /** All stored thermometer readings per airconId (raw JSON, for the backup on the phone). */
    fun loadSamples(address: String, callback: (JSONObject?, String?) -> Unit) =
        session<JSONObject>(address, callback) { socket, finish ->
            socket.emit("getState", SAMPLES_ID, IoBrokerAck { args ->
                val raw = (args.getOrNull(1) as? JSONObject)?.opt("val") as? String
                finish(raw?.let { runCatching { JSONObject(it) }.getOrNull() }?.optJSONObject("units") ?: JSONObject(), null)
            })
        }

    /** Sends a thermometer reading (or the deletion of a reading) to the script. */
    fun send(address: String, input: JSONObject, callback: (Boolean?, String?) -> Unit) =
        session<Boolean>(address, callback) { socket, finish ->
            val definition = JSONObject().put("type", "state")
                .put("common", JSONObject().put("name", "KlimaPilot – Raumtemperatur: Thermometerwert aus der App (wird von KlimaPilot geschrieben)")
                    .put("type", "string").put("role", "json").put("read", true).put("write", true))
                .put("native", JSONObject())
            socket.emit("setObject", INPUT_ID, definition, IoBrokerAck { objectArgs ->
                if (objectArgs.firstOrNull()?.takeUnless { it == JSONObject.NULL } != null) { finish(null, "Messwert konnte nicht gesendet werden."); return@IoBrokerAck }
                socket.emit("setState", INPUT_ID, JSONObject().put("val", input.toString()).put("ack", false), IoBrokerAck { stateArgs ->
                    if (stateArgs.firstOrNull()?.takeUnless { it == JSONObject.NULL } != null) finish(null, "Messwert konnte nicht gesendet werden.")
                    else finish(true, null)
                })
            })
        }

    /** Starts the one-off operation-data check of the script (roomTemp.probe). */
    fun startProbe(address: String, callback: (Boolean?, String?) -> Unit) =
        session<Boolean>(address, callback) { socket, finish ->
            val definition = JSONObject().put("type", "state")
                .put("common", JSONObject().put("name", "KlimaPilot – Betriebsdaten einmal prüfen: beliebigen Wert setzen")
                    .put("type", "string").put("role", "text").put("read", true).put("write", true))
                .put("native", JSONObject())
            socket.emit("setObject", PROBE_ID, definition, IoBrokerAck { objectArgs ->
                if (objectArgs.firstOrNull()?.takeUnless { it == JSONObject.NULL } != null) { finish(null, "Prüfung konnte nicht gestartet werden."); return@IoBrokerAck }
                socket.emit("setState", PROBE_ID, JSONObject().put("val", "start ${System.currentTimeMillis()}").put("ack", false), IoBrokerAck { stateArgs ->
                    if (stateArgs.firstOrNull()?.takeUnless { it == JSONObject.NULL } != null) finish(null, "Prüfung konnte nicht gestartet werden.")
                    else finish(true, null)
                })
            })
        }

    /** Result of the operation-data check, or null while none exists. */
    fun loadProbeResult(address: String, callback: (JSONObject?, String?) -> Unit) =
        session<JSONObject>(address, callback) { socket, finish ->
            socket.emit("getState", PROBE_RESULT_ID, IoBrokerAck { args ->
                val raw = (args.getOrNull(1) as? JSONObject)?.opt("val") as? String
                finish(raw?.let { runCatching { JSONObject(it) }.getOrNull() }, null)
            })
        }

    companion object {
        const val PROBE_ID = "0_userdata.0.KlimaPilot.roomTemp.probe"
        const val PROBE_RESULT_ID = "0_userdata.0.KlimaPilot.roomTemp.probeResult"
        const val INPUT_ID = "0_userdata.0.KlimaPilot.roomTemp.input"
        const val SAMPLES_ID = "0_userdata.0.KlimaPilot.roomTemp.samples"
        const val MODEL_ID = "0_userdata.0.KlimaPilot.roomTemp.model"
    }
}

private val DialogText = Color(0xFF172334)
private val DialogMuted = Color(0xFF63748A)
private val DialogBlue = Color(0xFF1769AA)

/**
 * Dialog behind a tap on "Innen": sensor value, where the shown value comes from, entry of the
 * thermometer reading and which situations already have readings.
 */
@Composable
internal fun RoomTempDialog(
    raw: Double?,
    shown: Double?,
    model: RoomTempModel?,
    ioBrokerReachable: Boolean,
    checking: Boolean,
    status: String?,
    statusOk: Boolean?,
    onSubmit: (Double) -> Unit,
    onDeleteLast: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
) {
    // Asks ioBroker right away instead of waiting for the next minute's query.
    LaunchedEffect(Unit) { onOpen() }
    // Digits only (number pad without searching for the comma): "175" = 17,5 °C, "17" = 17 °C.
    var text by remember { mutableStateOf("") }
    val value = thermometerValue(text)
    KlimaPilotAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Raumtemperatur", color = DialogText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fühler der Anlage: ${raw?.let { RoomTempModel.fmt(it) + " °C" } ?: "—"}", color = DialogText)
                Text("Angezeigt: ${shown?.let { RoomTempModel.fmt(it) + " °C" } ?: "—"}", color = DialogText, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        !ioBrokerReachable && checking -> "Verbindung zu ioBroker wird geprüft …"
                        !ioBrokerReachable -> "ioBroker nicht erreichbar – es gilt die feste Korrektur."
                        model == null -> "Noch keine Messwerte – es gilt die feste Korrektur."
                        model.using != "none" && model.currentOffset() == null -> "Keine aktuellen Daten vom Skript – es gilt die feste Korrektur."
                        else -> model.describe().replaceFirstChar { it.uppercase() } + "."
                    },
                    color = DialogMuted, fontSize = 13.sp
                )
                // After saving, the keyboard goes away so the result and the counts are visible.
                val keyboard = LocalSoftwareKeyboardController.current
                val focusManager = LocalFocusManager.current
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Thermometer (°C)") },
                        placeholder = { Text("z. B. 175 = 17,5") },
                        supportingText = { Text("Nur Ziffern – das Komma setzt die App", fontSize = 11.sp) },
                        singleLine = true,
                        visualTransformation = ThermometerComma,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DialogBlue, focusedLabelColor = DialogBlue, cursorColor = DialogBlue),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { value?.let { onSubmit(it); text = ""; keyboard?.hide(); focusManager.clearFocus() } }, enabled = value != null && raw != null && ioBrokerReachable) {
                        Text("Speichern", color = if (value != null && raw != null && ioBrokerReachable) DialogBlue else DialogMuted)
                    }
                }
                status?.let {
                    Text(
                        it,
                        color = when (statusOk) { true -> Color(0xFF16845B); false -> Color(0xFFB3261E); null -> DialogMuted },
                        fontSize = 14.sp,
                        fontWeight = if (statusOk != null) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
                if (model != null && model.scenarios.isNotEmpty()) {
                    Text("Messwerte je Lage – möglichst alle füllen:", color = DialogText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    val names = (model.scenarios + model.coverage.keys).distinct()
                    names.forEach { name ->
                        val current = name == model.scenario
                        Row(Modifier.fillMaxWidth()) {
                            Text((if (current) "▶ " else "   ") + name, color = if (current) DialogBlue else DialogText, fontSize = 13.sp,
                                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            Text("${model.coverage[name] ?: 0}", color = if (current) DialogBlue else DialogMuted, fontSize = 13.sp)
                        }
                    }
                }
                model?.last?.let { last ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val time = java.text.SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(java.util.Date(last.atMillis))
                        Text("Zuletzt: ${RoomTempModel.fmt(last.thermo)} °C ($time)", color = DialogMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onDeleteLast(last.id) }, enabled = ioBrokerReachable) { Text("Löschen", color = DialogMuted, fontSize = 12.sp) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen", color = DialogBlue) } }
    )
}

/** What the main page needs for the learned room temperature. */
internal class RoomTempUi(
    val models: Map<String, RoomTempModel>,
    val reachable: Boolean,
    /** The immediate query after opening the dialog is still running. */
    val checking: Boolean,
    /** Asks ioBroker right away (on opening the dialog). */
    val refresh: () -> Unit,
    val status: String?,
    /** null = waiting, true = confirmed by the script, false = failed. */
    val statusOk: Boolean?,
    val clearStatus: () -> Unit,
    val submit: (airconId: String, thermometer: Double, raw: Double) -> Unit,
    val delete: (airconId: String, sampleId: String) -> Unit,
)

/** "175" → 17.5, "17" → 17.0; anything outside 5–40 °C → null. */
internal fun thermometerValue(digits: String): Double? {
    if (digits.length !in 2..3 || !digits.all { it.isDigit() }) return null
    val value = if (digits.length == 3) digits.toInt() / 10.0 else digits.toDouble()
    return value.takeIf { it in 5.0..40.0 }
}

/** Shows the comma before the last of three digits: 175 → 17,5. */
private val ThermometerComma = androidx.compose.ui.text.input.VisualTransformation { text ->
    val raw = text.text
    val shown = if (raw.length == 3) raw.substring(0, 2) + "," + raw.substring(2) else raw
    androidx.compose.ui.text.input.TransformedText(
        androidx.compose.ui.text.AnnotatedString(shown),
        object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (raw.length == 3 && offset >= 3) offset + 1 else offset
            override fun transformedToOriginal(offset: Int) = if (raw.length == 3 && offset >= 3) (offset - 1).coerceAtMost(raw.length) else offset.coerceAtMost(raw.length)
        }
    )
}

/** Readable lines of a probe result (one block per unit). */
internal fun probeResultText(result: JSONObject): String {
    val units = result.optJSONObject("units") ?: return "Kein Ergebnis."
    fun num(o: JSONObject, key: String, unit: String) =
        if (o.has(key) && !o.isNull(key)) String.format(Locale.GERMANY, "%.1f %s", o.optDouble(key), unit) else "—"
    return units.keys().asSequence().joinToString("\n\n") { name ->
        val u = units.optJSONObject(name) ?: JSONObject()
        buildString {
            append(name).append(": ")
            append(if (u.optBoolean("ok")) "Antwort erhalten" else "keine Antwort (${u.optString("error")})")
            append("\nVerdichter: ").append(num(u, "compressorHz", "Hz")).append(" · Strom: ").append(num(u, "currentA", "A"))
            append("\nWärmetauscher: ").append(num(u, "coilC", "°C")).append(" / ").append(num(u, "coilGasC", "°C"))
            append("\nSollwert intern: ").append(num(u, "setpointC", "°C"))
            append("\nVerdichter laut Status: ").append(if (u.optBoolean("compressorRunning")) "läuft" else "steht")
            append("\nAnlage vorher/nachher: ").append(if (u.optBoolean("powerBefore")) "an" else "aus").append(" / ")
                .append(if (!u.has("powerAfter")) "?" else if (u.optBoolean("powerAfter")) "an" else "aus")
            if (u.optBoolean("switchedOff")) append("\n⚠ Hat sich ausgeschaltet – wieder eingeschaltet.")
            u.optJSONArray("raw")?.let { append("\nRohdaten: ").append((0 until it.length()).joinToString(" | ") { i -> it.optString(i) }) }
        }
    }
}

/**
 * Copy of the thermometer readings on the phone, so the work of entering them is not lost if the
 * ioBroker data goes missing (reinstall, broken state database). Refreshed at most every 6 hours;
 * if ioBroker has no readings at all for a unit while the copy has some, they are sent back
 * (the script only accepts this while its list for that unit is empty, so nothing is overwritten).
 * Readings deleted in the app are removed from the copy too.
 */
internal object RoomTempBackup {
    private const val FILE = "roomtemp_samples_backup.json"
    private const val PREF_LAST = "roomtemp_backup_at"
    private const val INTERVAL_MS = 6 * 3600 * 1000L
    /** Below this a missing list is more likely deliberate than lost. */
    private const val MIN_TO_RESTORE = 3

    private fun file(context: Context) = java.io.File(context.applicationContext.filesDir, FILE)

    private fun read(context: Context): JSONObject =
        runCatching { JSONObject(file(context).readText()) }.getOrDefault(JSONObject())

    @Synchronized
    private fun write(context: Context, units: JSONObject) {
        runCatching { file(context).writeText(units.toString()) }
    }

    fun count(context: Context): Int = read(context).let { u -> u.keys().asSequence().sumOf { u.optJSONArray(it)?.length() ?: 0 } }

    fun maybeRun(context: Context, address: String, io: RoomTempIoBroker = RoomTempIoBroker()) {
        val prefs = context.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(PREF_LAST, 0L) < INTERVAL_MS) return
        io.loadSamples(address) { remote, _ ->
            if (remote == null) return@loadSamples
            prefs.edit().putLong(PREF_LAST, now).apply()
            val backup = read(context)
            // Take over every unit ioBroker has readings for.
            remote.keys().asSequence().toList().forEach { id ->
                val list = remote.optJSONArray(id)
                if (list != null && list.length() > 0) backup.put(id, list)
            }
            write(context, backup)
            // Send back what ioBroker lost entirely.
            backup.keys().asSequence().toList().forEach { id ->
                val saved = backup.optJSONArray(id) ?: return@forEach
                val there = remote.optJSONArray(id)?.length() ?: 0
                if (there == 0 && saved.length() >= MIN_TO_RESTORE) {
                    val input = JSONObject().put("id", "restore-$now").put("airconId", id).put("restore", saved)
                    io.send(address, input) { ok, _ ->
                        EventJournal.log("Sicherung", "", if (ok == true) "${saved.length()} Messwerte an ioBroker zurückgeschickt ($id)" else "Zurückschicken der Messwerte fehlgeschlagen ($id)")
                    }
                }
            }
        }
    }

    /** A reading deleted in the app must not come back from the copy. */
    fun forget(context: Context, airconId: String, sampleId: String) {
        val backup = read(context)
        val list = backup.optJSONArray(airconId) ?: return
        val kept = org.json.JSONArray()
        for (i in 0 until list.length()) list.optJSONObject(i)?.takeIf { it.optString("id") != sampleId }?.let { kept.put(it) }
        backup.put(airconId, kept)
        write(context, backup)
    }
}
