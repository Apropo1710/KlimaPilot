package de.klimapilot.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ManagerBlue = Color(0xFF1769AA)
private val ManagerBlueLight = Color(0xFFE8F1FA)
private val ManagerText = Color(0xFF172334)
private val ManagerMuted = Color(0xFF63748A)
private val ManagerBorder = Color(0xFFCBD5E1)

internal data class UserDevice(val id: String, val name: String, val room: String, val adapterKey: String = "generic", val adapterInstance: String = "", val rootObjectId: String = "", val mappings: Map<String, String> = emptyMap(), val model: String = "", val syncDeviceKey: String = "", val wfRacHost: String = "", val wfRacAirconId: String = "", val demoMode: Boolean = false, val heatOffset: Double = 0.0, val heatOffsetAuto: Boolean = false)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoomDeviceManagerScreen(
    rooms: SnapshotStateList<String>,
    devices: SnapshotStateList<UserDevice>,
    save: () -> Unit,
    rename: (String, String) -> Unit,
    onRemoveRoom: (String) -> Unit,
    onDeleteDevice: (UserDevice) -> String?,
    onResetPairing: (String) -> String?,
    back: () -> Unit
) {
    var showAddOptions by remember { mutableStateOf(false) }
    var deletingDevice by remember { mutableStateOf<UserDevice?>(null) }
    var deleteError by remember { mutableStateOf("") }
    var addRoom by remember { mutableStateOf(false) }
    var addDevice by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<UserDevice?>(null) }
    var configuring by remember { mutableStateOf<UserDevice?>(null) }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFE8EEF5)).statusBarsPadding().navigationBarsPadding().imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFE8EEF5)).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, contentDescription = "Zurück", tint = ManagerText) }
            Text("Räume & Geräte", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = ManagerText)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        Text("Jeder Raum zeigt direkt die ihm zugeordneten Geräte.", color = ManagerMuted, fontSize = 13.sp)

        rooms.toList().forEach { room ->
            var name by remember(room) { mutableStateOf(room) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ManagerBorder)
            ) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MeetingRoom, contentDescription = null, tint = ManagerBlue)
                        Spacer(Modifier.width(8.dp))
                        Text(room, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ManagerText)
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
                        label = { Text("Raumname", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ManagerBlue,
                            focusedLabelColor = ManagerBlue,
                            cursorColor = ManagerBlue
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            modifier = Modifier.weight(1f).height(40.dp),
                            enabled = name.trim().isNotEmpty() && name.trim() != room && name.trim() !in rooms,
                            onClick = {
                                val newName = name.trim()
                                rooms[rooms.indexOf(room)] = newName
                                devices.indices.forEach { i ->
                                    if (devices[i].room == room) devices[i] = devices[i].copy(room = newName)
                                }
                                rename(room, newName)
                                save()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ManagerBlue, contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("Umbenennen", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f).height(40.dp),
                            enabled = rooms.size > 1,
                            onClick = {
                                rooms.remove(room)
                                onRemoveRoom(room)
                                devices.indices.filter { devices[it].room == room }.forEach { i ->
                                    devices[i] = devices[i].copy(room = rooms.first())
                                }
                                save()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ManagerText),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ManagerBorder)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("Löschen", fontSize = 13.sp)
                        }
                    }

                    HorizontalDivider(color = ManagerBorder)
                    Text("GERÄTE IN DIESEM RAUM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ManagerMuted, letterSpacing = 1.sp)

                    val roomDevices = devices.filter { it.room == room }
                    if (roomDevices.isEmpty()) {
                        Text("Noch keine Geräte zugeordnet", color = ManagerMuted, fontSize = 13.sp)
                    } else {
                        roomDevices.forEach { device ->
                            Row(
                                Modifier.fillMaxWidth().background(ManagerBlueLight, RoundedCornerShape(12.dp)).padding(start = 9.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AcUnit, contentDescription = null, tint = ManagerBlue)
                                Spacer(Modifier.width(9.dp))
                                Text(device.name, modifier = Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ManagerText)
                                IconButton(onClick = { configuring = device }) {
                                    Icon(Icons.Default.Tune, contentDescription = "Geräteeinstellungen", tint = ManagerBlue)
                                }
                                IconButton(onClick = { editing = device }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Gerät bearbeiten", tint = ManagerBlue)
                                }
                                IconButton(onClick = { deletingDevice = device; deleteError = "" }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Gerät löschen", tint = ManagerMuted)
                                }
                            }
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { showAddOptions = true },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ManagerBlue, contentColor = Color.White)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Hinzufügen")
        }
        Text("Die Einträge werden lokal gespeichert. Die Klimageräte werden direkt über das WF-RAC-WLAN-Modul eingebunden.", fontSize = 12.sp, color = ManagerMuted)
        }
    }

    if (showAddOptions) {
        KlimaPilotAlertDialog(
            onDismissRequest = { showAddOptions = false },
            containerColor = Color.White,
            title = { Text("Was möchtest du hinzufügen?", color = ManagerText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showAddOptions = false; addRoom = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ManagerBlue, contentColor = Color.White)
                    ) { Text("Raum hinzufügen") }
                    OutlinedButton(
                        onClick = { showAddOptions = false; editing = null; addDevice = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ManagerBlue),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ManagerBlue)
                    ) { Text("Gerät hinzufügen") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddOptions = false }, colors = ButtonDefaults.textButtonColors(contentColor = ManagerMuted)) {
                    Text("Schließen")
                }
            }
        )
    }

    if (deletingDevice != null) {
        val target = deletingDevice!!
        KlimaPilotAlertDialog(
            onDismissRequest = { deletingDevice = null; deleteError = "" },
            containerColor = Color.White,
            title = { Text("Gerät wirklich löschen?", color = ManagerText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("„${target.name}“ wird aus der Raumverwaltung entfernt. Eine vorhandene WF-RAC-Kopplung und die lokal gespeicherte Modulidentität werden ebenfalls zurückgesetzt.")
                    if (deleteError.isNotBlank()) Text(deleteError, color = Color(0xFFB42318))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // A unit still assigned to another device keeps its module identity.
                    val sharedUnit = target.wfRacAirconId.isNotBlank() &&
                        devices.any { it.id != target.id && it.wfRacAirconId == target.wfRacAirconId }
                    val error = if (sharedUnit) null else onDeleteDevice(target)
                    if (error == null) {
                        devices.remove(target)
                        save()
                        deletingDevice = null
                        deleteError = ""
                    } else deleteError = error
                }) { Text("Löschen", color = Color(0xFFB42318)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingDevice = null; deleteError = "" }) { Text("Abbrechen") }
            }
        )
    }

    if (addRoom) {
        var name by remember { mutableStateOf("") }
        KlimaPilotAlertDialog(
            onDismissRequest = { addRoom = false },
            containerColor = Color.White,
            title = { Text("Raum anlegen", color = ManagerText) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ManagerBlue, focusedLabelColor = ManagerBlue, cursorColor = ManagerBlue)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.trim().isNotEmpty() && name.trim() !in rooms) {
                            rooms.add(name.trim())
                            save()
                            addRoom = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ManagerBlue)
                ) { Text("Anlegen") }
            },
            dismissButton = { TextButton(onClick = { addRoom = false }, colors = ButtonDefaults.textButtonColors(contentColor = ManagerMuted)) { Text("Abbrechen") } }
        )
    }

    if (configuring != null) {
        DeviceSettingsDialog(
            device = configuring!!,
            onDismiss = { configuring = null },
            otherDevices = devices.filter { it.id != configuring!!.id },
            onResetPairing = { airconId ->
                // Only the module identity of a unit no other device uses is removed.
                if (devices.any { it.id != configuring!!.id && it.wfRacAirconId == airconId }) null
                else onResetPairing(airconId)
            },
            canMigrateSyncKey = devices.count { it.syncDeviceKey == configuring!!.syncDeviceKey } == 1,
            onSave = { updated ->
                val index = devices.indexOfFirst { it.id == updated.id }
                if (index >= 0) devices[index] = updated
                save()
                configuring = null
            }
        )
    }

    if (addDevice || editing != null) {
        val original = editing
        var name by remember(original, addDevice) { mutableStateOf(original?.name ?: "") }
        var selectedRoom by remember(original, addDevice, rooms.size) { mutableStateOf(original?.room ?: rooms.first()) }
        var heatOffset by remember(original, addDevice) { mutableStateOf(original?.heatOffset ?: 0.0) }
        var heatOffsetAuto by remember(original, addDevice) { mutableStateOf(original?.heatOffsetAuto ?: false) }
        var showOffsetInfo by remember { mutableStateOf(false) }
        // Suggestion learned by the ioBroker script (only if ioBroker is set up and has learned something).
        val editorContext = androidx.compose.ui.platform.LocalContext.current
        var learned by remember(original) { mutableStateOf<Pair<Double, Int>?>(null) }
        LaunchedEffect(original) {
            val aircon = original?.wfRacAirconId.orEmpty()
            if (aircon.isBlank()) return@LaunchedEffect
            val address = editorContext.getSharedPreferences("klimapilot_settings", android.content.Context.MODE_PRIVATE).getString("iobroker_server", null).orEmpty()
            if (address.isBlank()) return@LaunchedEffect
            HeatOffsetIoBroker.load(address) { all -> learned = all[aircon]?.takeIf { it.second > 0 } }
        }
        KlimaPilotAlertDialog(
            onDismissRequest = { addDevice = false; editing = null },
            containerColor = Color.White,
            title = { Text(if (original == null) "Gerät anlegen" else "Gerät bearbeiten", color = ManagerText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Gerätename") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ManagerBlue, focusedLabelColor = ManagerBlue, cursorColor = ManagerBlue)
                    )
                    Text("Raum zuordnen", color = ManagerText, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        rooms.forEach { room ->
                            FilterChip(
                                selected = selectedRoom == room,
                                onClick = { selectedRoom = room },
                                label = { Text(room) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ManagerBlueLight,
                                    selectedLabelColor = ManagerBlue,
                                    selectedLeadingIconColor = ManagerBlue,
                                    containerColor = Color(0xFFF7F9FC),
                                    labelColor = ManagerText
                                )
                            )
                        }
                    }
                    // Heating: the sensor sits high up where warm air collects; this is taken off the shown room temperature.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Raumtemperatur beim Heizen", color = ManagerText, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showOffsetInfo = true }, colors = ButtonDefaults.textButtonColors(contentColor = ManagerMuted)) { Text("ⓘ") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { heatOffset = (heatOffset - 0.5).coerceAtLeast(0.0) }, enabled = heatOffset > 0.0) { Text("−") }
                        Text(
                            if (heatOffset == 0.0) "keine Korrektur" else "Fühler − " + String.format(java.util.Locale.GERMANY, "%.1f", heatOffset) + " °C",
                            color = ManagerText, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        OutlinedButton(onClick = { heatOffset = (heatOffset + 0.5).coerceAtMost(8.0) }, enabled = heatOffset < 8.0) { Text("+") }
                    }
                    learned?.let { (value, samples) ->
                        val rounded = Math.round(value * 2) / 2.0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Von ioBroker gelernt: " + String.format(java.util.Locale.GERMANY, "%.1f", value) + " °C (aus $samples Messung" + (if (samples == 1) ")" else "en)"),
                                color = ManagerMuted, fontSize = 12.sp, modifier = Modifier.weight(1f)
                            )
                            if (!heatOffsetAuto && rounded != heatOffset) TextButton(onClick = { heatOffset = rounded.coerceIn(0.0, 8.0) }) { Text("Übernehmen") }
                        }
                    }
                    if (!original?.wfRacAirconId.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Gelernten Wert automatisch verwenden", color = ManagerText, fontSize = 14.sp)
                                Text(
                                    if (heatOffsetAuto) "Ab 5 Messungen gilt der von ioBroker gelernte Wert; sonst der Wert oben." else "Aus: Es gilt der Wert oben.",
                                    color = ManagerMuted, fontSize = 12.sp
                                )
                            }
                            Switch(heatOffsetAuto, { heatOffsetAuto = it })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.trim().isNotEmpty()) {
                            if (original == null) devices.add(UserDevice(java.util.UUID.randomUUID().toString(), name.trim(), selectedRoom, heatOffset = heatOffset, heatOffsetAuto = heatOffsetAuto))
                            else {
                                val index = devices.indexOfFirst { it.id == original.id }
                                if (index >= 0) devices[index] = original.copy(name = name.trim(), room = selectedRoom, heatOffset = heatOffset, heatOffsetAuto = heatOffsetAuto)
                            }
                            save()
                            addDevice = false
                            editing = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ManagerBlue)
                ) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { addDevice = false; editing = null }, colors = ButtonDefaults.textButtonColors(contentColor = ManagerMuted)) { Text("Abbrechen") } }
        )
        if (showOffsetInfo) KlimaPilotAlertDialog(
            onDismissRequest = { showOffsetInfo = false },
            confirmButton = { TextButton(onClick = { showOffsetInfo = false }) { Text("OK") } },
            title = { Text("Raumtemperatur beim Heizen") },
            text = {
                Text(
                    "Der Temperaturfühler sitzt oben im Innengerät, wo sich beim Heizen die warme Luft sammelt. Er misst deshalb mehr als ein Thermometer im Raum.\n\n" +
                        "Im Betriebsmodus Heizen zieht die App diese Korrektur ab, solange der Verdichter heizt. Steht er, wird sie innerhalb von 10 Minuten bis auf null zurückgenommen, weil sich die warme Luft dann verteilt. " +
                        "Am besten über ein paar Tage mit einem Thermometer im Raum vergleichen und anpassen. " +
                        "Ist ioBroker eingerichtet, lernt das Skript den Wert mit: Es vergleicht die Temperatur kurz vor und 10–20 Minuten nach einem Verdichterstopp und zeigt hier einen Vorschlag zum Übernehmen. " +
                        "Mit „Gelernten Wert automatisch verwenden“ nimmt die App diesen Wert selbst (ab 5 Messungen, auf 0,5 °C gerundet) und passt sich so an Winter und Übergangszeit an; ohne ioBroker gilt der eingestellte Wert. " +
                        "Nur Anzeige: An der Regelung der Anlage ändert sich nichts."
                )
            }
        )
    }
}


/** Heating correction learned by the ioBroker script, per unit (airconId → value, samples). Optional: without ioBroker nothing is shown. */
internal object HeatOffsetIoBroker {
    const val STATE_ID = "0_userdata.0.KlimaPilot.heatOffset"

    fun load(address: String, callback: (Map<String, Pair<Double, Int>>) -> Unit) {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val url = address.trim().trimEnd('/')
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) { callback(emptyMap()); return }
        val socket = IoBrokerLease(url)
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        val finish: (Map<String, Pair<Double, Int>>) -> Unit = { data ->
            if (done.compareAndSet(false, true)) {
                runCatching { socket.off() }
                runCatching { socket.disconnect() }
                main.post { callback(data) }
            }
        }
        main.postDelayed({ finish(emptyMap()) }, 15_000)
        socket.on("connect") {
            socket.emit("authenticate", IoBrokerAck { args ->
                if (args.firstOrNull() as? Boolean != true) { finish(emptyMap()); return@IoBrokerAck }
                socket.emit("getState", STATE_ID, IoBrokerAck { stateArgs ->
                    val raw = (stateArgs.getOrNull(1) as? org.json.JSONObject)?.opt("val") as? String
                    val json = raw?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
                    val result = mutableMapOf<String, Pair<Double, Int>>()
                    json?.keys()?.forEach { id ->
                        val e = json.optJSONObject(id) ?: return@forEach
                        val value = e.optDouble("learned", Double.NaN)
                        if (!value.isNaN()) result[id] = value to e.optInt("samples", 0)
                    }
                    finish(result)
                })
            })
        }
        socket.on("connect_error") { finish(emptyMap()) }
        socket.connect()
    }
}


/** Last learned heating corrections read from ioBroker (airconId → value, samples), kept for the "automatic" switch. */
internal object LearnedHeatOffsets {
    private const val PREFS = "klimapilot_settings"
    private const val KEY = "learned_heat_offsets"
    @Volatile private var cache: Map<String, Pair<Double, Int>>? = null

    fun store(context: android.content.Context, values: Map<String, Pair<Double, Int>>) {
        if (values.isEmpty()) return
        cache = values
        val json = org.json.JSONObject()
        values.forEach { (id, v) -> json.put(id, org.json.JSONObject().put("learned", v.first).put("samples", v.second)) }
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putString(KEY, json.toString()).apply()
    }

    private fun all(context: android.content.Context): Map<String, Pair<Double, Int>> = cache ?: run {
        val raw = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(KEY, null)
        val json = raw?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
        val map = mutableMapOf<String, Pair<Double, Int>>()
        json?.keys()?.forEach { id -> json.optJSONObject(id)?.let { map[id] = it.optDouble("learned") to it.optInt("samples") } }
        map.also { cache = it }
    }

    /** The correction that applies: the learned one (rounded to 0.5, from 5 measurements) if switched on, else the set one. */
    fun effective(context: android.content.Context, device: UserDevice?): Double {
        device ?: return 0.0
        if (device.heatOffsetAuto) {
            all(context)[device.wfRacAirconId]?.takeIf { it.second >= 5 && !it.first.isNaN() }?.let { return (Math.round(it.first * 2) / 2.0).coerceIn(0.0, 8.0) }
        }
        return device.heatOffset
    }
}
