package de.klimapilot.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/** Local-first profile; null fields are intentionally left untouched when applied later. */
internal data class ClimateProfile(
    val id: String,
    val deviceId: String,
    val name: String,
    val power: Boolean?,
    val mode: String?,
    val targetTemp: Double?,
    val updatedAt: Long
) {
    fun summary(): String = buildList {
        power?.let { add(if (it) "Ein" else "Aus") }
        mode?.let { add(when (it) {
            "AUTO" -> "Auto"
            "COOL" -> "Kühlen"
            "HEAT" -> "Heizen"
            "DRY" -> "Entfeuchten"
            "FAN" -> "Lüfter"
            else -> it
        }) }
        targetTemp?.let { add("${it.toString().replace('.', ',')} °C") }
    }.joinToString(" · ")
}

/** Local-first storage. This class never connects to ioBroker or the climate module. */
internal class ClimateProfileRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("klimapilot_climate_profiles", Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<ClimateProfile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        val root = JSONObject(raw)
        require(root.optInt("schemaVersion", -1) == SCHEMA_VERSION) { "Unbekannte Version der Klimaprofile." }
        val array = root.optJSONArray("profiles") ?: throw IllegalStateException("Die Klimaprofile sind beschädigt.")
        val profiles = (0 until array.length()).map { index ->
            val item = array.optJSONObject(index) ?: throw IllegalStateException("Ein Klimaprofil ist ungültig.")
            val id = item.optString("id").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Ein Klimaprofil hat keine ID.")
            val deviceId = item.optString("deviceId").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Ein Klimaprofil ist keinem Gerät zugeordnet.")
            val name = item.optString("name").trim().takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Ein Klimaprofil hat keinen Namen.")
            val mode = if (item.isNull("mode")) null else item.optString("mode").also {
                require(it in SUPPORTED_MODES) { "Ein Klimaprofil enthält einen unbekannten Betriebsmodus." }
            }
            val temp = if (item.isNull("targetTemp")) null else item.optDouble("targetTemp").also {
                require(it.isFinite() && it in 16.0..30.0 && abs(it * 2.0 - (it * 2.0).roundToInt()) < 0.001) {
                    "Ein Klimaprofil enthält eine ungültige Solltemperatur."
                }
            }
            ClimateProfile(
                id, deviceId, name,
                if (item.isNull("power")) null else item.getBoolean("power"),
                mode, temp, item.optLong("updatedAt", 0L).coerceAtLeast(0L)
            )
        }
        require(profiles.map { it.id }.distinct().size == profiles.size) { "Klimaprofil-IDs sind nicht eindeutig." }
        return profiles
    }

    @Synchronized
    fun save(profile: ClimateProfile) {
        require(profile.id.isNotBlank() && profile.deviceId.isNotBlank() && profile.name.isNotBlank())
        require(profile.mode == null || profile.mode in SUPPORTED_MODES)
        profile.targetTemp?.let {
            require(it.isFinite() && it in 16.0..30.0 && abs(it * 2.0 - (it * 2.0).roundToInt()) < 0.001)
        }
        require(profile.power != null || profile.mode != null || profile.targetTemp != null) {
            "Bitte mindestens eine Einstellung für das Profil auswählen."
        }
        val next = load().filterNot { it.id == profile.id } + profile.copy(name = profile.name.trim())
        write(next)
    }

    @Synchronized
    fun delete(profileId: String) {
        write(load().filterNot { it.id == profileId })
    }

    private fun write(profiles: List<ClimateProfile>) {
        val json = JSONObject().put("schemaVersion", SCHEMA_VERSION).put("profiles", JSONArray().apply {
            profiles.forEach { item ->
                put(JSONObject()
                    .put("id", item.id).put("deviceId", item.deviceId).put("name", item.name)
                    .put("power", item.power ?: JSONObject.NULL)
                    .put("mode", item.mode ?: JSONObject.NULL)
                    .put("targetTemp", item.targetTemp ?: JSONObject.NULL)
                    .put("updatedAt", item.updatedAt))
            }
        })
        check(prefs.edit().putString(KEY_PROFILES, json.toString()).commit()) {
            "Klimaprofile konnten nicht lokal gespeichert werden."
        }
    }

    companion object {
        private const val KEY_PROFILES = "climate_profiles_v1"
        private const val SCHEMA_VERSION = 1
        private val SUPPORTED_MODES = setOf("AUTO", "COOL", "HEAT", "DRY", "FAN")
    }
}

@Composable
internal fun ClimateProfilesSection(deviceId: String, deviceName: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember(context) { ClimateProfileRepository(context) }
    val profiles = remember { mutableStateListOf<ClimateProfile>() }
    var loaded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ClimateProfile?>(null) }
    var deleting by remember { mutableStateOf<ClimateProfile?>(null) }

    LaunchedEffect(repository) {
        runCatching { repository.load() }
            .onSuccess { profiles.clear(); profiles.addAll(it); loaded = true }
            .onFailure { status = it.message ?: "Klimaprofile konnten nicht geladen werden."; loaded = true }
    }

    val deviceProfiles = profiles.filter { it.deviceId == deviceId }
    Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Klimaprofile", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(deviceName.ifBlank { "Kein Gerät ausgewählt" }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text("${deviceProfiles.size}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            if (!loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (deviceId.isBlank()) Text("Wähle zuerst eine Klimaanlage aus.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            else {
                if (deviceProfiles.isEmpty()) {
                    Text("Noch keine Profile für dieses Gerät gespeichert.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                } else deviceProfiles.forEach { profile ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, fontWeight = FontWeight.SemiBold)
                            Text(profile.summary(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        TextButton(onClick = { editing = profile }) { Text("Bearbeiten") }
                        TextButton(onClick = { deleting = profile }) { Text("Löschen") }
                    }
                }
                Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Neues Klimaprofil erstellen")
                }
                Text(
                    "Die Profile werden lokal auf diesem Smartphone gespeichert. Anwenden wird erst nach Prüfung und Freigabe der Schreibsteuerung aktiviert.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp
                )
            }
            if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }

    val profileToEdit = editing
    if (creating || profileToEdit != null) {
        ClimateProfileEditorDialog(
            deviceId = deviceId, profile = profileToEdit,
            onDismiss = { creating = false; editing = null },
            onSave = { profile ->
                runCatching { repository.save(profile) }
                    .onSuccess {
                        val index = profiles.indexOfFirst { it.id == profile.id }
                        if (index >= 0) profiles[index] = profile else profiles.add(profile)
                        status = ""; creating = false; editing = null
                    }
                    .onFailure { status = it.message ?: "Profil konnte nicht gespeichert werden." }
            }
        )
    }

    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Profil löschen?") },
            text = { Text("„${profile.name}“ wird von diesem Smartphone gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { repository.delete(profile.id) }
                        .onSuccess { profiles.removeAll { it.id == profile.id }; status = "" }
                        .onFailure { status = it.message ?: "Profil konnte nicht gelöscht werden." }
                    deleting = null
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun ClimateProfileEditorDialog(
    deviceId: String,
    profile: ClimateProfile?,
    onDismiss: () -> Unit,
    onSave: (ClimateProfile) -> Unit
) {
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var includePower by remember(profile?.id) { mutableStateOf(profile?.power != null || profile == null) }
    var power by remember(profile?.id) { mutableStateOf(profile?.power ?: true) }
    var includeMode by remember(profile?.id) { mutableStateOf(profile?.mode != null || profile == null) }
    var mode by remember(profile?.id) { mutableStateOf(profile?.mode ?: "COOL") }
    var includeTemp by remember(profile?.id) { mutableStateOf(profile?.targetTemp != null || profile == null) }
    var tempText by remember(profile?.id) { mutableStateOf(profile?.targetTemp?.toString()?.replace('.', ',') ?: "22,0") }

    val temp = tempText.trim().replace(',', '.').toDoubleOrNull()
    val tempValid = !includeTemp || (temp != null && temp.isFinite() && temp in 16.0..30.0 &&
        abs(temp * 2.0 - (temp * 2.0).roundToInt()) < 0.001)
    val anySetting = includePower || includeMode || includeTemp
    val modes = listOf("AUTO" to "Automatik", "COOL" to "Kühlen", "HEAT" to "Heizen", "DRY" to "Entfeuchten", "FAN" to "Lüfter")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) "Neues Klimaprofil" else "Klimaprofil bearbeiten") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Profilname") },
                    placeholder = { Text("z. B. Nachtmodus") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                HorizontalDivider()
                Text("Welche Einstellungen gehören in dieses Profil?", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(includePower, { includePower = it }); Text("Ein-/Aus-Zustand")
                }
                if (includePower) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = power, onClick = { power = true }, label = { Text("Ein") })
                    FilterChip(selected = !power, onClick = { power = false }, label = { Text("Aus") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(includeMode, { includeMode = it }); Text("Betriebsmodus")
                }
                if (includeMode) modes.forEach { (value, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == value, onClick = { mode = value }); Text(label)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(includeTemp, { includeTemp = it }); Text("Solltemperatur")
                }
                if (includeTemp) OutlinedTextField(
                    value = tempText, onValueChange = { tempText = it },
                    label = { Text("Temperatur in °C") },
                    supportingText = { Text("16–30 °C in 0,5-°C-Schritten") },
                    isError = !tempValid, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Lüfterstufe und weitere modellabhängige Optionen ergänzen wir nach Prüfung am installierten Gerät.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && anySetting && tempValid, onClick = {
                onSave(ClimateProfile(
                    id = profile?.id ?: UUID.randomUUID().toString(),
                    deviceId = deviceId, name = name.trim(),
                    power = if (includePower) power else null,
                    mode = if (includeMode) mode else null,
                    targetTemp = if (includeTemp) temp else null,
                    updatedAt = System.currentTimeMillis()
                ))
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
