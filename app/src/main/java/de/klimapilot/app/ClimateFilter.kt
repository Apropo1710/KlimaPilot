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
internal data class FilterConfig(val intervalHours: Int, val cleanedRunMinutes: Map<String, Long>) {
    /** Operating hours since the last cleaning (or since recording started). */
    fun hoursSinceCleaning(unit: EnergyUnitData): Double =
        ((unit.runMinutes - (cleanedRunMinutes[unit.airconId] ?: 0L)).coerceAtLeast(0L)) / 60.0

    fun due(unit: EnergyUnitData): Boolean = intervalHours > 0 && hoursSinceCleaning(unit) >= intervalHours

    fun toJson(): JSONObject = JSONObject()
        .put("intervalHours", intervalHours)
        .put("units", JSONObject().apply {
            cleanedRunMinutes.forEach { (id, minutes) -> put(id, JSONObject().put("cleanedRunMinutes", minutes)) }
        })
        .put("updatedAt", System.currentTimeMillis())

    companion object {
        const val DEFAULT_INTERVAL_HOURS = 200
        val INTERVALS = listOf(0, 100, 150, 200, 250, 300, 400)

        fun fromJson(o: JSONObject?): FilterConfig {
            val units = o?.optJSONObject("units")
            val cleaned = units?.keys()?.asSequence()?.associateWith { units.optJSONObject(it)?.optLong("cleanedRunMinutes", 0L) ?: 0L }.orEmpty()
            return FilterConfig(o?.optInt("intervalHours", DEFAULT_INTERVAL_HOURS) ?: DEFAULT_INTERVAL_HOURS, cleaned)
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
internal fun FilterReminder(hours: Double, onDone: () -> Unit) {
    Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Filter reinigen", color = Color(0xFF8A4B0F), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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
                        config?.let { if (it.intervalHours == 0) "Aus" else "Alle ${it.intervalHours} Betriebsstunden" } ?: "Nach Betriebsstunden der Anlage",
                        color = SMuted, fontSize = 11.sp
                    )
                }
                Text(if (expanded) "⌃" else "⌄", color = SMuted, fontSize = 20.sp)
            }
            val current = config
            if (expanded && current != null) {
                SettingRow("Erinnern nach") {
                    Dropdown(
                        FilterConfig.INTERVALS.map { if (it == 0) "Aus" else "$it Std." },
                        FilterConfig.INTERVALS.indexOf(current.intervalHours).coerceAtLeast(0)
                    ) { index -> save { it.copy(intervalHours = FilterConfig.INTERVALS[index]) } }
                }
                units.forEach { unit ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(unit.room.ifBlank { unit.name }, color = SText, fontSize = 14.sp)
                            Text("%.0f Std. seit der letzten Reinigung".format(java.util.Locale.GERMANY, current.hoursSinceCleaning(unit)), color = SMuted, fontSize = 11.sp)
                        }
                        TextButton(onClick = { save { it.copy(cleanedRunMinutes = it.cleanedRunMinutes + (unit.airconId to unit.runMinutes)) } }) {
                            Text("Gereinigt")
                        }
                    }
                }
            }
            if (expanded && status.isNotBlank()) Text(status, color = SMuted, fontSize = 11.sp)
        }
    }
}
