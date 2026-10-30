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

internal data class UserDevice(val id: String, val name: String, val room: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoomDeviceManagerScreen(
    rooms: SnapshotStateList<String>,
    devices: SnapshotStateList<UserDevice>,
    save: () -> Unit,
    rename: (String, String) -> Unit,
    onRemoveRoom: (String) -> Unit,
    back: () -> Unit
) {
    var addRoom by remember { mutableStateOf(false) }
    var addDevice by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<UserDevice?>(null) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, contentDescription = "Zurück", tint = ManagerText) }
            Text("Räume & Geräte", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = ManagerText)
        }

        Text("Jeder Raum zeigt direkt die ihm zugeordneten Geräte.", color = ManagerMuted, fontSize = 13.sp)

        rooms.toList().forEach { room ->
            var name by remember(room) { mutableStateOf(room) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ManagerBorder)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MeetingRoom, contentDescription = null, tint = ManagerBlue)
                        Spacer(Modifier.width(8.dp))
                        Text(room, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ManagerText)
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Raumname") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ManagerBlue,
                            focusedLabelColor = ManagerBlue,
                            cursorColor = ManagerBlue
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
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
                            Text("Umbenennen")
                        }
                        OutlinedButton(
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
                            Text("Löschen")
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
                                Modifier.fillMaxWidth().background(ManagerBlueLight, RoundedCornerShape(12.dp)).padding(start = 12.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AcUnit, contentDescription = null, tint = ManagerBlue)
                                Spacer(Modifier.width(9.dp))
                                Text(device.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = ManagerText)
                                IconButton(onClick = { editing = device }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Gerät bearbeiten", tint = ManagerBlue)
                                }
                                IconButton(onClick = { devices.remove(device); save() }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Gerät löschen", tint = ManagerMuted)
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            editing = null
                            addDevice = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ManagerBlue),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ManagerBlue)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Gerät diesem Raum hinzufügen")
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { addRoom = true },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ManagerBlue),
            border = androidx.compose.foundation.BorderStroke(1.dp, ManagerBlue)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Raum hinzufügen")
        }
        Text("Die Einträge werden lokal gespeichert. Die echte ioBroker-Anbindung folgt später.", fontSize = 12.sp, color = ManagerMuted)
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
