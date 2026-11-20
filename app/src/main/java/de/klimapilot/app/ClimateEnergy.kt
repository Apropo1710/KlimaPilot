package de.klimapilot.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

internal enum class EnergyPeriod(val label: String) {
    HOURS("Stunden"), DAYS("Tage"), WEEKS("Wochen"), MONTHS("Monate"), YEARS("Jahre")
}

internal data class EnergyBar(val label: String, val kWh: Double)

internal data class EnergyUnitData(
    val airconId: String,
    val name: String,
    val room: String,
    val hourly: Map<String, Double>,
    val daily: Map<String, Double>,
    val lastPollAt: String?,
    val lastError: String?,
    val lastRunKwh: Double? = null,
)

/** Turns the hourly and daily values the ioBroker script records into chart bars. */
internal object EnergyMath {
    const val DEFAULT_PRICE_EUR_PER_KWH = 0.33

    fun parseUnit(json: JSONObject): EnergyUnitData? {
        val airconId = json.optString("airconId").ifBlank { return null }
        fun map(name: String): Map<String, Double> {
            val obj = json.optJSONObject(name) ?: return emptyMap()
            return obj.keys().asSequence().associateWith { obj.optDouble(it, 0.0) }
        }
        return EnergyUnitData(
            airconId = airconId,
            name = json.optString("name"),
            room = json.optString("room"),
            hourly = map("hourly"),
            daily = map("daily"),
            lastPollAt = json.optString("lastPollAt").ifBlank { null },
            lastError = json.optString("lastError").ifBlank { null },
            lastRunKwh = json.optDouble("lastRunKwh", Double.NaN).takeIf { !it.isNaN() },
        )
    }

    private fun dayKey(d: LocalDate) = "%04d-%02d-%02d".format(Locale.ROOT, d.year, d.monthValue, d.dayOfMonth)
    private fun hourKey(t: LocalDateTime) = dayKey(t.toLocalDate()) + "T%02d".format(Locale.ROOT, t.hour)

    private fun daySum(units: List<EnergyUnitData>, from: LocalDate, toInclusive: LocalDate): Double {
        var sum = 0.0
        var d = from
        while (!d.isAfter(toInclusive)) {
            val key = dayKey(d)
            units.forEach { sum += it.daily[key] ?: 0.0 }
            d = d.plusDays(1)
        }
        return sum
    }

    fun bars(period: EnergyPeriod, units: List<EnergyUnitData>, now: LocalDateTime): List<EnergyBar> {
        val today = now.toLocalDate()
        return when (period) {
            EnergyPeriod.HOURS -> {
                val current = now.truncatedTo(ChronoUnit.HOURS)
                (23 downTo 0).map { back ->
                    val t = current.minusHours(back.toLong())
                    EnergyBar("%02d".format(Locale.ROOT, t.hour), units.sumOf { it.hourly[hourKey(t)] ?: 0.0 })
                }
            }
            EnergyPeriod.DAYS -> (13 downTo 0).map { back ->
                val d = today.minusDays(back.toLong())
                EnergyBar("%02d.%02d.".format(Locale.ROOT, d.dayOfMonth, d.monthValue), daySum(units, d, d))
            }
            EnergyPeriod.WEEKS -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                (11 downTo 0).map { back ->
                    val start = monday.minusWeeks(back.toLong())
                    EnergyBar("KW${start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}", daySum(units, start, start.plusDays(6)))
                }
            }
            EnergyPeriod.MONTHS -> {
                val first = today.withDayOfMonth(1)
                (11 downTo 0).map { back ->
                    val start = first.minusMonths(back.toLong())
                    EnergyBar("%02d/%02d".format(Locale.ROOT, start.monthValue, start.year % 100), daySum(units, start, start.plusMonths(1).minusDays(1)))
                }
            }
            EnergyPeriod.YEARS -> {
                val firstYear = units.flatMap { it.daily.keys }.mapNotNull { it.take(4).toIntOrNull() }.minOrNull() ?: today.year
                (firstYear.coerceAtMost(today.year)..today.year).map { year ->
                    EnergyBar(year.toString(), daySum(units, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)))
                }
            }
        }
    }

    /**
     * Today's consumption: what the script recorded today plus what the unit counted since the
     * script's last reading (the live run counter is newer than the 5-minute recording).
     */
    fun todayKwh(unit: EnergyUnitData, liveRunKwh: Double?, now: LocalDateTime): Double {
        val recorded = unit.daily[dayKey(now.toLocalDate())] ?: 0.0
        val last = unit.lastRunKwh ?: return recorded
        val live = liveRunKwh ?: return recorded
        val polledToday = unit.lastPollAt
            ?.let { runCatching { java.time.OffsetDateTime.parse(it).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate() }.getOrNull() } == now.toLocalDate()
        if (!polledToday) return recorded
        return recorded + if (live >= last) live - last else live
    }

    /** The configuration the ioBroker script polls: every paired unit plus the price. */
    fun config(devices: List<UserDevice>, priceEurPerKwh: Double): JSONObject = JSONObject()
        .put("priceEurPerKwh", priceEurPerKwh)
        .put("units", JSONArray().apply {
            devices.filter { !it.demoMode && it.wfRacAirconId.isNotBlank() && it.wfRacHost.isNotBlank() }
                .distinctBy { it.wfRacAirconId }
                .sortedBy { it.wfRacAirconId }
                .forEach {
                    put(JSONObject().put("airconId", it.wfRacAirconId).put("host", it.wfRacHost.trim())
                        .put("name", it.name).put("room", it.room))
                }
        })
}

internal data class EnergyFetch(val priceEurPerKwh: Double, val units: List<EnergyUnitData>)

/**
 * Short-lived ioBroker connection of its own: keeps the energy configuration current and reads
 * the recorded values. The configuration is only written when it actually changed.
 */
internal class EnergyIoBroker {
    private val main = Handler(Looper.getMainLooper())

    fun exchange(address: String, devices: List<UserDevice>, newPrice: Double?, callback: (EnergyFetch?, String?) -> Unit) {
        val url = address.trim().trimEnd('/')
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            callback(null, "Keine ioBroker-Adresse hinterlegt.")
            return
        }
        val socket = IoBrokerSocket(url, "name=klimapilot.energy")
        val done = AtomicBoolean(false)
        fun finish(result: EnergyFetch?, error: String?) {
            if (!done.compareAndSet(false, true)) return
            runCatching { socket.off() }
            runCatching { socket.disconnect() }
            main.post { callback(result, error) }
        }
        main.postDelayed({ finish(null, "ioBroker antwortet nicht.") }, 15_000)
        fun err(args: Array<Any?>) = args.firstOrNull()?.takeUnless { it == JSONObject.NULL }
        socket.on("connect") {
            socket.emit("authenticate", IoBrokerAck authCallback@{ authArgs ->
                if (authArgs.firstOrNull() as? Boolean != true) { finish(null, "ioBroker hat die Verbindung nicht authentifiziert."); return@authCallback }
                socket.emit("getState", CONFIG_ID, IoBrokerAck configCallback@{ configArgs ->
                    val remote = ((configArgs.getOrNull(1) as? JSONObject)?.opt("val") as? String)
                        ?.let { runCatching { JSONObject(it) }.getOrNull() }
                    val remotePrice = remote?.optDouble("priceEurPerKwh", Double.NaN)?.takeIf { !it.isNaN() }
                    val price = newPrice ?: remotePrice ?: EnergyMath.DEFAULT_PRICE_EUR_PER_KWH
                    val config = EnergyMath.config(devices, price)
                    // Another phone may know units this one has not synced yet: keep them.
                    remote?.optJSONArray("units")?.let { known ->
                        val ids = (0 until config.getJSONArray("units").length()).map { config.getJSONArray("units").getJSONObject(it).optString("airconId") }.toSet()
                        for (i in 0 until known.length()) {
                            val unit = known.optJSONObject(i) ?: continue
                            if (unit.optString("airconId") !in ids) config.getJSONArray("units").put(unit)
                        }
                    }
                    fun readUnits() {
                        socket.emit("getStates", "$ROOT.units.*", IoBrokerAck statesCallback@{ args ->
                            if (err(args) != null) { finish(null, "Energiedaten konnten nicht gelesen werden: ${err(args)}"); return@statesCallback }
                            val states = when (val raw = args.getOrNull(1)) {
                                is JSONObject -> raw
                                is Map<*, *> -> JSONObject().apply { raw.forEach { (key, value) -> if (key is String) put(key, value) } }
                                else -> JSONObject()
                            }
                            val units = states.keys().asSequence().mapNotNull { id ->
                                val value = states.optJSONObject(id)?.opt("val") as? String ?: return@mapNotNull null
                                runCatching { JSONObject(value) }.getOrNull()?.let { EnergyMath.parseUnit(it) }
                            }.toList()
                            finish(EnergyFetch(price, units), null)
                        })
                    }
                    val unchanged = remote != null && remotePrice == price &&
                        remote.optJSONArray("units")?.toString() == config.getJSONArray("units").toString()
                    if (unchanged) { readUnits(); return@configCallback }
                    val definition = JSONObject().put("type", "state")
                        .put("common", JSONObject().put("name", "KlimaPilot Energie – Konfiguration").put("type", "string").put("role", "json").put("read", true).put("write", true))
                        .put("native", JSONObject())
                    socket.emit("setObject", CONFIG_ID, definition, IoBrokerAck objectCallback@{ objectArgs ->
                        if (err(objectArgs) != null) { finish(null, "Energie-Konfiguration konnte nicht angelegt werden: ${err(objectArgs)}"); return@objectCallback }
                        socket.emit("setState", CONFIG_ID, JSONObject().put("val", config.toString()).put("ack", false), IoBrokerAck stateCallback@{ stateArgs ->
                            if (err(stateArgs) != null) { finish(null, "Energie-Konfiguration konnte nicht gespeichert werden: ${err(stateArgs)}"); return@stateCallback }
                            readUnits()
                        })
                    })
                })
            })
        }
        socket.on("connect_error") { args -> finish(null, "ioBroker nicht erreichbar: ${args.firstOrNull() ?: "Verbindung fehlgeschlagen"}") }
        socket.connect()
    }

    companion object {
        const val ROOT = "0_userdata.0.KlimaPilot.energy"
        const val CONFIG_ID = "$ROOT.config"
        const val PREF_PRICE = "energy_price_eur_kwh"

        fun cachedPrice(context: Context): Double =
            context.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE)
                .getFloat(PREF_PRICE, EnergyMath.DEFAULT_PRICE_EUR_PER_KWH.toFloat()).toDouble()

        fun cachePrice(context: Context, price: Double) {
            context.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE).edit().putFloat(PREF_PRICE, price.toFloat()).apply()
        }
    }
}

/**
 * Installs or updates the energy script in the ioBroker JavaScript adapter. It only ever writes
 * its own script object; other copies of the energy script (recognised by their header line) are
 * disabled so two scripts never count the same consumption.
 */
internal class EnergyScriptInstaller(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())

    private fun source(): String = context.assets.open(ASSET).bufferedReader().use { it.readText() }

    /** [updateOnly]: change nothing unless the script was installed by KlimaPilot before. */
    fun install(address: String, updateOnly: Boolean, callback: (Boolean, String) -> Unit) {
        val url = address.trim().trimEnd('/')
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) { callback(false, "Keine ioBroker-Adresse hinterlegt."); return }
        val source = runCatching { source() }.getOrElse { callback(false, "Skript fehlt in der App."); return }
        val socket = IoBrokerSocket(url, "name=klimapilot.energy-script")
        val done = AtomicBoolean(false)
        fun finish(ok: Boolean, message: String) {
            if (!done.compareAndSet(false, true)) return
            runCatching { socket.off() }
            runCatching { socket.disconnect() }
            main.post { callback(ok, message) }
        }
        main.postDelayed({ finish(false, "ioBroker antwortet nicht.") }, 20_000)
        fun err(args: Array<Any?>) = args.firstOrNull()?.takeUnless { it == JSONObject.NULL }
        fun asJson(raw: Any?): JSONObject? = when (raw) {
            is JSONObject -> raw
            is Map<*, *> -> JSONObject().apply { raw.forEach { (k, v) -> if (k is String) put(k, v) } }
            else -> null
        }

        fun disableOtherCopies(message: String) {
            val params = JSONObject().put("startkey", "script.js.").put("endkey", "script.js.\u9999")
            socket.emit("getObjectView", "system", "script", params, IoBrokerAck viewCallback@{ args ->
                val rows = asJson(args.getOrNull(1))?.optJSONArray("rows")
                if (err(args) != null || rows == null) { finish(true, message); return@viewCallback }
                val others = (0 until rows.length()).mapNotNull { i ->
                    val row = rows.optJSONObject(i) ?: return@mapNotNull null
                    val id = row.optString("id")
                    val common = row.optJSONObject("value")?.optJSONObject("common") ?: return@mapNotNull null
                    id.takeIf { it != SCRIPT_ID && common.optBoolean("enabled") && common.optString("source").contains(MARKER) }
                }
                fun next(index: Int) {
                    if (index >= others.size) {
                        finish(true, if (others.isEmpty()) message else "$message Eine ältere Kopie wurde angehalten.")
                        return
                    }
                    val update = JSONObject().put("common", JSONObject().put("enabled", false))
                    socket.emit("extendObject", others[index], update, IoBrokerAck { next(index + 1) })
                }
                next(0)
            })
        }

        socket.on("connect") {
            socket.emit("authenticate", IoBrokerAck authCallback@{ authArgs ->
                if (authArgs.firstOrNull() as? Boolean != true) { finish(false, "ioBroker hat die Verbindung nicht authentifiziert."); return@authCallback }
                socket.emit("getObject", SCRIPT_ID, IoBrokerAck getCallback@{ getArgs ->
                    val existing = asJson(getArgs.getOrNull(1))
                    if (updateOnly && existing == null) { finish(true, "Nicht eingerichtet."); return@getCallback }
                    val common = existing?.optJSONObject("common")
                    if (common != null && common.optString("source") == source && common.optBoolean("enabled")) {
                        disableOtherCopies("Das Energieskript ist eingerichtet und aktuell.")
                        return@getCallback
                    }
                    val definition = JSONObject()
                        .put("type", "script")
                        .put("common", JSONObject()
                            .put("name", "KlimaPilot Energie")
                            .put("engineType", "Javascript/js")
                            .put("engine", "system.adapter.javascript.0")
                            .put("source", source)
                            .put("enabled", true)
                            .put("debug", false)
                            .put("verbose", false))
                        .put("native", JSONObject())
                    socket.emit("setObject", SCRIPT_ID, definition, IoBrokerAck setCallback@{ setArgs ->
                        if (err(setArgs) != null) { finish(false, "Skript konnte nicht angelegt werden: ${err(setArgs)}"); return@setCallback }
                        disableOtherCopies(if (existing == null) "Energieskript eingerichtet und gestartet." else "Energieskript aktualisiert.")
                    })
                })
            })
        }
        socket.on("connect_error") { args -> finish(false, "ioBroker nicht erreichbar: ${args.firstOrNull() ?: "Verbindung fehlgeschlagen"}") }
        socket.connect()
    }

    companion object {
        const val ASSET = "klimapilot-energie.js"
        const val SCRIPT_ID = "script.js.common.KlimaPilot_Energie"
        const val MARKER = "KlimaPilot – Energieaufzeichnung für ioBroker"
    }
}

private val EnergyBlue = Color(0xFF1769AA)

/** Same look as the selection buttons in the rest of the app: blue when selected. */
@Composable
private fun EnergyPill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        color = if (selected) EnergyBlue else Color(0xFFE3EEF7),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            color = if (selected) Color.White else Color(0xFF123B60),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatNumber(value: Double, decimals: Int) = "%.${decimals}f".format(Locale.GERMANY, value)

@Composable
internal fun EnergyDialog(devices: List<UserDevice>, selectedDevice: UserDevice?, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE) }
    val io = remember { EnergyIoBroker() }
    var period by remember { mutableStateOf(EnergyPeriod.DAYS) }
    var inEuro by remember { mutableStateOf(false) }
    // null = all units
    var selectedUnit by remember { mutableStateOf(selectedDevice?.wfRacAirconId?.takeIf { it.isNotBlank() }) }
    var price by remember { mutableStateOf(EnergyIoBroker.cachedPrice(context)) }
    var priceText by remember { mutableStateOf(formatNumber(price, 3)) }
    var units by remember { mutableStateOf<List<EnergyUnitData>?>(null) }
    var status by remember { mutableStateOf("Energiedaten werden aus ioBroker geladen …") }
    var refresh by remember { mutableStateOf(0) }
    var pendingPrice by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(refresh) {
        val address = prefs.getString("iobroker_server", null).orEmpty()
        io.exchange(address, devices, pendingPrice) { result, error ->
            if (result != null) {
                price = result.priceEurPerKwh
                EnergyIoBroker.cachePrice(context, price)
                if (pendingPrice == null) priceText = formatNumber(price, 3)
                pendingPrice = null
                units = result.units
                status = if (result.units.isEmpty()) "Noch keine Energiedaten. Das Energieskript wird in den Einstellungen unter „Energieaufzeichnung“ eingerichtet; danach dauert es bis zu 5 Minuten." else ""
            } else status = error ?: "Energiedaten konnten nicht geladen werden."
        }
    }

    val shown = units.orEmpty().filter { selectedUnit == null || it.airconId == selectedUnit }
    val pairedDevices = devices.filter { !it.demoMode && it.wfRacAirconId.isNotBlank() }.distinctBy { it.wfRacAirconId }
    val bars = EnergyMath.bars(period, shown, LocalDateTime.now())
    val factor = if (inEuro) price else 1.0
    val unitLabel = if (inEuro) "€" else "kWh"

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Energie", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Schließen") }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pairedDevices.forEach { device ->
                        EnergyPill(device.room.ifBlank { device.name }, selectedUnit == device.wfRacAirconId) { selectedUnit = device.wfRacAirconId }
                    }
                    EnergyPill("Alle", selectedUnit == null) { selectedUnit = null }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EnergyPeriod.entries.forEach { p ->
                        EnergyPill(p.label, period == p, Modifier.weight(1f)) { period = p }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EnergyPill("kWh", !inEuro) { inEuro = false }
                    EnergyPill("Kosten (€)", inEuro) { inEuro = true }
                }

                val total = bars.sumOf { it.kWh } * factor
                Text("Summe im Zeitraum: ${formatNumber(total, 2)} $unitLabel", fontWeight = FontWeight.SemiBold)
                EnergyChart(bars.map { it.label to it.kWh * factor }, unitLabel)

                if (status.isNotBlank()) Text(status, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                shown.forEach { unit ->
                    val label = unit.name.ifBlank { unit.airconId } + if (unit.room.isNotBlank()) " (${unit.room})" else ""
                    val last = unit.lastPollAt?.let { runCatching { java.time.OffsetDateTime.parse(it).atZoneSameInstant(java.time.ZoneId.systemDefault()) }.getOrNull() }
                    val lastText = last?.let { "%02d.%02d. %02d:%02d".format(Locale.ROOT, it.dayOfMonth, it.monthValue, it.hour, it.minute) } ?: "noch nie"
                    Text("$label – zuletzt gelesen: $lastText" + (unit.lastError?.let { "\nFehler: $it" } ?: ""), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "Gezählt wird in 0,25-kWh-Schritten, so wie die Anlage es meldet. Die Stundenansicht ist deshalb grob; Stundenwerte werden 31 Tage aufbewahrt, Tageswerte dauerhaft.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Strompreis", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("€ pro kWh") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        val parsed = priceText.trim().replace(',', '.').toDoubleOrNull()
                        if (parsed == null || parsed < 0 || parsed > 5) {
                            status = "Bitte einen Preis zwischen 0 und 5 € pro kWh eingeben."
                        } else {
                            pendingPrice = parsed
                            price = parsed
                            EnergyIoBroker.cachePrice(context, parsed)
                            status = "Strompreis wird gespeichert …"
                            refresh++
                        }
                    }) { Text("Speichern") }
                }
                Text("Ohne Grundgebühr. Der Preis gilt für beide Smartphones und wird in ioBroker gespeichert.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EnergyChart(values: List<Pair<String, Double>>, unitLabel: String) {
    val max = values.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    val barColor = EnergyBlue
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("max. ${formatNumber(max, 2)} $unitLabel", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val gap = 4.dp.toPx()
            val slot = size.width / values.size.coerceAtLeast(1)
            val barWidth = (slot - gap).coerceAtLeast(1f)
            drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            values.forEachIndexed { index, (_, value) ->
                val h = (value / max * size.height).toFloat()
                if (h > 0f) drawRoundRect(
                    color = barColor,
                    topLeft = Offset(index * slot + gap / 2, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
            }
        }
        // Labels: at most about eight, evenly spread, so they stay readable.
        val step = ((values.size + 7) / 8).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth()) {
            values.forEachIndexed { index, (label, _) ->
                Text(
                    if (index % step == 0) label else "",
                    modifier = Modifier.weight(1f),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    color = Color.Unspecified
                )
            }
        }
    }
}
