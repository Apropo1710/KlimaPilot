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

internal data class UserDevice(val id: String, val name: String, val room: String, val adapterKey: String = "generic", val adapterInstance: String = "", val rootObjectId: String = "", val mappings: Map<String, String> = emptyMap(), val model: String = "", val syncDeviceKey: String = "", val wfRacHost: String = "", val wfRacAirconId: String = "", val demoMode: Boolean = false)

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
        AlertDialog(
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
        AlertDialog(
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
        AlertDialog(
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
        AlertDialog(
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
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.trim().isNotEmpty()) {
                            if (original == null) devices.add(UserDevice(java.util.UUID.randomUUID().toString(), name.trim(), selectedRoom))
                            else {
                                val index = devices.indexOfFirst { it.id == original.id }
                                if (index >= 0) devices[index] = original.copy(name = name.trim(), room = selectedRoom)
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
    }
}
