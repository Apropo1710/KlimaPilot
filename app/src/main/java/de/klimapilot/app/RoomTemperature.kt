package de.klimapilot.app

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

    private fun <T> session(address: String, callback: (T?, String?) -> Unit, block: (IoBrokerLease, (T?, String?) -> Unit) -> Unit) {
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
                if (args.firstOrNull() as? Boolean != true) finish(null, "ioBroker hat die Verbindung nicht authentifiziert.")
                else block(socket, finish)
            })
        }
        socket.on("connect_error") { args -> finish(null, "ioBroker nicht erreichbar: ${args.firstOrNull() ?: "Verbindung fehlgeschlagen"}") }
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

    companion object {
        const val INPUT_ID = "0_userdata.0.KlimaPilot.roomTemp.input"
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
    status: String?,
    onSubmit: (Double) -> Unit,
    onDeleteLast: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val value = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 5.0..40.0 }
    KlimaPilotAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Raumtemperatur", color = DialogText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fühler der Anlage: ${raw?.let { RoomTempModel.fmt(it) + " °C" } ?: "—"}", color = DialogText)
                Text("Angezeigt: ${shown?.let { RoomTempModel.fmt(it) + " °C" } ?: "—"}", color = DialogText, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        !ioBrokerReachable -> "ioBroker nicht erreichbar – es gilt die feste Korrektur."
                        model == null -> "Noch keine Messwerte – es gilt die feste Korrektur."
                        model.using != "none" && model.currentOffset() == null -> "Keine aktuellen Daten vom Skript – es gilt die feste Korrektur."
                        else -> model.describe().replaceFirstChar { it.uppercase() } + "."
                    },
                    color = DialogMuted, fontSize = 13.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5) },
                        label = { Text("Thermometer zeigt (°C)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DialogBlue, focusedLabelColor = DialogBlue, cursorColor = DialogBlue),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { value?.let { onSubmit(it); text = "" } }, enabled = value != null && raw != null && ioBrokerReachable) {
                        Text("Speichern", color = if (value != null && raw != null && ioBrokerReachable) DialogBlue else DialogMuted)
                    }
                }
                status?.let { Text(it, color = DialogBlue, fontSize = 13.sp) }
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
    val status: String?,
    val submit: (airconId: String, thermometer: Double, raw: Double) -> Unit,
    val delete: (airconId: String, sampleId: String) -> Unit,
)
