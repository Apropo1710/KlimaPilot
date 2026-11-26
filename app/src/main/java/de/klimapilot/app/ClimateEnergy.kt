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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
internal data class EnergyBar(val label: String, val kWh: Double, val drillDown: LocalDate? = null, val outdoor: Double? = null, val indoor: Double? = null)

internal data class EnergyUnitData(
    val airconId: String,
    val name: String,
    val room: String,
    val hourly: Map<String, Double>,
    val daily: Map<String, Double>,
    val lastPollAt: String?,
    val lastError: String?,
    val lastRunKwh: Double? = null,
    /** Mean outdoor temperature per hour (31 days) and per day (kept), in °C. */
    val outdoorHourly: Map<String, Double> = emptyMap(),
    val outdoorDaily: Map<String, Double> = emptyMap(),
    /** Mean room temperature as shown in the app (sensor minus learned offset) per hour and day, since 0.5.80. */
    val indoorHourly: Map<String, Double> = emptyMap(),
    val indoorDaily: Map<String, Double> = emptyMap(),
    /** Operating minutes counted by the script. */
    val runMinutes: Long = 0L,
    /** Calendar days on which the unit ran at least once (for the filter reminder). */
    val runDays: Int = 0,
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
            outdoorHourly = map("outdoorHourly"),
            outdoorDaily = map("outdoorDaily"),
            indoorHourly = map("indoorHourly"),
            indoorDaily = map("indoorDaily"),
            runMinutes = json.optLong("runMinutes", 0L),
            runDays = json.optInt("runDays", 0),
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

    /** Mean of the recorded outdoor temperatures of all units for the given keys, or null without data. */
    private fun outdoorMean(units: List<EnergyUnitData>, keys: List<String>, hourly: Boolean): Double? {
        val values = units.flatMap { unit -> keys.mapNotNull { (if (hourly) unit.outdoorHourly else unit.outdoorDaily)[it] } }
        return values.takeIf { it.isNotEmpty() }?.average()
    }

    /** Mean of the recorded room temperatures for the given keys, or null without data. */
    private fun indoorMean(units: List<EnergyUnitData>, keys: List<String>, hourly: Boolean): Double? {
        val values = units.flatMap { unit -> keys.mapNotNull { (if (hourly) unit.indoorHourly else unit.indoorDaily)[it] } }
        return values.takeIf { it.isNotEmpty() }?.average()
    }

    private fun dayKeys(from: LocalDate, toInclusive: LocalDate): List<String> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(toInclusive) }.map(::dayKey).toList()

    private val monthShort = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")
    private val monthLong = listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")
    private val weekdayShort = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

    private fun weekStart(d: LocalDate) = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Calendar bars for the period that contains [anchor]: hours of a day, days of a week or month, months of a year, or all years. */
    fun bars(period: EnergyPeriod, units: List<EnergyUnitData>, anchor: LocalDate, today: LocalDate = LocalDate.now()): List<EnergyBar> = when (period) {
        EnergyPeriod.DAY -> (0..23).map { h ->
            val key = hourKey(anchor.atTime(h, 0))
            EnergyBar("%02d".format(Locale.ROOT, h), units.sumOf { it.hourly[key] ?: 0.0 }, outdoor = outdoorMean(units, listOf(key), hourly = true), indoor = indoorMean(units, listOf(key), hourly = true))
        }
        EnergyPeriod.WEEK -> {
            val monday = weekStart(anchor)
            (0..6).map { i -> val d = monday.plusDays(i.toLong()); EnergyBar(weekdayShort[i], daySum(units, d, d), d, outdoorMean(units, listOf(dayKey(d)), hourly = false), indoorMean(units, listOf(dayKey(d)), hourly = false)) }
        }
        EnergyPeriod.MONTH -> {
            val first = anchor.withDayOfMonth(1)
            (0 until first.lengthOfMonth()).map { i -> val d = first.plusDays(i.toLong()); EnergyBar("${d.dayOfMonth}", daySum(units, d, d), d, outdoorMean(units, listOf(dayKey(d)), hourly = false), indoorMean(units, listOf(dayKey(d)), hourly = false)) }
        }
        EnergyPeriod.YEAR -> (1..12).map { m ->
            val start = LocalDate.of(anchor.year, m, 1)
            val end = start.plusMonths(1).minusDays(1)
            EnergyBar(monthShort[m - 1], daySum(units, start, end), start, outdoorMean(units, dayKeys(start, end), hourly = false), indoorMean(units, dayKeys(start, end), hourly = false))
        }
        EnergyPeriod.ALL -> {
            val firstYear = units.flatMap { it.daily.keys }.mapNotNull { it.take(4).toIntOrNull() }.minOrNull() ?: today.year
            (firstYear.coerceAtMost(today.year)..today.year).map { year ->
                val start = LocalDate.of(year, 1, 1); val end = LocalDate.of(year, 12, 31)
                EnergyBar(year.toString(), daySum(units, start, end), start, outdoorMean(units, dayKeys(start, end), hourly = false), indoorMean(units, dayKeys(start, end), hourly = false))
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

    fun exchange(address: String, devices: List<UserDevice>, newPrice: Double?, callback: (EnergyFetch?, String?) -> Unit) =
        IoBrokerRetry.wrap(callback) { cb -> exchangeOnce(address, devices, newPrice, cb) }

    private fun exchangeOnce(address: String, devices: List<UserDevice>, newPrice: Double?, callback: (EnergyFetch?, String?) -> Unit) {
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
                IoBrokerMessages.authError(authArgs)?.let { finish(null, it); return@authCallback }
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
        socket.on("connect_error") { args -> finish(null, IoBrokerMessages.connectError(args)) }
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

        /**
         * Removes every other copy of the script (found by its marker text, its old name or the
         * old id), enabled or not. If ioBroker refuses the deletion, the copy is at least stopped,
         * and the result says so, so it can be deleted by hand.
         */
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
                    val name = common.optString("name").replace('_', ' ').trim()
                    id.takeIf {
                        it != SCRIPT_ID && (it == LEGACY_SCRIPT_ID || name.equals("KlimaPilot Energie", ignoreCase = true) ||
                            source.contains(MARKER) || source.contains(LEGACY_MARKER))
                    }?.let { it to common.optString("name").ifBlank { it } }
                }
                val stuck = mutableListOf<String>()
                fun next(index: Int) {
                    if (index >= others.size) {
                        val text = when {
                            stuck.isNotEmpty() -> "$message Die alte Kopie „${stuck.joinToString()}“ konnte nicht gelöscht werden und wurde angehalten – bitte in ioBroker löschen."
                            others.isNotEmpty() -> "$message Alte Kopie „${others.joinToString { it.second }}“ wurde entfernt."
                            else -> message
                        }
                        finish(true, text)
                        return
                    }
                    val (id, name) = others[index]
                    socket.emit("delObject", id, IoBrokerAck { delArgs ->
                        // Check that it is really gone; otherwise stop it so it cannot run twice.
                        socket.emit("getObject", id, IoBrokerAck { checkArgs ->
                            if (err(delArgs) == null && asJson(checkArgs.getOrNull(1)) == null) { next(index + 1); return@IoBrokerAck }
                            stuck += name
                            val update = JSONObject().put("common", JSONObject().put("enabled", false))
                            socket.emit("extendObject", id, update, IoBrokerAck { next(index + 1) })
                        })
                    })
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
            fun store() {
                socket.emit("setObject", SCRIPT_ID, definition, IoBrokerAck setCallback@{ setArgs ->
                    if (err(setArgs) != null) { finish(false, "Skript konnte nicht angelegt werden: ${err(setArgs)}"); return@setCallback }
                    disableOtherCopies(if (existing == null) "KlimaPilot-Skript eingerichtet und gestartet." else "KlimaPilot-Skript aktualisiert.")
                })
            }
            // A running script is stopped first and only then started with the new code, so that
            // no old run keeps going next to the new one.
            if (common != null && common.optBoolean("enabled")) {
                val stop = JSONObject().put("common", JSONObject().put("enabled", false))
                socket.emit("extendObject", SCRIPT_ID, stop, IoBrokerAck { main.postDelayed({ if (!done.get()) store() }, 3_000) })
            } else store()
        }

        socket.on("connect") {
            socket.emit("authenticate", IoBrokerAck authCallback@{ authArgs ->
                IoBrokerMessages.authError(authArgs)?.let { finish(false, it); return@authCallback }
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
        socket.on("connect_error") { args -> finish(false, IoBrokerMessages.connectError(args)) }
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
    // Temperature view: lines for each room (inside) and outside instead of the consumption bars.
    var showTemp by remember { mutableStateOf(false) }
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

    ScaledDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogNavigationBar(MaterialTheme.colorScheme.background)
        BackHandler(enabled = drillStack.isNotEmpty()) {
            period = drillStack.last()
            drillStack = drillStack.dropLast(1)
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // Fixed header; everything below scrolls.
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Energie", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Schließen") }
                }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                    EnergyPill("kWh", !inEuro && !showTemp) { inEuro = false; showTemp = false }
                    EnergyPill("Kosten (€)", inEuro && !showTemp) { inEuro = true; showTemp = false }
                    EnergyPill("Temperatur", showTemp) { showTemp = true }
                }

                val total = bars.sumOf { it.kWh } * factor
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val summary = if (showTemp) {
                        val inside = bars.mapNotNull { it.indoor }.takeIf { it.isNotEmpty() }?.average()
                        val outside = bars.mapNotNull { it.outdoor }.takeIf { it.isNotEmpty() }?.average()
                        "Mittel im Zeitraum: innen ${inside?.let { formatNumber(it, 1) + " °C" } ?: "–"} · außen ${outside?.let { formatNumber(it, 1) + " °C" } ?: "–"}"
                    } else "Summe im Zeitraum: ${formatNumber(total, 2)} $unitLabel"
                    Text(summary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("ⓘ", fontSize = 20.sp, color = EnergyBlue, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { showInfo = true }.padding(horizontal = 8.dp, vertical = 2.dp))
                }
                if (showInfo) KlimaPilotAlertDialog(
                    onDismissRequest = { showInfo = false },
                    confirmButton = { TextButton(onClick = { showInfo = false }) { Text("OK") } },
                    title = { Text("Energieansicht") },
                    text = {
                        Text(
                            "Einen Balken antippen, um eine Ebene tiefer zu gehen: Jahr → Monat → Tag. „↑“ über dem Diagramm oder die Zurück-Geste geht wieder eine Ebene höher. " +
                                "Seitlich wischen oder mit ‹ › blättern, den Titel antippen springt zurück zum aktuellen Zeitraum.\n\n" +
                                "Die Anlage meldet ihren Verbrauch in 0,25-kWh-Schritten; jeder Schritt wird auf die Laufzeit davor verteilt. Die orange Linie zeigt die mittlere Außentemperatur, gemessen vom Außenfühler der Anlage. Unter „Alle“ ist jeder Balken nach Anlagen aufgeteilt (Farben siehe Legende), mit den Einzelwerten im Balken, wenn Platz ist. " +
                                "Stundenwerte werden 31 Tage aufbewahrt, Tageswerte dauerhaft. Kosten ohne Grundgebühr; " +
                                "„Temperatur“ zeigt statt der Balken Linien: je Raum die in der App angezeigte Raumtemperatur (Fühler minus gelernte Abweichung, aufgezeichnet seit Version 0.5.80) und orange die Außentemperatur. " +
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
                    fun barsAt(at: LocalDate) = if (at == anchor) bars else EnergyMath.bars(period, shown, at, today)
                    // "Alle": every bar is split into one coloured part per unit.
                    fun partsAt(at: LocalDate, atBars: List<EnergyBar>): List<List<Double>> {
                        if (shown.size < 2) return emptyList()
                        val perUnit = shown.map { unit -> EnergyMath.bars(period, listOf(unit), at, today) }
                        return atBars.indices.map { i -> perUnit.map { (it.getOrNull(i)?.kWh ?: 0.0) * factor } }
                    }
                    // One scale for this page and its neighbours: while swiping, both visible pages
                    // use the same scale, so equal bars mean equal values.
                    val neighbours = if (period == EnergyPeriod.ALL) listOf(bars)
                        else listOf(-1L, 0L, 1L).map { barsAt(EnergyMath.shift(period, anchor, it)) }
                    val scale = chartScale(neighbours, factor)
                    val partNames = shown.map { it.room.ifBlank { it.name.ifBlank { it.airconId } } }
                    var selectedBar by remember(period, anchor) { mutableStateOf<Int?>(null) }
                    val currentParts = partsAt(anchor, bars)
                    val selectedText = selectedBar?.let { index ->
                        val bar = bars.getOrNull(index) ?: return@let null
                        "${bar.label} Uhr: ${formatNumber(bar.kWh * factor, 2)} $unitLabel" +
                            (currentParts.getOrNull(index)?.let { split -> " (" + split.indices.joinToString(" · ") { j -> "${partNames.getOrNull(j).orEmpty()} ${formatNumber(split[j], 2)}" } + ")" } ?: "") +
                            (bar.outdoor?.let { " · Außen ${formatNumber(it, 1)} °C" } ?: "")
                    }
                    if (showTemp) {
                        // One line per room (inside) plus the outside line; same pages and drill-down as the bars.
                        fun linesAt(at: LocalDate, atBars: List<EnergyBar>): List<List<Double?>> =
                            shown.map { unit -> EnergyMath.bars(period, listOf(unit), at, today).map { it.indoor } } + listOf(atBars.map { it.outdoor })
                        val tScale = tempScale(neighbours.indices.map { k ->
                            val at = if (period == EnergyPeriod.ALL) anchor else EnergyMath.shift(period, anchor, (k - 1).toLong())
                            linesAt(at, neighbours[k])
                        })
                        val lineNames = partNames + "Außen"
                        val lineColors = shown.indices.map { partColor(it) } + OutdoorColor
                        val currentLines = linesAt(anchor, bars)
                        val tempText = selectedBar?.let { index ->
                            val bar = bars.getOrNull(index) ?: return@let null
                            "${bar.label}${if (period == EnergyPeriod.DAY) " Uhr" else ""}: " + currentLines.indices.joinToString(" · ") { j ->
                                "${lineNames[j]} ${currentLines[j].getOrNull(index)?.let { formatNumber(it, 1) + " °C" } ?: "–"}"
                            }
                        }
                        TempChartFrame(tScale, lineNames, lineColors, tempText) {
                            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth(), pageSpacing = 16.dp) { page ->
                                val pageAnchor = anchorOf(page)
                                val pageBars = barsAt(pageAnchor)
                                TempLinesPage(
                                    pageBars.map { it.label }, linesAt(pageAnchor, pageBars), lineColors, tScale,
                                    selected = if (pageAnchor == anchor) selectedBar else null
                                ) { index ->
                                    val finer = EnergyMath.finer(period)
                                    if (finer == null) { selectedBar = if (selectedBar == index) null else index; return@TempLinesPage }
                                    val target = pageBars.getOrNull(index)?.drillDown ?: return@TempLinesPage
                                    drillStack = drillStack + period
                                    period = finer
                                    anchor = target
                                }
                            }
                        }
                    } else {
                        EnergyChartFrame(scale, unitLabel, partNames, currentParts, selectedText) {
                            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth(), pageSpacing = 16.dp) { page ->
                                val pageAnchor = anchorOf(page)
                                val pageBars = barsAt(pageAnchor)
                                EnergyBarsPage(
                                    pageBars.map { it.label to it.kWh * factor }, pageBars.map { it.outdoor }, partsAt(pageAnchor, pageBars), scale,
                                    selected = if (pageAnchor == anchor) selectedBar else null
                                ) { index ->
                                    val finer = EnergyMath.finer(period)
                                    if (finer == null) { selectedBar = if (selectedBar == index) null else index; return@EnergyBarsPage }
                                    val target = pageBars.getOrNull(index)?.drillDown ?: return@EnergyBarsPage
                                    drillStack = drillStack + period
                                    period = finer
                                    anchor = target
                                }
                            }
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
}

private const val ENERGY_PAGES = 4000

/** Outdoor temperature line: a warm orange that stands apart from the blue bars. */
private val OutdoorColor = Color(0xFFE8833A)

/** Colours of the units in split bars: blue shades of clearly different lightness (orange is the outdoor line). */
private val PartColors = listOf(Color(0xFF1769AA), Color(0xFF8CC0E8), Color(0xFF0B3558), Color(0xFF4F95CC))
private fun partColor(index: Int) = PartColors[index % PartColors.size]
private fun partValueStyle(index: Int) = androidx.compose.ui.text.TextStyle(
    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
    color = if (index % PartColors.size == 1) Color(0xFF0B3558) else Color.White
)

/** Round scale steps (1, 2, 2.5 or 5 × 10^n) so that about [ticks] lines fit up to [max]. */
private fun niceStep(range: Double, ticks: Int): Double {
    if (range <= 0) return 1.0
    val raw = range / ticks
    val magnitude = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it * magnitude >= raw } * magnitude
    return step
}

private fun scaleLabel(value: Double, step: Double): String =
    formatNumber(value, if (step >= 1) 0 else if (step >= 0.1) 1 else 2)

/** Value and temperature scale, shared by neighbouring pages so swiping keeps bars comparable. */
internal data class ChartScale(
    val max: Double, val step: Double,
    val tMin: Double?, val tMax: Double?, val tStep: Double,
    val showValues: Boolean
)

private fun chartScale(pages: List<List<EnergyBar>>, factor: Double): ChartScale {
    val all = pages.flatten()
    val rawMax = all.maxOfOrNull { it.kWh * factor }?.takeIf { it > 0 } ?: 1.0
    val step = niceStep(rawMax, 4)
    val max = kotlin.math.ceil(rawMax / step) * step
    val temps = all.mapNotNull { it.outdoor }
    val tStep = if (temps.isEmpty()) 1.0 else niceStep((temps.max() - temps.min()).coerceAtLeast(2.0), 3)
    val tMin = temps.minOrNull()?.let { kotlin.math.floor(it / tStep) * tStep }
    val tMax = temps.maxOrNull()?.let { kotlin.math.ceil(it / tStep) * tStep }?.let { if (tMin != null && it - tMin < 2 * tStep) tMin + 2 * tStep else it }
    return ChartScale(max, step, tMin, tMax, tStep, showValues = (pages.maxOfOrNull { it.size } ?: 0) <= 12)
}

private val ChartHeight = 280.dp

/** Space above the highest bar: room for its value label. */
@Composable
private fun chartTopPadPx(showValues: Boolean): Float {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val valueHeight = measurer.measure("0", chartValueStyle()).size.height
    return with(density) { if (showValues) valueHeight + 4.dp.toPx() else 10.dp.toPx() }
}

@Composable
private fun chartAxisStyle() = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun chartValueStyle() = chartAxisStyle().copy(fontSize = 13.sp, color = EnergyBlue, fontWeight = FontWeight.SemiBold)

/**
 * Everything around the bars that stays put while swiping: the unit and temperature headings,
 * the legend of the split bars, the tapped hour's values and both scales. [content] is the pager
 * with the bars; it fills the space between the scales.
 */
@Composable
private fun EnergyChartFrame(
    scale: ChartScale,
    unitLabel: String,
    partNames: List<String>,
    parts: List<List<Double>>,
    selectedText: String?,
    content: @Composable () -> Unit
) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val axisStyle = chartAxisStyle()
    val tempStyle = axisStyle.copy(color = OutdoorColor)
    val hasTemps = scale.tMin != null && scale.tMax != null
    val topPad = chartTopPadPx(scale.showValues)
    // Axis widths follow the measured labels, so larger fonts never get cut off.
    val ticks = generateSequence(0.0) { it + scale.step }.takeWhile { it <= scale.max + scale.step / 2 }.toList()
    val leftWidth = with(density) { ((ticks.maxOfOrNull { measurer.measure(scaleLabel(it, scale.step), axisStyle).size.width } ?: 0) + 8.dp.toPx()).toDp() }
    val tempTicks = scale.tMin?.let { lo -> val hi = scale.tMax ?: lo; generateSequence(lo) { it + scale.tStep }.takeWhile { it <= hi + scale.tStep / 2 }.toList() } ?: emptyList()
    val rightWidth = with(density) {
        if (hasTemps) ((tempTicks.maxOfOrNull { measurer.measure(scaleLabel(it, scale.tStep) + "°", tempStyle).size.width } ?: 0) + 8.dp.toPx()).toDp() else 4.dp
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(unitLabel, fontSize = 13.sp, color = axisStyle.color, modifier = Modifier.weight(1f))
            if (hasTemps) Text("— Außen °C", fontSize = 13.sp, color = OutdoorColor)
        }
        // Legend for the split bars, with each unit's total for the shown period.
        if (parts.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                partNames.forEachIndexed { j, name ->
                    val total = parts.sumOf { it.getOrNull(j) ?: 0.0 }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(partColor(j)))
                        Text("$name ${formatNumber(total, if (total >= 10) 1 else 2)} $unitLabel", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        selectedText?.let { Text(it, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = EnergyBlue) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Canvas(Modifier.width(leftWidth).height(ChartHeight)) {
                val chartHeight = size.height - topPad
                ticks.forEach { tick ->
                    val y = (size.height - tick / scale.max * chartHeight).toFloat()
                    val text = measurer.measure(scaleLabel(tick, scale.step), axisStyle)
                    drawText(text, topLeft = Offset(size.width - text.size.width - 4.dp.toPx(), y - text.size.height / 2))
                }
            }
            Box(Modifier.weight(1f)) { content() }
            Canvas(Modifier.width(rightWidth).height(ChartHeight)) {
                val lo = scale.tMin ?: return@Canvas
                val hi = scale.tMax ?: return@Canvas
                val chartHeight = size.height - topPad
                tempTicks.forEach { t ->
                    val y = (size.height - (t - lo) / (hi - lo) * chartHeight).toFloat()
                    val text = measurer.measure(scaleLabel(t, scale.tStep) + "°", tempStyle)
                    drawText(text, topLeft = Offset(4.dp.toPx(), y - text.size.height / 2))
                }
            }
        }
    }
}

/**
 * One period's bars on the shared [scale]: grid lines, bars (split per unit under "Alle"), the
 * outdoor temperature line and, when there is room, the values; the labels underneath.
 */
@Composable
private fun EnergyBarsPage(
    values: List<Pair<String, Double>>,
    outdoor: List<Double?>,
    parts: List<List<Double>>,
    scale: ChartScale,
    selected: Int?,
    onBarTap: (Int) -> Unit
) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val valueStyle = chartValueStyle()
    val tempStyle = chartAxisStyle().copy(color = OutdoorColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val topPad = chartTopPadPx(scale.showValues)
    val max = scale.max
    Column {
        Canvas(Modifier.fillMaxWidth().height(ChartHeight).pointerInput(values.size) {
            detectTapGestures { offset ->
                val slot = size.width / values.size.coerceAtLeast(1)
                onBarTap((offset.x / slot).toInt().coerceIn(0, values.size - 1))
            }
        }) {
            val chartBottom = size.height
            val chartHeight = chartBottom - topPad
            fun yValue(v: Double) = (chartBottom - v / max * chartHeight).toFloat()
            var tick = 0.0
            while (tick <= max + scale.step / 2) {
                val y = yValue(tick)
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                tick += scale.step
            }
            val slot = size.width / values.size.coerceAtLeast(1)
            val gap = (slot * 0.18f).coerceIn(2.dp.toPx(), 6.dp.toPx())
            val barWidth = (slot - gap).coerceAtLeast(1f)
            values.forEachIndexed { index, (_, value) ->
                val top = yValue(value)
                val h = chartBottom - top
                val dim = selected != null && selected != index
                val split = parts.getOrNull(index)
                if (split != null && split.sum() > 0.0) {
                    // Stacked from the bottom, one part per unit; its value inside when there is room.
                    var bottom = chartBottom
                    split.forEachIndexed { j, part ->
                        if (part <= 0.0) return@forEachIndexed
                        val partHeight = (part / max * chartHeight).toFloat()
                        drawRoundRect(
                            color = partColor(j).let { if (dim) it.copy(alpha = 0.35f) else it },
                            topLeft = Offset(index * slot + gap / 2, bottom - partHeight),
                            size = Size(barWidth, (partHeight - 1.dp.toPx()).coerceAtLeast(1f)),
                            cornerRadius = CornerRadius(3.dp.toPx())
                        )
                        if (scale.showValues) {
                            val text = measurer.measure(formatNumber(part, if (part >= 10) 0 else 1), partValueStyle(j))
                            if (text.size.height + 2.dp.toPx() <= partHeight && text.size.width <= barWidth) {
                                drawText(text, topLeft = Offset(index * slot + slot / 2 - text.size.width / 2, bottom - partHeight / 2 - text.size.height / 2))
                            }
                        }
                        bottom -= partHeight
                    }
                } else if (h > 0f) drawRoundRect(
                    color = if (dim) EnergyBlue.copy(alpha = 0.35f) else EnergyBlue,
                    topLeft = Offset(index * slot + gap / 2, top),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
                if (scale.showValues && value > 0) {
                    val text = measurer.measure(formatNumber(value, if (value >= 10) 0 else 1), valueStyle)
                    // Too narrow for the number (many bars, large font): leave it to the scale.
                    if (text.size.width <= slot * 1.05f) drawText(text, topLeft = Offset(index * slot + slot / 2 - text.size.width / 2, top - text.size.height - 1.dp.toPx()))
                }
            }
            val tMin = scale.tMin
            val tMax = scale.tMax
            if (tMin != null && tMax != null) {
                fun yTemp(t: Double) = (chartBottom - (t - tMin) / (tMax - tMin) * chartHeight).toFloat()
                var previous: Offset? = null
                outdoor.forEachIndexed { index, value ->
                    if (value == null) { previous = null; return@forEachIndexed }
                    val point = Offset(index * slot + slot / 2, yTemp(value))
                    previous?.let { drawLine(OutdoorColor, it, point, strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round) }
                    drawCircle(Color.White, radius = 3.5.dp.toPx(), center = point)
                    drawCircle(OutdoorColor, radius = 2.5.dp.toPx(), center = point)
                    if (scale.showValues) {
                        val text = measurer.measure(formatNumber(value, 1), tempStyle)
                        if (text.size.width <= slot * 1.05f) drawText(text, topLeft = Offset(point.x - text.size.width / 2, point.y + 4.dp.toPx()))
                    }
                    previous = point
                }
            }
        }
        // Labels under the bars: at most about eight, evenly spread, centred on their bar and
        // free to be wider than one bar (two-digit days in the month view).
        val labelStep = ((values.size + 7) / 8).coerceAtLeast(1)
        val labelStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        val labelHeight = with(density) { measurer.measure("Mo", labelStyle).size.height.toDp() }
        Canvas(Modifier.fillMaxWidth().height(labelHeight)) {
            val slot = size.width / values.size.coerceAtLeast(1)
            values.forEachIndexed { index, (label, _) ->
                if (index % labelStep != 0) return@forEachIndexed
                val text = measurer.measure(label, labelStyle)
                drawText(text, topLeft = Offset(index * slot + slot / 2 - text.size.width / 2, 0f))
            }
        }
    }
}

/** Temperature scale shared by neighbouring pages (°C, whole or half degrees). */
internal data class TempScale(val min: Double, val max: Double, val step: Double, val showValues: Boolean)

private fun tempScale(pages: List<List<List<Double?>>>): TempScale {
    val values = pages.flatten().flatten().filterNotNull()
    if (values.isEmpty()) return TempScale(0.0, 25.0, 5.0, false)
    val step = niceStep((values.max() - values.min()).coerceAtLeast(4.0), 4)
    val lo = kotlin.math.floor(values.min() / step) * step
    val hi = kotlin.math.ceil(values.max() / step) * step
    val points = pages.maxOfOrNull { lines -> lines.maxOfOrNull { it.size } ?: 0 } ?: 0
    return TempScale(lo, if (hi - lo < 2 * step) lo + 2 * step else hi, step, showValues = points <= 12)
}

/** Heading, legend, tapped values and the °C scale around the temperature lines. */
@Composable
private fun TempChartFrame(scale: TempScale, names: List<String>, colors: List<Color>, selectedText: String?, content: @Composable () -> Unit) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val axisStyle = chartAxisStyle()
    val topPad = with(density) { 10.dp.toPx() }
    val ticks = generateSequence(scale.min) { it + scale.step }.takeWhile { it <= scale.max + scale.step / 2 }.toList()
    val leftWidth = with(density) { ((ticks.maxOfOrNull { measurer.measure(scaleLabel(it, scale.step) + "°", axisStyle).size.width } ?: 0) + 8.dp.toPx()).toDp() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("°C", fontSize = 13.sp, color = axisStyle.color)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            names.forEachIndexed { j, name ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.width(14.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(colors[j]))
                    Text(name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        selectedText?.let { Text(it, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = EnergyBlue) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Canvas(Modifier.width(leftWidth).height(ChartHeight)) {
                val chartHeight = size.height - topPad
                ticks.forEach { t ->
                    val y = (size.height - (t - scale.min) / (scale.max - scale.min) * chartHeight).toFloat()
                    val text = measurer.measure(scaleLabel(t, scale.step) + "°", axisStyle)
                    drawText(text, topLeft = Offset(size.width - text.size.width - 4.dp.toPx(), y - text.size.height / 2))
                }
            }
            Box(Modifier.weight(1f).padding(end = 6.dp)) { content() }
        }
    }
}

/** One period's temperature lines on the shared [scale]; gaps where nothing was recorded. */
@Composable
private fun TempLinesPage(
    labels: List<String>,
    lines: List<List<Double?>>,
    colors: List<Color>,
    scale: TempScale,
    selected: Int?,
    onTap: (Int) -> Unit
) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val markColor = EnergyBlue.copy(alpha = 0.12f)
    Column {
        Canvas(Modifier.fillMaxWidth().height(ChartHeight).pointerInput(labels.size) {
            detectTapGestures { offset ->
                val slot = size.width / labels.size.coerceAtLeast(1)
                onTap((offset.x / slot).toInt().coerceIn(0, labels.size - 1))
            }
        }) {
            val topPad = 10.dp.toPx()
            val chartHeight = size.height - topPad
            fun y(t: Double) = (size.height - (t - scale.min) / (scale.max - scale.min) * chartHeight).toFloat()
            var tick = scale.min
            while (tick <= scale.max + scale.step / 2) {
                drawLine(gridColor, Offset(0f, y(tick)), Offset(size.width, y(tick)), strokeWidth = 1.dp.toPx())
                tick += scale.step
            }
            val slot = size.width / labels.size.coerceAtLeast(1)
            selected?.let { drawRect(markColor, topLeft = Offset(it * slot, 0f), size = Size(slot, size.height)) }
            lines.forEachIndexed { j, line ->
                var previous: Offset? = null
                line.forEachIndexed { index, value ->
                    if (value == null) { previous = null; return@forEachIndexed }
                    val point = Offset(index * slot + slot / 2, y(value))
                    previous?.let { drawLine(colors[j], it, point, strokeWidth = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round) }
                    if (scale.showValues || index == selected) {
                        drawCircle(Color.White, radius = 3.5.dp.toPx(), center = point)
                        drawCircle(colors[j], radius = 2.5.dp.toPx(), center = point)
                    }
                    previous = point
                }
            }
        }
        val labelStep = ((labels.size + 7) / 8).coerceAtLeast(1)
        val labelStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        val labelHeight = with(density) { measurer.measure("Mo", labelStyle).size.height.toDp() }
        Canvas(Modifier.fillMaxWidth().height(labelHeight)) {
            val slot = size.width / labels.size.coerceAtLeast(1)
            labels.forEachIndexed { index, label ->
                if (index % labelStep != 0) return@forEachIndexed
                val text = measurer.measure(label, labelStyle)
                drawText(text, topLeft = Offset(index * slot + slot / 2 - text.size.width / 2, 0f))
            }
        }
    }
}
