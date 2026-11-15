package de.klimapilot.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Local device details only; transport and module identity are managed by the WF-RAC connection. */
@Composable
internal fun DeviceSettingsDialog(device: UserDevice, onDismiss: () -> Unit, onSave: (UserDevice) -> Unit) {
    var model by remember(device.id) { mutableStateOf(device.model) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Geräteeinstellungen", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(device.name, color = Color(0xFF1769AA), fontWeight = FontWeight.SemiBold)
                Text(
                    "Die Verbindung und Geräteidentität werden direkt über das lokale WF-RAC-WLAN-Modul verwaltet.",
                    color = Color(0xFF63748A),
                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Modellbezeichnung (optional)") },
                    placeholder = { Text("z. B. SRK15ZS-WF") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(device.copy(model = model.trim())) }) {
                Text("Speichern", color = Color(0xFF1769AA))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
