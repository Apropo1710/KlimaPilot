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

/** Local device details only; transport and module identity are managed by the WF-RAC connection. */
@Composable
internal fun DeviceSettingsDialog(device: UserDevice, onDismiss: () -> Unit, onSave: (UserDevice) -> Unit) {
    var model by remember(device.id) { mutableStateOf(device.model) }

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
                Text("GERÄTEVERBINDUNG", color = Color(0xFF63748A), fontWeight = FontWeight.Bold)
                Text(
                    "Die Verbindung wird hier für dieses Klimagerät eingerichtet. Der aktuelle Verbindungstest bleibt read-only.",
                    color = Color(0xFF63748A),
                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                )
                WfRacConnectionCard(onConnectionChanged = {})
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
