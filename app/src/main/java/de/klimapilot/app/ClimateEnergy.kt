package de.klimapilot.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.key
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
    DAY("Tag"), WEEK("Woche"), MONTH("Monat"), YEAR("Jahr"), ALL("Gesamt")
}

/** [drillDown]: the date a tap on this bar opens one level finer, if any. */
internal data class EnergyBar(val label: String, val kWh: Double, val drillDown: LocalDate? = null)

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

    private val monthShort = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")
    private val monthLong = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")
    private val weekdayShort = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

    private fun weekStart(d: LocalDate) = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Calendar bars for the period that contains [anchor]: hours of a day, days of a week or month, months of a year, or all years. */
    fun bars(period: EnergyPeriod, units: List<EnergyUnitData>, anchor: LocalDate, today: LocalDate = LocalDate.now()): List<EnergyBar> = when (period) {
        EnergyPeriod.DAY -> (0..23).map { h ->
            EnergyBar("%02d".format(Locale.ROOT, h), units.sumOf { it.hourly[hourKey(anchor.atTime(h, 0))] ?: 0.0 })
        }
        EnergyPeriod.WEEK -> {
            val monday = weekStart(anchor)
            (0..6).map { i -> val d = monday.plusDays(i.toLong()); EnergyBar(weekdayShort[i], daySum(units, d, d), d) }
        }
        EnergyPeriod.MONTH -> {
            val first = anchor.withDayOfMonth(1)
            (0 until first.lengthOfMonth()).map { i -> val d = first.plusDays(i.toLong()); EnergyBar("${d.dayOfMonth}", daySum(units, d, d), d) }
        }
        EnergyPeriod.YEAR -> (1..12).map { m ->
            val start = LocalDate.of(anchor.year, m, 1)
            EnergyBar(monthShort[m - 1], daySum(units, start, start.plusMonths(1).minusDays(1)), start)
        }
        EnergyPeriod.ALL -> {
            val firstYear = units.flatMap { it.daily.keys }.mapNotNull { it.take(4).toIntOrNull() }.minOrNull() ?: today.year
            (firstYear.coerceAtMost(today.year)..today.year).map { year ->
                EnergyBar(year.toString(), daySum(units, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)), LocalDate.of(year, 1, 1))
            }
        }
    }

    fun title(period: EnergyPeriod, anchor: LocalDate): String = when (period) {
        EnergyPeriod.DAY -> "${weekdayShort[anchor.dayOfWeek.value - 1]}, %02d.%02d.%d".format(Locale.ROOT, anchor.dayOfMonth, anchor.monthValue, anchor.year)
        EnergyPeriod.WEEK -> {
            val monday = weekStart(anchor); val sunday = monday.plusDays(6)
            "KW ${monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} · %02d.%02d.–%02d.%02d.%d".format(Locale.ROOT, monday.dayOfMonth, monday.monthValue, sunday.dayOfMonth, sunday.monthValue, sunday.year)
        }
        EnergyPeriod.MONTH -> "${monthLong[anchor.monthValue - 1]} ${anchor.year}"
        EnergyPeriod.YEAR -> anchor.year.toString()
        EnergyPeriod.ALL -> "Gesamter Zeitraum"
    }

    fun shift(period: EnergyPeriod, anchor: LocalDate, steps: Long): LocalDate = when (period) {
        EnergyPeriod.DAY -> anchor.plusDays(steps)
        EnergyPeriod.WEEK -> anchor.plusWeeks(steps)
        EnergyPeriod.MONTH -> anchor.plusMonths(steps)
        EnergyPeriod.YEAR -> anchor.plusYears(steps)
        EnergyPeriod.ALL -> anchor
    }

    /** The next coarser view for the "up" button: day → week → month → year → all. */
    fun coarser(period: EnergyPeriod): EnergyPeriod? = when (period) {
        EnergyPeriod.DAY -> EnergyPeriod.WEEK
        EnergyPeriod.WEEK -> EnergyPeriod.MONTH
        EnergyPeriod.MONTH -> EnergyPeriod.YEAR
        EnergyPeriod.YEAR -> EnergyPeriod.ALL
        EnergyPeriod.ALL -> null
    }

    /** How many periods the one containing [anchor] lies before the one containing [today] (0 = current). */
    fun stepsBack(period: EnergyPeriod, anchor: LocalDate, today: LocalDate): Long = when (period) {
        EnergyPeriod.DAY -> java.time.temporal.ChronoUnit.DAYS.between(anchor, today)
        EnergyPeriod.WEEK -> java.time.temporal.ChronoUnit.WEEKS.between(weekStart(anchor), weekStart(today))
        EnergyPeriod.MONTH -> java.time.temporal.ChronoUnit.MONTHS.between(anchor.withDayOfMonth(1), today.withDayOfMonth(1))
        EnergyPeriod.YEAR -> (today.year - anchor.year).toLong()
        EnergyPeriod.ALL -> 0L
    }.coerceAtLeast(0L)

    /** Whether the period containing [anchor] lies before the one containing [today]. */
    fun isBeforeCurrent(period: EnergyPeriod, anchor: LocalDate, today: LocalDate): Boolean = when (period) {
        EnergyPeriod.DAY -> anchor.isBefore(today)
        EnergyPeriod.WEEK -> weekStart(anchor).isBefore(weekStart(today))
        EnergyPeriod.MONTH -> anchor.withDayOfMonth(1).isBefore(today.withDayOfMonth(1))
        EnergyPeriod.YEAR -> anchor.year < today.year
        EnergyPeriod.ALL -> false
    }

    fun finer(period: EnergyPeriod): EnergyPeriod? = when (period) {
        EnergyPeriod.DAY -> null
        EnergyPeriod.WEEK, EnergyPeriod.MONTH -> EnergyPeriod.DAY
        EnergyPeriod.YEAR -> EnergyPeriod.MONTH
        EnergyPeriod.ALL -> EnergyPeriod.YEAR
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
        val socket = IoBrokerLease(url)
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
        val socket = IoBrokerLease(url)
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
                    val source = common.optString("source")
                    id.takeIf { it != SCRIPT_ID && common.optBoolean("enabled") && (source.contains(MARKER) || source.contains(LEGACY_MARKER)) }
                }
                fun next(index: Int) {
                    if (index >= others.size) {
                        // The earlier energy-only script KlimaPilot set up itself is removed for good.
                        socket.emit("delObject", LEGACY_SCRIPT_ID, IoBrokerAck {
                            finish(true, if (others.isEmpty()) message else "$message Eine ältere Kopie wurde angehalten.")
                        })
                        return
                    }
                    val update = JSONObject().put("common", JSONObject().put("enabled", false))
                    socket.emit("extendObject", others[index], update, IoBrokerAck { next(index + 1) })
                }
                next(0)
            })
        }

        fun write(existing: JSONObject?) {
            val common = existing?.optJSONObject("common")
            if (common != null && common.optString("source") == source && common.optBoolean("enabled")) {
                disableOtherCopies("Das KlimaPilot-Skript ist eingerichtet und aktuell.")
                return
            }
            val definition = JSONObject()
                .put("type", "script")
                .put("common", JSONObject()
                    .put("name", "KlimaPilot")
                    .put("engineType", "Javascript/js")
                    .put("engine", "system.adapter.javascript.0")
                    .put("source", source)
                    .put("enabled", true)
                    .put("debug", false)
                    .put("verbose", false))
                .put("native", JSONObject())
            socket.emit("setObject", SCRIPT_ID, definition, IoBrokerAck setCallback@{ setArgs ->
                if (err(setArgs) != null) { finish(false, "Skript konnte nicht angelegt werden: ${err(setArgs)}"); return@setCallback }
                disableOtherCopies(if (existing == null) "KlimaPilot-Skript eingerichtet und gestartet." else "KlimaPilot-Skript aktualisiert.")
            })
        }

        socket.on("connect") {
            socket.emit("authenticate", IoBrokerAck authCallback@{ authArgs ->
                if (authArgs.firstOrNull() as? Boolean != true) { finish(false, "ioBroker hat die Verbindung nicht authentifiziert."); return@authCallback }
                socket.emit("getObject", SCRIPT_ID, IoBrokerAck getCallback@{ getArgs ->
                    val existing = asJson(getArgs.getOrNull(1))
                    if (updateOnly && existing == null) {
                        // Not installed yet; only replace the earlier energy script if that exists.
                        socket.emit("getObject", LEGACY_SCRIPT_ID, IoBrokerAck legacyCallback@{ legacyArgs ->
                            if (asJson(legacyArgs.getOrNull(1)) == null) { finish(true, "Nicht eingerichtet."); return@legacyCallback }
                            write(null)
                        })
                        return@getCallback
                    }
                    write(existing)
                })
            })
        }
        socket.on("connect_error") { args -> finish(false, "ioBroker nicht erreichbar: ${args.firstOrNull() ?: "Verbindung fehlgeschlagen"}") }
        socket.connect()
    }

    companion object {
        const val ASSET = "klimapilot.js"
        const val SCRIPT_ID = "script.js.common.KlimaPilot"
        const val MARKER = "KlimaPilot – ioBroker-Skript"
        /** Earlier energy-only script; replaced by [SCRIPT_ID]. */
        const val LEGACY_SCRIPT_ID = "script.js.common.KlimaPilot_Energie"
        const val LEGACY_MARKER = "KlimaPilot – Energieaufzeichnung für ioBroker"
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
    var period by remember { mutableStateOf(EnergyPeriod.WEEK) }
    var showInfo by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    var anchor by remember { mutableStateOf(today) }
    // Periods left by tapping a bar; the back gesture returns to them one level at a time.
    var drillStack by remember { mutableStateOf(listOf<EnergyPeriod>()) }
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
    val bars = EnergyMath.bars(period, shown, anchor, today)
    val factor = if (inEuro) price else 1.0
    val unitLabel = if (inEuro) "€" else "kWh"

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogNavigationBar(MaterialTheme.colorScheme.background)
        BackHandler(enabled = drillStack.isNotEmpty()) {
            period = drillStack.last()
            drillStack = drillStack.dropLast(1)
        }
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
                        EnergyPill(p.label, period == p, Modifier.weight(1f)) { period = p; drillStack = emptyList() }
                    }
                }
                if (period != EnergyPeriod.ALL) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        EnergyPill("‹", false) { anchor = EnergyMath.shift(period, anchor, -1) }
                        Text(
                            EnergyMath.title(period, anchor),
                            modifier = Modifier.weight(1f).clickable { anchor = today },
                            textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 15.sp
                        )
                        if (EnergyMath.isBeforeCurrent(period, anchor, today)) {
                            EnergyPill("›", false) { anchor = EnergyMath.shift(period, anchor, 1) }
                        } else {
                            EnergyPill("›", false, Modifier.alpha(0.3f)) {}
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EnergyPill("kWh", !inEuro) { inEuro = false }
                    EnergyPill("Kosten (€)", inEuro) { inEuro = true }
                }

                val total = bars.sumOf { it.kWh } * factor
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Summe im Zeitraum: ${formatNumber(total, 2)} $unitLabel", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("ⓘ", fontSize = 20.sp, color = EnergyBlue, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { showInfo = true }.padding(horizontal = 8.dp, vertical = 2.dp))
                }
                if (showInfo) AlertDialog(
                    onDismissRequest = { showInfo = false },
                    confirmButton = { TextButton(onClick = { showInfo = false }) { Text("OK") } },
                    title = { Text("Energieansicht") },
                    text = {
                        Text(
                            "Einen Balken antippen, um eine Ebene tiefer zu gehen: Jahr → Monat → Tag. „↑“ über dem Diagramm oder die Zurück-Geste geht wieder eine Ebene höher. " +
                                "Seitlich wischen oder mit ‹ › blättern, den Titel antippen springt zurück zum aktuellen Zeitraum.\n\n" +
                                "Die Anlage meldet ihren Verbrauch in 0,25-kWh-Schritten; jeder Schritt wird auf die Laufzeit davor verteilt. " +
                                "Stundenwerte werden 31 Tage aufbewahrt, Tageswerte dauerhaft. Kosten ohne Grundgebühr; " +
                                "der Strompreis gilt für beide Smartphones."
                        )
                    }
                )
                // One level up: back to where a bar tap came from, otherwise the next coarser period.
                val up = drillStack.lastOrNull() ?: EnergyMath.coarser(period)
                if (up != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(EnergyBlue.copy(alpha = 0.10f))
                            .clickable {
                                if (drillStack.isNotEmpty()) drillStack = drillStack.dropLast(1)
                                period = up
                            }
                            .padding(start = 6.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Default.KeyboardArrowUp, contentDescription = null,
                            tint = EnergyBlue, modifier = Modifier.size(20.dp)
                        )
                        Text(up.label, color = EnergyBlue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                // Swipe sideways through the periods; the last page is the current one (no future pages).
                key(period) {
                    val pageCount = if (period == EnergyPeriod.ALL) 1 else ENERGY_PAGES
                    val last = pageCount - 1
                    fun pageOf(date: LocalDate) = (last - EnergyMath.stepsBack(period, date, today).coerceAtMost(last.toLong())).toInt()
                    fun anchorOf(page: Int) = if (period == EnergyPeriod.ALL) today else EnergyMath.shift(period, today, (page - last).toLong())
                    val pager = rememberPagerState(initialPage = pageOf(anchor)) { pageCount }
                    LaunchedEffect(pager.settledPage) {
                        val settled = anchorOf(pager.settledPage)
                        if (pageOf(anchor) != pager.settledPage) anchor = settled
                    }
                    LaunchedEffect(anchor) {
                        val target = pageOf(anchor)
                        if (pager.currentPage != target) pager.animateScrollToPage(target)
                    }
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth(), pageSpacing = 16.dp) { page ->
                        val pageAnchor = anchorOf(page)
                        val pageBars = if (pageAnchor == anchor) bars else EnergyMath.bars(period, shown, pageAnchor, today)
                        EnergyChart(pageBars.map { it.label to it.kWh * factor }, unitLabel) { index ->
                            val target = pageBars.getOrNull(index)?.drillDown ?: return@EnergyChart
                            val finer = EnergyMath.finer(period) ?: return@EnergyChart
                            drillStack = drillStack + period
                            period = finer
                            anchor = target
                        }
                    }
                }
                if (period == EnergyPeriod.DAY && anchor.isBefore(today.minusDays(30))) {
                    Text("Stundenwerte werden nur für die letzten 31 Tage aufbewahrt.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (status.isNotBlank()) Text(status, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                shown.forEach { unit ->
                    val label = unit.name.ifBlank { unit.airconId } + if (unit.room.isNotBlank()) " (${unit.room})" else ""
                    val last = unit.lastPollAt?.let { runCatching { java.time.OffsetDateTime.parse(it).atZoneSameInstant(java.time.ZoneId.systemDefault()) }.getOrNull() }
                    val lastText = last?.let { "%02d.%02d. %02d:%02d".format(Locale.ROOT, it.dayOfMonth, it.monthValue, it.hour, it.minute) } ?: "noch nie"
                    Text("$label – zuletzt gelesen: $lastText" + (unit.lastError?.let { "\nFehler: $it" } ?: ""), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
                Spacer(Modifier.height(96.dp))
            }
        }
    }
}

private const val ENERGY_PAGES = 4000

@Composable
private fun EnergyChart(values: List<Pair<String, Double>>, unitLabel: String, onBarTap: (Int) -> Unit) {
    val max = values.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    val barColor = EnergyBlue
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("max. ${formatNumber(max, 2)} $unitLabel", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(200.dp).pointerInput(values.size) {
            detectTapGestures { offset ->
                val slot = size.width.toFloat() / values.size.coerceAtLeast(1)
                onBarTap((offset.x / slot).toInt().coerceIn(0, values.size - 1))
            }
        }) {
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
