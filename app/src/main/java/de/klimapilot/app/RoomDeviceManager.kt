package de.klimapilot.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, contentDescription = "Zurück") }
            Text("Räume & Geräte", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
        Text("RÄUME", fontWeight = FontWeight.Bold, color = Color.Gray)
        rooms.toList().forEach { room ->
            var name by remember(room) { mutableStateOf(room) }
            Card {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Raumname") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            enabled = name.trim().isNotEmpty() && name.trim() != room && name.trim() !in rooms,
                            onClick = {
                                val newName = name.trim()
                                rooms[rooms.indexOf(room)] = newName
                                devices.indices.forEach { i -> if (devices[i].room == room) devices[i] = devices[i].copy(room = newName) }
                                rename(room, newName)
                                save()
                            }
                        ) { Icon(Icons.Default.Edit, contentDescription = null); Text("Umbenennen") }
                        OutlinedButton(
                            enabled = rooms.size > 1,
                            onClick = {
                                rooms.remove(room)
                                onRemoveRoom(room)
                                devices.indices.filter { devices[it].room == room }.forEach { i -> devices[i] = devices[i].copy(room = rooms.first()) }
                                save()
                            }
                        ) { Icon(Icons.Default.Delete, contentDescription = null); Text("Löschen") }
                    }
                }
            }
        }
        OutlinedButton(onClick = { addRoom = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null); Text("Raum hinzufügen")
        }
        Divider()
        Text("GERÄTE", fontWeight = FontWeight.Bold, color = Color.Gray)
        devices.toList().forEach { device ->
            Card {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(device.name, fontWeight = FontWeight.SemiBold)
                        Text(device.room, color = Color.Gray, fontSize = 12.sp)
                    }
                    IconButton(onClick = { editing = device }) { Icon(Icons.Default.Edit, contentDescription = "Bearbeiten") }
                    IconButton(onClick = { devices.remove(device); save() }) { Icon(Icons.Default.Delete, contentDescription = "Löschen") }
                }
            }
        }
        Button(onClick = { addDevice = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null); Text("Gerät hinzufügen")
        }
        Text("Die Einträge werden lokal gespeichert. Die echte ioBroker-Anbindung folgt später.", fontSize = 12.sp, color = Color.Gray)
    }
    if (addRoom) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addRoom = false },
            title = { Text("Raum anlegen") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { if (name.trim().isNotEmpty() && name.trim() !in rooms) { rooms.add(name.trim()); save(); addRoom = false } }) { Text("Anlegen") } },
            dismissButton = { TextButton(onClick = { addRoom = false }) { Text("Abbrechen") } }
        )
    }
    if (addDevice || editing != null) {
        val original = editing
        var name by remember(original, addDevice) { mutableStateOf(original?.name ?: "") }
        var selectedRoom by remember(original, addDevice, rooms.size) { mutableStateOf(original?.room ?: rooms.first()) }
        AlertDialog(
            onDismissRequest = { addDevice = false; editing = null },
            title = { Text(if (original == null) "Gerät anlegen" else "Gerät bearbeiten") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Gerätename") })
                    Text("Raum zuordnen")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        rooms.forEach { room -> FilterChip(selected = selectedRoom == room, onClick = { selectedRoom = room }, label = { Text(room) }) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.trim().isNotEmpty()) {
                        if (original == null) devices.add(UserDevice(java.util.UUID.randomUUID().toString(), name.trim(), selectedRoom))
                        else {
                            val index = devices.indexOfFirst { it.id == original.id }
                            if (index >= 0) devices[index] = original.copy(name = name.trim(), room = selectedRoom)
                        }
                        save(); addDevice = false; editing = null
                    }
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { addDevice = false; editing = null }) { Text("Abbrechen") } }
        )
    }
}
