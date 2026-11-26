package de.klimapilot.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Filter reminder. The ioBroker script counts operating minutes per unit (`runMinutes` in
 * energy.units.<airconId>); this stores, per unit, the count at the last cleaning and the interval.
 */
internal enum class FilterKind(val label: String, val reminder: String) {
    /** Washable air filter at the front: interval in weeks. */
    AIR("Luftfilter", "Luftfilter reinigen"),
    /** Allergen-clear and deodorizing filter, cleaned together and less often: interval in months. */
    CLEAN_AIR("Allergen- und Geruchsfilter", "Allergen- und Geruchsfilter reinigen"),
}

/**
 * Filter reminder settings. Intervals are given like in the manual (weeks / months) but counted in
 * operating days: only calendar days on which the unit ran (`runDays` from the ioBroker script).
 */
internal data class FilterConfig(
    val airIntervalWeeks: Int = DEFAULT_AIR_WEEKS,
    val cleanAirIntervalMonths: Int = DEFAULT_CLEAN_AIR_MONTHS,
    val airCleanedRunDays: Map<String, Int> = emptyMap(),
    val cleanAirCleanedRunDays: Map<String, Int> = emptyMap(),
) {
    /** Interval in operating days, 0 = off. */
    fun intervalDays(kind: FilterKind) = if (kind == FilterKind.AIR) airIntervalWeeks * 7 else cleanAirIntervalMonths * 30

    fun intervalLabel(kind: FilterKind) = when {
        intervalDays(kind) == 0 -> "aus"
        kind == FilterKind.AIR -> if (airIntervalWeeks == 1) "1 Woche" else "$airIntervalWeeks Wochen"
        else -> if (cleanAirIntervalMonths == 1) "1 Monat" else "$cleanAirIntervalMonths Monate"
    }

    /** Operating days since the last cleaning (or since recording started). */
    fun daysSinceCleaning(unit: EnergyUnitData, kind: FilterKind = FilterKind.AIR): Int =
        (unit.runDays - ((if (kind == FilterKind.AIR) airCleanedRunDays else cleanAirCleanedRunDays)[unit.airconId] ?: 0)).coerceAtLeast(0)

    fun due(unit: EnergyUnitData, kind: FilterKind = FilterKind.AIR): Boolean =
        intervalDays(kind) > 0 && daysSinceCleaning(unit, kind) >= intervalDays(kind)

    /** All filters of the unit that are due, with their operating days and interval. */
    fun dueFilters(unit: EnergyUnitData): List<Triple<FilterKind, Int, Int>> =
        FilterKind.entries.filter { due(unit, it) }.map { Triple(it, daysSinceCleaning(unit, it), intervalDays(it)) }

    fun withInterval(kind: FilterKind, value: Int) =
        if (kind == FilterKind.AIR) copy(airIntervalWeeks = value) else copy(cleanAirIntervalMonths = value)

    fun cleaned(kind: FilterKind, unit: EnergyUnitData) =
        if (kind == FilterKind.AIR) copy(airCleanedRunDays = airCleanedRunDays + (unit.airconId to unit.runDays))
        else copy(cleanAirCleanedRunDays = cleanAirCleanedRunDays + (unit.airconId to unit.runDays))

    fun toJson(): JSONObject = JSONObject()
        .put("airIntervalWeeks", airIntervalWeeks)
        .put("cleanAirIntervalMonths", cleanAirIntervalMonths)
        .put("units", JSONObject().apply {
            (airCleanedRunDays.keys + cleanAirCleanedRunDays.keys).forEach { id ->
                put(id, JSONObject().apply {
                    airCleanedRunDays[id]?.let { put("airCleanedRunDays", it) }
                    cleanAirCleanedRunDays[id]?.let { put("cleanAirCleanedRunDays", it) }
                })
            }
        })
        .put("updatedAt", System.currentTimeMillis())

    companion object {
        /** Manual: clean the air filter every two weeks. */
        const val DEFAULT_AIR_WEEKS = 2
        /** Allergen and deodorizing filter: "periodically"; about a quarter of a year. */
        const val DEFAULT_CLEAN_AIR_MONTHS = 3
        private val AIR_OPTIONS = listOf(0, 1, 2, 3, 4)
        private val CLEAN_AIR_OPTIONS = listOf(0, 1, 2, 3, 4, 5, 6)

        fun options(kind: FilterKind) = if (kind == FilterKind.AIR) AIR_OPTIONS else CLEAN_AIR_OPTIONS
        fun optionLabel(kind: FilterKind, value: Int) = when {
            value == 0 -> "Aus"
            kind == FilterKind.AIR -> if (value == 1) "1 Woche" else "$value Wochen"
            else -> if (value == 1) "1 Monat" else "$value Monate"
        }

        fun fromJson(o: JSONObject?): FilterConfig {
            val units = o?.optJSONObject("units")
            fun cleaned(field: String) = units?.keys()?.asSequence()
                ?.mapNotNull { id -> units.optJSONObject(id)?.takeIf { it.has(field) }?.let { id to it.optInt(field, 0) } }
                ?.toMap().orEmpty()
            return FilterConfig(
                airIntervalWeeks = o?.optInt("airIntervalWeeks", DEFAULT_AIR_WEEKS) ?: DEFAULT_AIR_WEEKS,
                cleanAirIntervalMonths = o?.optInt("cleanAirIntervalMonths", DEFAULT_CLEAN_AIR_MONTHS) ?: DEFAULT_CLEAN_AIR_MONTHS,
                airCleanedRunDays = cleaned("airCleanedRunDays"),
                cleanAirCleanedRunDays = cleaned("cleanAirCleanedRunDays"),
            )
        }
    }
}

internal class FilterIoBroker {
    private val main = Handler(Looper.getMainLooper())

    private fun session(address: String, callback: (FilterConfig?, String?) -> Unit, block: (IoBrokerLease, (FilterConfig?, String?) -> Unit) -> Unit) {
        val url = address.trim().trimEnd('/')
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) { callback(null, "Keine ioBroker-Adresse hinterlegt."); return }
        val socket = IoBrokerLease(url)
        val done = AtomicBoolean(false)
        val finish: (FilterConfig?, String?) -> Unit = { data, error ->
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

    private fun read(socket: IoBrokerLease, next: (FilterConfig) -> Unit) {
        socket.emit("getState", CONFIG_ID, IoBrokerAck { args ->
            val raw = (args.getOrNull(1) as? JSONObject)?.opt("val") as? String
            next(FilterConfig.fromJson(raw?.let { runCatching { JSONObject(it) }.getOrNull() }))
        })
    }

    fun load(address: String, callback: (FilterConfig?, String?) -> Unit) = session(address, callback) { socket, finish ->
        read(socket) { finish(it, null) }
    }

    /** Read – change – write, so both phones can mark cleanings without losing each other's. */
    fun update(address: String, change: (FilterConfig) -> FilterConfig, callback: (FilterConfig?, String?) -> Unit) =
        session(address, callback) { socket, finish ->
            read(socket) { current ->
                val next = change(current)
                val definition = JSONObject().put("type", "state")
                    .put("common", JSONObject().put("name", "KlimaPilot – Filter-Erinnerung (wird von KlimaPilot geschrieben)").put("type", "string").put("role", "json").put("read", true).put("write", true))
                    .put("native", JSONObject())
                socket.emit("setObject", CONFIG_ID, definition, IoBrokerAck { objectArgs ->
                    if (objectArgs.firstOrNull()?.takeUnless { it == JSONObject.NULL } != null) { finish(null, "Filter-Erinnerung konnte nicht gespeichert werden."); return@IoBrokerAck }
                    socket.emit("setState", CONFIG_ID, JSONObject().put("val", next.toJson().toString()).put("ack", false), IoBrokerAck { stateArgs ->
                        if (stateArgs.firstOrNull()?.takeUnless { it == JSONObject.NULL } != null) finish(null, "Filter-Erinnerung konnte nicht gespeichert werden.")
                        else finish(next, null)
                    })
                })
            }
        }

    companion object {
        const val CONFIG_ID = "0_userdata.0.KlimaPilot.filter.config"
    }
}

/** Small reminder on the unit page, shown only when cleaning is due. */
@Composable
internal fun FilterReminder(kind: FilterKind, days: Int, intervalDays: Int, onDone: () -> Unit) {
    Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(kind.reminder, color = Color(0xFF8A4B0F), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("$days von $intervalDays Betriebstagen seit der letzten Reinigung", color = Color(0xFF8A4B0F), fontSize = 12.sp)
            }
            TextButton(onClick = onDone) { Text("Erledigt", color = Color(0xFF8A4B0F), fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Settings card: interval and the state of each unit, with "cleaned" per unit. */
@Composable
internal fun FilterSettingsCard(units: List<EnergyUnitData>, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, onChanged: (FilterConfig) -> Unit) {
    val context = LocalContext.current
    val address = remember { context.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE).getString("iobroker_server", null).orEmpty() }
    val io = remember { FilterIoBroker() }
    var config by remember { mutableStateOf<FilterConfig?>(null) }
    var status by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf(false) }
    LaunchedEffect(expanded, refresh) {
        if (expanded) io.load(address) { result, error -> if (result != null) { config = result; status = "" } else status = error.orEmpty() }
    }
    fun save(change: (FilterConfig) -> FilterConfig) {
        io.update(address, change) { result, error ->
            if (result != null) { config = result; status = ""; onChanged(result) } else status = error.orEmpty()
        }
    }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3EEF7))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().clickable { onExpandedChange(!expanded) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Filter-Erinnerung", color = SText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        config?.let { c ->
                            FilterKind.entries.joinToString(" · ") { k -> "${k.label} ${c.intervalLabel(k)}" }
                        } ?: "Nach Betriebstagen der Anlage",
                        color = SMuted, fontSize = 11.sp
                    )
                }
                if (expanded) Box(Modifier.padding(end = 8.dp).size(26.dp).clip(CircleShape).clickable { showInfo = true }, contentAlignment = Alignment.Center) {
                    Text("ⓘ", color = SMuted, fontSize = 15.sp)
                }
                Text(if (expanded) "⌃" else "⌄", color = SMuted, fontSize = 20.sp)
            }
            val current = config
            if (expanded && current != null) {
                FilterKind.entries.forEach { kind ->
                    Text(kind.label, color = SText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    SettingRow("Erinnern nach") {
                        val options = FilterConfig.options(kind)
                        val currentValue = if (kind == FilterKind.AIR) current.airIntervalWeeks else current.cleanAirIntervalMonths
                        Dropdown(
                            options.map { FilterConfig.optionLabel(kind, it) },
                            options.indexOf(currentValue).coerceAtLeast(0)
                        ) { index -> save { it.withInterval(kind, options[index]) } }
                    }
                    units.forEach { unit ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(unit.room.ifBlank { unit.name }, color = SText, fontSize = 14.sp)
                                Text(
                                    if (current.intervalDays(kind) > 0) "${current.daysSinceCleaning(unit, kind)} von ${current.intervalDays(kind)} Betriebstagen"
                                    else "${current.daysSinceCleaning(unit, kind)} Betriebstage seit der letzten Reinigung",
                                    color = SMuted, fontSize = 11.sp
                                )
                            }
                            TextButton(onClick = { save { it.cleaned(kind, unit) } }) { Text("Gereinigt") }
                        }
                    }
                }
            }
            if (expanded && status.isNotBlank()) Text(status, color = SMuted, fontSize = 11.sp)
        }
    }
    if (showInfo) {
        KlimaPilotAlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("OK") } },
            title = { Text("Filter-Erinnerung") },
            text = {
                Text(
                    "Die Abstände entsprechen dem Handbuch: Luftfilter alle zwei Wochen, Allergen- und Geruchsfilter regelmäßig " +
                        "(hier alle drei Monate). Gezählt werden nur Betriebstage – Tage, an denen die Anlage lief. Wochen oder Monate, " +
                        "in denen sie aus ist, zählen nicht mit. Den Allergenfilter etwa einmal im Jahr austauschen."
                )
            }
        )
    }
}
