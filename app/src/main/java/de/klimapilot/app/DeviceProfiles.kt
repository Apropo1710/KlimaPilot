package de.klimapilot.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.klimapilot.app.wfrac.WfRacControlRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import de.klimapilot.app.wfrac.WfRacDiscovery
import de.klimapilot.app.wfrac.WfRacDiscoveredService

/** Per-device configuration and explicit first-time WF-RAC pairing. */
@Composable
internal fun DeviceSettingsDialog(device: UserDevice, onDismiss: () -> Unit, onSave: (UserDevice) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var model by remember(device.id) { mutableStateOf(device.model) }
    var syncDeviceKey by remember(device.id) { mutableStateOf(device.syncDeviceKey) }
    var wfRacHost by remember(device.id) { mutableStateOf(device.wfRacHost) }
    var wfRacAirconId by remember(device.id) { mutableStateOf(device.wfRacAirconId) }
    var demoMode by remember(device.id) { mutableStateOf(device.demoMode) }
    var pairing by remember { mutableStateOf(false) }
    var pairingStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val repository = remember(context) { WfRacControlRepository(context) }
    val discovery = remember(context) { WfRacDiscovery(context) }
    var scanning by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf("") }
    var foundModules by remember { mutableStateOf<List<WfRacDiscoveredService>>(emptyList()) }
    DisposableEffect(discovery) { onDispose { discovery.stop() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Geräteeinstellungen", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(device.name, color = Color(0xFF1769AA), fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Modellbezeichnung (optional)") },
                    placeholder = { Text("z. B. SRK15ZS-WF") },
                    singleLine = true
                )
                HorizontalDivider()
                Text("DIREKTE WLAN-VERBINDUNG", color = Color(0xFF63748A), fontWeight = FontWeight.Bold)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = demoMode, onCheckedChange = { demoMode = it })
                    Text("Simuliertes Gerät (Testmodus)")
                }
                Text(
                    "Im Testmodus wird eine Klimaanlage simuliert. Es werden keine Netzwerkverbindungen und keine echten Steuerbefehle ausgeführt.",
                    color = Color(0xFF63748A), fontSize = 11.sp
                )
                if (!demoMode) {
                    OutlinedButton(
                        enabled = !scanning && !pairing,
                        onClick = {
                            foundModules = emptyList()
                            scanning = true
                            scanMessage = "Suche im lokalen WLAN …"
                            val started = discovery.start(
                                onFound = { found ->
                                    if (foundModules.none { it.host == found.host }) foundModules = foundModules + found
                                },
                                onError = { message -> scanMessage = message; scanning = false }
                            )
                            if (started) scope.launch {
                                kotlinx.coroutines.delay(10_000L)
                                discovery.stop()
                                scanning = false
                                scanMessage = if (foundModules.isEmpty()) "Keine WF-RAC-Module gefunden." else "${foundModules.size} Modul(e) gefunden"
                            } else {
                                scanning = false
                                scanMessage = "Die WLAN-Suche konnte nicht gestartet werden."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (scanning) "Suche läuft …" else "Im WLAN nach Klimaanlagen suchen") }
                    if (scanMessage.isNotBlank()) Text(scanMessage, color = Color(0xFF63748A), fontSize = 11.sp)
                    foundModules.forEach { found ->
                        OutlinedButton(
                            onClick = { wfRacHost = found.host },
                            enabled = !pairing,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("${found.name} · ${found.host}") }
                    }
                    OutlinedTextField(
                        value = wfRacHost,
                        onValueChange = { wfRacHost = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("IP-Adresse des WF-RAC-Moduls") },
                        placeholder = { Text("z. B. 192.168.178.100") },
                        singleLine = true
                    )
                    OutlinedButton(
                        enabled = !pairing && wfRacHost.isNotBlank(),
                        onClick = {
                            pairing = true
                            pairingStatus = "Modul wird geprüft und gekoppelt …"
                            scope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) { repository.pair(wfRacHost) }
                                    wfRacAirconId = result.airconId
                                    pairingStatus = "Erfolgreich gekoppelt. Geräte-ID: ${result.airconId}"
                                } catch (error: Exception) {
                                    pairingStatus = error.message ?: "Kopplung fehlgeschlagen."
                                } finally {
                                    pairing = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (pairing) "Kopplung läuft …" else "WLAN-Modul koppeln und auslesen") }
                    if (wfRacAirconId.isNotBlank()) {
                        Text("Gekoppelte Geräte-ID: $wfRacAirconId", color = Color(0xFF16845B), fontSize = 11.sp)
                    }
                    if (pairingStatus.isNotBlank()) Text(
                        pairingStatus,
                        color = if (pairingStatus.startsWith("Erfolgreich")) Color(0xFF16845B) else Color(0xFF63748A),
                        fontSize = 11.sp
                    )
                }
                HorizontalDivider()
                Text("PROFIL-SYNCHRONISIERUNG", color = Color(0xFF63748A), fontWeight = FontWeight.Bold)
                Text(
                    "Gleiche Kennung auf allen Smartphones eintragen, die dieselbe physische Klimaanlage verwenden. Die Kennung ordnet Profile dem Gerät zu; sie ist kein Zugangsschlüssel.",
                    color = Color(0xFF63748A), fontSize = 11.sp
                )
                Text(
                    if (wfRacAirconId.isNotBlank()) {
                        "Die Profil-Synchronisierung verwendet automatisch die vom gekoppelten WLAN-Modul gelieferte Geräte-ID. Auf weiteren Smartphones wird dieselbe Klimaanlage dadurch automatisch erkannt."
                    } else {
                        "Nach dem Koppeln des WF-RAC-Moduls wird dessen Geräte-ID automatisch für die Profil-Synchronisierung verwendet. Du musst keine Kennung selbst vergeben."
                    },
                    color = Color(0xFF63748A), fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !pairing,
                onClick = {
                    onSave(device.copy(
                        model = model.trim(),
                        // Keep an existing legacy sync key; otherwise derive a stable key from
                        // the physical module identity shared by all installations of this unit.
                        syncDeviceKey = syncDeviceKey.trim().ifBlank {
                            wfRacAirconId.trim().takeIf { it.isNotBlank() }?.let { "wfrac:$it" }.orEmpty()
                        },
                        wfRacHost = wfRacHost.trim(),
                        wfRacAirconId = wfRacAirconId.trim(),
                        demoMode = demoMode
                    ))
                }
            ) { Text("Speichern", color = Color(0xFF1769AA)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
