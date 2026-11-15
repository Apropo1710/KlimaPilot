package de.klimapilot.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import de.klimapilot.app.wfrac.AirconState
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
    val fanSpeed: Int?,
    val verticalSwing: Int?,
    val horizontalSwing: Int?,
    val threeDAuto: Boolean?,
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
        fanSpeed?.let { add("Lüfter: " + listOf("Auto", "Leise", "Stufe 1", "Stufe 2", "Stufe 3").getOrElse(it) { "?" }) }
        verticalSwing?.let { add("Vertikal: " + listOf("Auto", "Ganz oben", "Mitte oben", "Mitte unten", "Ganz unten").getOrElse(it) { "?" }) }
        horizontalSwing?.let { add("Horizontal: " + listOf("Auto", "Links außen", "Links", "Mitte links", "Mitte", "Mitte rechts", "Rechts", "Rechts außen").getOrElse(it) { "?" }) }
        threeDAuto?.let { add(if (it) "3D Auto ein" else "3D Auto aus") }
    }.joinToString(" · ")
}

/** Local-first storage. This class never connects to ioBroker or the climate module. */
internal class ClimateProfileRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("klimapilot_climate_profiles", Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<ClimateProfile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        val root = JSONObject(raw)
        val schemaVersion = root.optInt("schemaVersion", -1)
        require(schemaVersion in 1..SCHEMA_VERSION) { "Unbekannte Version der Klimaprofile." }
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
            fun optionalIndex(key: String, max: Int): Int? =
                if (item.isNull(key) || !item.has(key)) null else item.getInt(key).also { require(it in 0..max) { "Ein Klimaprofil enthält eine ungültige Einstellung." } }
            ClimateProfile(
                id, deviceId, name,
                if (item.isNull("power")) null else item.getBoolean("power"),
                mode, temp,
                optionalIndex("fanSpeed", 4),
                optionalIndex("verticalSwing", 4),
                optionalIndex("horizontalSwing", 7),
                if (item.isNull("threeDAuto") || !item.has("threeDAuto")) null else item.getBoolean("threeDAuto"),
                item.optLong("updatedAt", 0L).coerceAtLeast(0L)
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
        require(profile.fanSpeed == null || profile.fanSpeed in 0..4)
        require(profile.verticalSwing == null || profile.verticalSwing in 0..4)
        require(profile.horizontalSwing == null || profile.horizontalSwing in 0..7)
        require(profile.power != null || profile.mode != null || profile.targetTemp != null ||
            profile.fanSpeed != null || profile.verticalSwing != null || profile.horizontalSwing != null ||
            profile.threeDAuto != null) {
            "Bitte mindestens eine Einstellung für das Profil auswählen."
        }
        val next = load().filterNot { it.id == profile.id } + profile.copy(name = profile.name.trim())
        write(next)
    }

    @Synchronized
    fun delete(profileId: String) {
        write(load().filterNot { it.id == profileId })
    }

    /** Applies a remote event locally without creating a new outbound event. */
    @Synchronized
    fun applyRemoteEvent(event: JSONObject, localDeviceId: String) {
        require(localDeviceId.isNotBlank())
        val profileId = event.optString("profileId").takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Synchronisierungsereignis ohne Profil-ID.")
        when (event.optString("type")) {
            "delete" -> write(load().filterNot { it.id == profileId })
            "upsert" -> {
                val item = event.optJSONObject("profile")
                    ?: throw IllegalArgumentException("Synchronisierungsereignis enthält kein Profil.")
                val name = item.optString("name").trim().takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("Synchronisiertes Profil hat keinen Namen.")
                val mode = if (item.isNull("mode")) null else item.optString("mode")
                val targetTemp = if (item.isNull("targetTemp")) null else item.optDouble("targetTemp")
                val profile = ClimateProfile(
                    id = profileId,
                    deviceId = localDeviceId,
                    name = name,
                    power = if (item.isNull("power")) null else item.optBoolean("power"),
                    mode = mode,
                    targetTemp = targetTemp,
                    fanSpeed = if (item.isNull("fanSpeed")) null else item.optInt("fanSpeed"),
                    verticalSwing = if (item.isNull("verticalSwing")) null else item.optInt("verticalSwing"),
                    horizontalSwing = if (item.isNull("horizontalSwing")) null else item.optInt("horizontalSwing"),
                    threeDAuto = if (item.isNull("threeDAuto")) null else item.optBoolean("threeDAuto"),
                    updatedAt = item.optLong("updatedAt", 0L).coerceAtLeast(0L)
                )
                save(profile)
            }
            else -> throw IllegalArgumentException("Unbekannter Synchronisierungstyp.")
        }
    }

    private fun write(profiles: List<ClimateProfile>) {
        val json = JSONObject().put("schemaVersion", SCHEMA_VERSION).put("profiles", JSONArray().apply {
            profiles.forEach { item ->
                put(JSONObject()
                    .put("id", item.id).put("deviceId", item.deviceId).put("name", item.name)
                    .put("power", item.power ?: JSONObject.NULL)
                    .put("mode", item.mode ?: JSONObject.NULL)
                    .put("targetTemp", item.targetTemp ?: JSONObject.NULL)
                    .put("fanSpeed", item.fanSpeed ?: JSONObject.NULL)
                    .put("verticalSwing", item.verticalSwing ?: JSONObject.NULL)
                    .put("horizontalSwing", item.horizontalSwing ?: JSONObject.NULL)
                    .put("threeDAuto", item.threeDAuto ?: JSONObject.NULL)
                    .put("updatedAt", item.updatedAt))
            }
        })
        check(prefs.edit().putString(KEY_PROFILES, json.toString()).commit()) {
            "Klimaprofile konnten nicht lokal gespeichert werden."
        }
    }

    companion object {
        private const val KEY_PROFILES = "climate_profiles_v1"
        private const val SCHEMA_VERSION = 2
        private val SUPPORTED_MODES = setOf("AUTO", "COOL", "HEAT", "DRY", "FAN")
    }
}

@Composable
internal fun ClimateProfilesSection(deviceId: String, syncDeviceKey: String, devices: List<UserDevice>, currentState: AirconState? = null, managerMode: Boolean = false, onApply: (ClimateProfile) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember(context) { ClimateProfileRepository(context) }
    val syncStore = remember(context) { ClimateProfileSyncStore(context) }
    val syncClient = remember { ClimateProfileSyncClient() }
    val settingsPrefs = remember(context) { context.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE) }
    var syncRevision by remember { mutableIntStateOf(0) }
    val profiles = remember { mutableStateListOf<ClimateProfile>() }
    var loaded by remember { mutableStateOf(false) }
    var loadSucceeded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ClimateProfile?>(null) }
    var deleting by remember { mutableStateOf<ClimateProfile?>(null) }
    var selectedProfileId by remember(deviceId) { mutableStateOf<String?>(null) }
    var selectedProfileConfirmed by remember(deviceId) { mutableStateOf(false) }
    var profileMenuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(repository) {
        runCatching { repository.load() }
            .onSuccess { profiles.clear(); profiles.addAll(it); loadSucceeded = true; loaded = true }
            .onFailure { loadSucceeded = false; status = it.message ?: "Klimaprofile konnten nicht geladen werden."; loaded = true }
    }

    val deviceProfiles = profiles.filter { it.deviceId == deviceId }
    val selectedProfile = deviceProfiles.firstOrNull { it.id == selectedProfileId }
    LaunchedEffect(selectedProfileId, currentState?.power, currentState?.mode, currentState?.presetTemp, currentState?.airFlow, currentState?.windUD, currentState?.windLR, currentState?.entrust) {
        val selected = selectedProfile ?: return@LaunchedEffect
        val state = currentState ?: return@LaunchedEffect
        val matches = (selected.power == null || selected.power == state.power) &&
            (selected.mode == null || selected.mode == state.mode?.name) &&
            (selected.targetTemp == null || kotlin.math.abs(selected.targetTemp - state.presetTemp) < 0.01) &&
            (selected.fanSpeed == null || selected.fanSpeed == state.airFlow) &&
            (selected.verticalSwing == null || selected.verticalSwing == state.windUD) &&
            (selected.horizontalSwing == null || selected.horizontalSwing == state.windLR) &&
            (selected.threeDAuto == null || selected.threeDAuto == state.entrust)
        if (matches) selectedProfileConfirmed = true
        else if (selectedProfileConfirmed) {
            selectedProfileId = null
            selectedProfileConfirmed = false
        }
    }
    val deviceSyncSignature = devices.joinToString("|") { it.id + "=" + it.syncDeviceKey }
    LaunchedEffect(loaded, loadSucceeded, deviceSyncSignature, syncRevision) {
        if (!loaded || !loadSucceeded) return@LaunchedEffect
        devices.filter { it.syncDeviceKey.isNotBlank() }.forEach { device ->
            runCatching { syncStore.enqueueInitialProfiles(profiles.filter { it.deviceId == device.id }, device.syncDeviceKey) }
                .onFailure { status = it.message ?: "Vorhandene Profile konnten nicht zur Synchronisierung vorgemerkt werden." }
        }
        val address = settingsPrefs.getString("iobroker_server", null).orEmpty()
        if (devices.none { it.syncDeviceKey.isNotBlank() } || address.isBlank()) return@LaunchedEffect
        syncClient.syncNow(address, devices, syncStore, repository) { message ->
            status = message
            runCatching { repository.load() }.onSuccess { latest -> profiles.clear(); profiles.addAll(latest) }
        }
    }
    LaunchedEffect(loaded, loadSucceeded, deviceSyncSignature) {
        if (!loaded || !loadSucceeded || devices.none { it.syncDeviceKey.isNotBlank() }) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            val address = settingsPrefs.getString("iobroker_server", null).orEmpty()
            if (address.isNotBlank()) syncClient.syncNow(address, devices, syncStore, repository) { message ->
                status = message
                runCatching { repository.load() }.onSuccess { latest -> profiles.clear(); profiles.addAll(latest) }
            }
        }
    }
    Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Klimaprofile", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${deviceProfiles.size}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            if (!loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (deviceId.isBlank()) Text("Wähle zuerst eine Klimaanlage aus.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            else {
                if (!managerMode) {
                    Box {
                        Surface(onClick = { profileMenuExpanded = true }, shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(selectedProfile?.let { profileSymbol(it) } ?: "⚙️", fontSize = 19.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(selectedProfile?.name ?: "Individuell", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(if (selectedProfile == null) "Eigene Einstellungen" else selectedProfile.summary(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1)
                                }
                                Text("⌄", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        DropdownMenu(expanded = profileMenuExpanded, onDismissRequest = { profileMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text("⚙️  Individuell") },
                                onClick = { selectedProfileId = null; selectedProfileConfirmed = false; profileMenuExpanded = false })
                            deviceProfiles.forEach { profile ->
                                DropdownMenuItem(text = { Text(profileSymbol(profile) + "  " + profile.name) },
                                    onClick = { selectedProfileId = profile.id; selectedProfileConfirmed = false; profileMenuExpanded = false; onApply(profile) })
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { creating = true }, enabled = currentState != null) {
                            Text("＋ Aktuelle Einstellungen speichern", fontSize = 11.sp)
                        }
                    }
                } else if (deviceProfiles.isEmpty()) {
                    Text("Noch keine Profile für dieses Gerät gespeichert.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                } else deviceProfiles.forEach { profile ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(profile.summary(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        }
                        TextButton(onClick = { editing = profile }) { Text("Bearbeiten") }
                        TextButton(onClick = { deleting = profile }) { Text("Löschen") }
                    }
                }
            }
            if (status.isNotBlank()) {
                val failed = listOf("fehlgeschlagen", "konnte nicht", "ungültig", "abgebrochen", "beschädigt")
                    .any { status.contains(it, ignoreCase = true) }
                Text(
                    status,
                    color = if (failed) MaterialTheme.colorScheme.error
                    else if (status.startsWith("Synchronisierung abgeschlossen")) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }

    val profileToEdit = editing
    if (creating || profileToEdit != null) {
        ClimateProfileEditorDialog(
            deviceId = deviceId, profile = profileToEdit, initialState = if (creating) currentState else null,
            onDismiss = { creating = false; editing = null },
            onSave = { profile ->
                runCatching { repository.save(profile) }
                    .onSuccess {
                        val index = profiles.indexOfFirst { it.id == profile.id }
                        if (index >= 0) profiles[index] = profile else profiles.add(profile)
                        status = if (syncDeviceKey.isBlank()) {
                            "Lokal gespeichert. Für die Synchronisierung auf anderen Smartphones fehlt noch die gemeinsame Gerätekennung."
                        } else {
                            runCatching { syncStore.enqueueUpsert(profile, syncDeviceKey) }
                                .fold({ "" }, { it.message ?: "Profil lokal gespeichert; Synchronisierung konnte nicht vorgemerkt werden." })
                        }
                        syncRevision++
                        creating = false; editing = null
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
                        .onSuccess {
                            profiles.removeAll { it.id == profile.id }
                            syncRevision++
                            status = when {
                                syncDeviceKey.isBlank() ->
                                    "Lokal gelöscht. Ohne gemeinsame Gerätekennung kann die Löschung nicht auf andere Smartphones übertragen werden."
                                else -> runCatching {
                                    check(syncStore.enqueueDelete(profile.id, syncDeviceKey)) {
                                        "Löschung konnte nicht zur Synchronisierung vorgemerkt werden."
                                    }
                                }.fold(
                                    { "" },
                                    { "Lokal gelöscht, aber die Synchronisierung konnte nicht vorgemerkt werden: ${it.message ?: "Unbekannter Fehler"}" }
                                )
                            }
                        }
                        .onFailure { status = it.message ?: "Profil konnte nicht gelöscht werden." }
                    deleting = null
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Abbrechen") } }
        )
    }
}


private fun profileSymbol(profile: ClimateProfile): String = when (profile.mode) {
    "HEAT" -> "☀️"
    "COOL" -> "❄️"
    "DRY" -> "💧"
    "FAN" -> "🌀"
    "AUTO" -> "🔄"
    else -> "🌡️"
}

@Composable
internal fun ClimateProfileManagerScreen(devices: List<UserDevice>, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Zurück") }
            Text("Klimaprofile verwalten", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            devices.forEach { device ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(device.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    ClimateProfilesSection(deviceId = device.id, syncDeviceKey = device.syncDeviceKey, devices = devices, managerMode = true, onApply = {})
                }
            }
        }
    }
}
@Composable
private fun ClimateProfileEditorDialog(
    deviceId: String,
    profile: ClimateProfile?,
    initialState: AirconState? = null,
    onDismiss: () -> Unit,
    onSave: (ClimateProfile) -> Unit
) {
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var includePower by remember(profile?.id) { mutableStateOf(profile?.power != null || initialState != null) }
    var power by remember(profile?.id) { mutableStateOf(profile?.power ?: initialState?.power ?: true) }
    var includeMode by remember(profile?.id) { mutableStateOf(profile?.mode != null || initialState != null) }
    var mode by remember(profile?.id) { mutableStateOf(profile?.mode ?: initialState?.mode?.name ?: "COOL") }
    var includeTemp by remember(profile?.id) { mutableStateOf(profile?.targetTemp != null || initialState != null) }
    var tempText by remember(profile?.id) { mutableStateOf((profile?.targetTemp ?: initialState?.presetTemp)?.toString()?.replace('.', ',') ?: "22,0") }
    var includeFan by remember(profile?.id) { mutableStateOf(profile?.fanSpeed != null || initialState != null) }
    var fanSpeed by remember(profile?.id) { mutableIntStateOf(profile?.fanSpeed ?: initialState?.airFlow ?: 0) }
    var includeVertical by remember(profile?.id) { mutableStateOf(profile?.verticalSwing != null || initialState != null) }
    var verticalSwing by remember(profile?.id) { mutableIntStateOf(profile?.verticalSwing ?: initialState?.windUD ?: 0) }
    var includeHorizontal by remember(profile?.id) { mutableStateOf(profile?.horizontalSwing != null || initialState != null) }
    var horizontalSwing by remember(profile?.id) { mutableIntStateOf(profile?.horizontalSwing ?: initialState?.windLR ?: 0) }
    var include3d by remember(profile?.id) { mutableStateOf(profile?.threeDAuto != null || initialState != null) }
    var threeDAuto by remember(profile?.id) { mutableStateOf(profile?.threeDAuto ?: initialState?.entrust ?: false) }

    val temp = tempText.trim().replace(',', '.').toDoubleOrNull()
    val tempValid = !includeTemp || (temp != null && temp.isFinite() && temp in 16.0..30.0 &&
        abs(temp * 2.0 - (temp * 2.0).roundToInt()) < 0.001)
    val anySetting = includePower || includeMode || includeTemp || includeFan || includeVertical ||
        includeHorizontal || include3d
    val modes = listOf("AUTO" to "Automatik", "COOL" to "Kühlen", "HEAT" to "Heizen", "DRY" to "Entfeuchten", "FAN" to "Lüfter")
    val fanOptions = listOf("Automatik", "Leise", "Stufe 1", "Stufe 2", "Stufe 3")
    val verticalOptions = listOf("Automatik", "Ganz oben", "Mitte oben", "Mitte unten", "Ganz unten")
    val horizontalOptions = listOf("Automatik", "Links außen", "Links", "Mitte links", "Mitte", "Mitte rechts", "Rechts", "Rechts außen")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) "Neues Klimaprofil" else "Klimaprofil bearbeiten") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Profilname") },
                    placeholder = { Text("z. B. Nachtmodus") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                HorizontalDivider()
                Text(if (profile == null) "Die aktuellen Einstellungen werden übernommen. Du kannst nur den Namen festlegen oder Werte bei Bedarf anpassen." else "Gespeicherte Einstellungen", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(includeFan, { includeFan = it }); Text("Lüftergeschwindigkeit")
                }
                if (includeFan) fanOptions.forEachIndexed { index, label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = fanSpeed == index, onClick = { fanSpeed = index }); Text(label)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(includeVertical, { includeVertical = it }); Text("Vertikale Luftführung")
                }
                if (includeVertical) verticalOptions.forEachIndexed { index, label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = verticalSwing == index, onClick = { verticalSwing = index }); Text(label)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(includeHorizontal, { includeHorizontal = it }); Text("Horizontale Luftführung")
                }
                if (includeHorizontal) horizontalOptions.forEachIndexed { index, label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = horizontalSwing == index, onClick = { horizontalSwing = index }); Text(label)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(include3d, { include3d = it }); Text("3D-Automatik")
                }
                if (include3d) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = threeDAuto, onClick = { threeDAuto = true }, label = { Text("Ein") })
                    FilterChip(selected = !threeDAuto, onClick = { threeDAuto = false }, label = { Text("Aus") })
                }
                Text(
                    "Horizontale Luftführung und 3D-Automatik sind modell- bzw. firmwareabhängig. Vor dem Anwenden werden die Fähigkeiten des Geräts geprüft.",
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
                    fanSpeed = if (includeFan) fanSpeed else null,
                    verticalSwing = if (includeVertical) verticalSwing else null,
                    horizontalSwing = if (includeHorizontal) horizontalSwing else null,
                    threeDAuto = if (include3d) threeDAuto else null,
                    updatedAt = System.currentTimeMillis()
                ))
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
