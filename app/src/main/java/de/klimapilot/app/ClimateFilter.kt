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
import androidx.compose.material3.Card
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
    /** Washable air filter at the front. */
    AIR("Luftfilter", "Luftfilter reinigen"),
    /** Allergen-clear and deodorizing filter, cleaned together and less often. */
    CLEAN_AIR("Allergen- und Geruchsfilter", "Allergen- und Geruchsfilter reinigen"),
}

internal data class FilterConfig(
    val intervalHours: Int,
    val cleanedRunMinutes: Map<String, Long>,
    val cleanAirIntervalHours: Int = DEFAULT_CLEAN_AIR_INTERVAL_HOURS,
    val cleanAirCleanedRunMinutes: Map<String, Long> = emptyMap(),
) {
    fun interval(kind: FilterKind) = if (kind == FilterKind.AIR) intervalHours else cleanAirIntervalHours

    private fun cleanedAt(kind: FilterKind, airconId: String) =
        (if (kind == FilterKind.AIR) cleanedRunMinutes else cleanAirCleanedRunMinutes)[airconId] ?: 0L

    /** Operating hours since the last cleaning (or since recording started). */
    fun hoursSinceCleaning(unit: EnergyUnitData, kind: FilterKind = FilterKind.AIR): Double =
        (unit.runMinutes - cleanedAt(kind, unit.airconId)).coerceAtLeast(0L) / 60.0

    fun due(unit: EnergyUnitData, kind: FilterKind = FilterKind.AIR): Boolean =
        interval(kind) > 0 && hoursSinceCleaning(unit, kind) >= interval(kind)

    /** All filters of the unit that are due, with their hours. */
    fun dueFilters(unit: EnergyUnitData): List<Pair<FilterKind, Double>> =
        FilterKind.entries.filter { due(unit, it) }.map { it to hoursSinceCleaning(unit, it) }

    fun withInterval(kind: FilterKind, hours: Int) =
        if (kind == FilterKind.AIR) copy(intervalHours = hours) else copy(cleanAirIntervalHours = hours)

    fun cleaned(kind: FilterKind, unit: EnergyUnitData) =
        if (kind == FilterKind.AIR) copy(cleanedRunMinutes = cleanedRunMinutes + (unit.airconId to unit.runMinutes))
        else copy(cleanAirCleanedRunMinutes = cleanAirCleanedRunMinutes + (unit.airconId to unit.runMinutes))

    fun toJson(): JSONObject = JSONObject()
        .put("intervalHours", intervalHours)
        .put("cleanAirIntervalHours", cleanAirIntervalHours)
        .put("units", JSONObject().apply {
            (cleanedRunMinutes.keys + cleanAirCleanedRunMinutes.keys).forEach { id ->
                put(id, JSONObject().apply {
                    cleanedRunMinutes[id]?.let { put("cleanedRunMinutes", it) }
                    cleanAirCleanedRunMinutes[id]?.let { put("cleanAirCleanedRunMinutes", it) }
                })
            }
        })
        .put("updatedAt", System.currentTimeMillis())

    companion object {
        /** About two weeks of daily use; the manual says "clean every two weeks". */
        const val DEFAULT_INTERVAL_HOURS = 150
        /** About three months of daily use. */
        const val DEFAULT_CLEAN_AIR_INTERVAL_HOURS = 750
        val INTERVALS = listOf(0, 100, 150, 200, 250, 300, 400)
        val CLEAN_AIR_INTERVALS = listOf(0, 300, 500, 750, 1000, 1500)

        fun intervals(kind: FilterKind) = if (kind == FilterKind.AIR) INTERVALS else CLEAN_AIR_INTERVALS

        fun fromJson(o: JSONObject?): FilterConfig {
            val units = o?.optJSONObject("units")
            fun cleaned(field: String) = units?.keys()?.asSequence()
                ?.mapNotNull { id -> units.optJSONObject(id)?.takeIf { it.has(field) }?.let { id to it.optLong(field, 0L) } }
                ?.toMap().orEmpty()
            return FilterConfig(
                intervalHours = o?.optInt("intervalHours", DEFAULT_INTERVAL_HOURS) ?: DEFAULT_INTERVAL_HOURS,
                cleanedRunMinutes = cleaned("cleanedRunMinutes"),
                cleanAirIntervalHours = o?.optInt("cleanAirIntervalHours", DEFAULT_CLEAN_AIR_INTERVAL_HOURS) ?: DEFAULT_CLEAN_AIR_INTERVAL_HOURS,
                cleanAirCleanedRunMinutes = cleaned("cleanAirCleanedRunMinutes"),
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
                if (args.firstOrNull() as? Boolean != true) finish(null, "ioBroker hat die Verbindung nicht authentifiziert.")
                else block(socket, finish)
            })
        }
        socket.on("connect_error") { args -> finish(null, "ioBroker nicht erreichbar: ${args.firstOrNull() ?: "Verbindung fehlgeschlagen"}") }
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
internal fun FilterReminder(kind: FilterKind, hours: Double, onDone: () -> Unit) {
    Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(kind.reminder, color = Color(0xFF8A4B0F), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("%.0f Betriebsstunden seit der letzten Reinigung".format(java.util.Locale.GERMANY, hours), color = Color(0xFF8A4B0F), fontSize = 12.sp)
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
                            FilterKind.entries.joinToString(" · ") { k -> if (c.interval(k) == 0) "${k.label} aus" else "${k.label} ${c.interval(k)} Std." }
                        } ?: "Nach Betriebsstunden der Anlage",
                        color = SMuted, fontSize = 11.sp
                    )
                }
                Text(if (expanded) "⌃" else "⌄", color = SMuted, fontSize = 20.sp)
            }
            val current = config
            if (expanded && current != null) {
                FilterKind.entries.forEach { kind ->
                    Text(kind.label, color = SText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    SettingRow("Erinnern nach") {
                        val options = FilterConfig.intervals(kind)
                        Dropdown(
                            options.map { if (it == 0) "Aus" else "$it Std." },
                            options.indexOf(current.interval(kind)).coerceAtLeast(0)
                        ) { index -> save { it.withInterval(kind, options[index]) } }
                    }
                    units.forEach { unit ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(unit.room.ifBlank { unit.name }, color = SText, fontSize = 14.sp)
                                Text("%.0f Std. seit der letzten Reinigung".format(java.util.Locale.GERMANY, current.hoursSinceCleaning(unit, kind)), color = SMuted, fontSize = 11.sp)
                            }
                            TextButton(onClick = { save { it.cleaned(kind, unit) } }) { Text("Gereinigt") }
                        }
                    }
                }
            }
            if (expanded && status.isNotBlank()) Text(status, color = SMuted, fontSize = 11.sp)
        }
    }
}
