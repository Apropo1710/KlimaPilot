package de.klimapilot.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class AdapterProfile(val key: String, val title: String, val adapterId: String, val description: String)

internal object AdapterProfiles {
    // Add manufacturers here; the device settings UI remains unchanged.
    val all = listOf(
        AdapterProfile("mhi", "Mitsubishi Heavy", "mhi-wfrac", "Mitsubishi-Heavy-Klimageräte"),
        AdapterProfile("daikin", "Daikin", "daikin", "Daikin-Klimageräte"),
        AdapterProfile("panasonic", "Panasonic", "panasonic", "Panasonic-Klimageräte"),
        AdapterProfile("generic", "Universell / anderer Hersteller", "", "Freie Zuordnung für beliebige Adapter")
    )
}

internal data class DiscoveredIoBrokerPoint(
    val id: String, val name: String, val type: String, val role: String,
    val read: Boolean, val write: Boolean, val unit: String? = null,
    val min: Double? = null, val max: Double? = null
)

internal object DevicePointMatcher {
    private val aliases = mapOf(
        "power" to listOf("power", "on", "switch", "enabled", "state"),
        "targetTemperature" to listOf("setpoint", "targettemperature", "desiredtemperature", "temperature_set"),
        "currentTemperature" to listOf("actualtemperature", "currenttemperature", "roomtemperature", "temperature"),
        "mode" to listOf("mode", "operationmode", "workingmode"),
        "fanSpeed" to listOf("fanspeed", "fan", "windspeed"),
        "swing" to listOf("swing", "vane", "louver", "airdirection"),
        "humidity" to listOf("humidity", "relativehumidity"),
        "energy" to listOf("energy", "powerconsumption", "electricpower"),
        "filterStatus" to listOf("filter", "filterstatus", "cleanfilter")
    )

    fun suggest(points: List<DiscoveredIoBrokerPoint>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        aliases.forEach { (function, terms) ->
            val candidate = points.firstOrNull { point ->
                val searchable = (point.id + " " + point.name + " " + point.role).lowercase().replace(Regex("[^a-z0-9]"), "")
                terms.any(searchable::contains) &&
                    if (function in listOf("power", "targetTemperature", "mode", "fanSpeed", "swing")) point.write else point.read
            }
            if (candidate != null) result[function] = candidate.id
        }
        return result
    }
}

@Composable
internal fun DeviceSettingsDialog(device: UserDevice, onDismiss: () -> Unit, onSave: (UserDevice) -> Unit) {
    var adapterKey by remember(device.id) { mutableStateOf(device.adapterKey) }
    var adapterInstance by remember(device.id) { mutableStateOf(device.adapterInstance) }
    var rootObjectId by remember(device.id) { mutableStateOf(device.rootObjectId) }
    var model by remember(device.id) { mutableStateOf(device.model) }
    val mappings = remember(device.id) { mutableStateMapOf<String, String>().apply { putAll(device.mappings) } }
    var expanded by remember { mutableStateOf(false) }
    var scanInfo by remember { mutableStateOf(false) }
    val selected = AdapterProfiles.all.firstOrNull { it.key == adapterKey } ?: AdapterProfiles.all.last()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Geräteeinstellungen", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(device.name, color = Color(0xFF1769AA), fontWeight = FontWeight.SemiBold)
                Text("Hersteller / Adapterprofil", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selected.title, modifier = Modifier.weight(1f))
                        Text("⌄")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        AdapterProfiles.all.forEach { profile ->
                            DropdownMenuItem(text = { Text(profile.title) }, onClick = { adapterKey = profile.key; expanded = false })
                        }
                    }
                }
                Text(selected.description, color = Color(0xFF63748A), fontSize = 11.sp)
                OutlinedTextField(value = model, onValueChange = { model = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Modellbezeichnung (optional)") }, placeholder = { Text("z. B. SRK15ZS-WF") }, singleLine = true)
                OutlinedTextField(value = adapterInstance, onValueChange = { adapterInstance = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ioBroker-Adapter-Instanz") }, placeholder = { Text(if (selected.adapterId.isNotBlank()) "${selected.adapterId}.0" else "z. B. hersteller.0") }, singleLine = true)
                OutlinedTextField(value = rootObjectId, onValueChange = { rootObjectId = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Oberstes Geräteobjekt (optional)") }, placeholder = { Text("Wird später beim Scan erkannt") }, singleLine = true)
                HorizontalDivider()
                Text("Funktionszuordnung", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Ordne ioBroker-Datenpunkte den einheitlichen Gerätefunktionen zu.", color = Color(0xFF63748A), fontSize = 11.sp)
                val functions = listOf(
                    "power" to "Ein / Aus", "targetTemperature" to "Solltemperatur",
                    "currentTemperature" to "Isttemperatur", "mode" to "Betriebsmodus",
                    "fanSpeed" to "Lüfterstufe", "swing" to "Lamellen",
                    "humidity" to "Luftfeuchtigkeit", "energy" to "Energieverbrauch",
                    "filterStatus" to "Filterstatus"
                )
                functions.forEach { (key, label) ->
                    OutlinedTextField(value = mappings[key] ?: "", onValueChange = { mappings[key] = it }, modifier = Modifier.fillMaxWidth(), label = { Text(label, fontSize = 12.sp) }, placeholder = { Text("ioBroker-Datenpunkt-ID", fontSize = 11.sp) }, singleLine = true)
                }
                OutlinedButton(onClick = { scanInfo = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Datenpunkte automatisch erkennen")
                }
                Text("Der automatische Scan wird mit der echten ioBroker-Verbindung aktiviert. Die manuelle Zuordnung kann bereits gespeichert werden.", color = Color(0xFF63748A), fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(device.copy(adapterKey = adapterKey, adapterInstance = adapterInstance.trim(), rootObjectId = rootObjectId.trim(), mappings = mappings.filterValues { it.isNotBlank() }.toMap(), model = model.trim()))
            }) { Text("Speichern", color = Color(0xFF1769AA)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
    if (scanInfo) {
        AlertDialog(onDismissRequest = { scanInfo = false }, title = { Text("ioBroker-Verbindung fehlt") }, text = { Text("Die App kann den Objektbaum noch nicht abrufen. Das wird ergänzt, sobald die sichere ioBroker-Schnittstelle eingerichtet ist.") }, confirmButton = { TextButton(onClick = { scanInfo = false }) { Text("OK") } })
    }
}
