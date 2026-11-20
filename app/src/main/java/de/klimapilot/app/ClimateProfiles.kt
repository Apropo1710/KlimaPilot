package de.klimapilot.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import de.klimapilot.app.wfrac.AirconState
import de.klimapilot.app.wfrac.Codec
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/**
 * Shared persistence path used by the profile UI when changing the active profile.
 * Returns a user-facing error, or null after the local selection/outbox commit succeeds.
 */
internal fun persistActiveProfileSelection(
    store: ClimateProfileSyncStore,
    deviceId: String,
    syncDeviceKey: String,
    profileId: String?
): String? {
    val saved = runCatching {
        if (syncDeviceKey.isBlank()) store.saveActiveProfileId(deviceId, profileId)
        else store.saveActiveProfileIdWithSync(deviceId, profileId, syncDeviceKey)
    }.getOrElse {
        return it.message ?: "Die aktive Profilauswahl konnte nicht lokal gespeichert werden."
    }
    return if (saved) null else
        "Die aktive Profilauswahl konnte nicht zusammen mit ihrer Synchronisierungsvormerkung gespeichert werden."
}

/** Local-first storage. This class never connects to ioBroker or the climate module. */
internal class ClimateProfileRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("klimapilot_climate_profiles", Context.MODE_PRIVATE)

    
    fun load(): List<ClimateProfile> {
        synchronized(ClimateProfileStorageLock.monitor) {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        val root = JSONObject(raw)
        val schemaVersion = root.optInt("schemaVersion", -1)
        check(schemaVersion in 1..SCHEMA_VERSION) { "Unbekannte Version der Klimaprofile." }
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
                check(it in SUPPORTED_MODES) { "Ein Klimaprofil enthält einen unbekannten Betriebsmodus." }
            }
            val temp = if (item.isNull("targetTemp")) null else item.optDouble("targetTemp").also {
                check(it.isFinite() && it in 16.0..30.0 && abs(it * 2.0 - (it * 2.0).roundToInt()) < 0.001) {
                    "Ein Klimaprofil enthält eine ungültige Solltemperatur."
                }
            }
            fun optionalIndex(key: String, max: Int): Int? =
                if (item.isNull(key) || !item.has(key)) null else item.getInt(key).also { check(it in 0..max) { "Ein Klimaprofil enthält eine ungültige Einstellung." } }
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
        check(profiles.map { it.id }.distinct().size == profiles.size) { "Klimaprofil-IDs sind nicht eindeutig." }
        return profiles
    
        }}

    
    fun save(profile: ClimateProfile) {
        synchronized(ClimateProfileStorageLock.monitor) {
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
    
        }}

    
    fun saveWithPendingSync(profile: ClimateProfile, deviceKey: String): ClimateProfile {
        synchronized(ClimateProfileStorageLock.monitor) {
        require(deviceKey.isNotBlank()) { "Für die Synchronisierung fehlt die gemeinsame Gerätekennung." }
        validateProfile(profile)
        val normalized = profile.copy(name = profile.name.trim())
        val next = load().filterNot { it.id == normalized.id } + normalized
        val journal = readJournal()
        journal.put(newJournalEvent("upsert", normalized.id, deviceKey, profileToJson(normalized)))
        writeProfilesAndJournal(next, journal)
        return normalized
    
        }}

    
    fun delete(profileId: String) {
        synchronized(ClimateProfileStorageLock.monitor) {
        write(load().filterNot { it.id == profileId })
    
        }}

    
    fun deleteWithPendingSync(profileId: String, deviceKey: String) {
        synchronized(ClimateProfileStorageLock.monitor) {
        require(profileId.isNotBlank() && deviceKey.isNotBlank())
        val next = load().filterNot { it.id == profileId }
        val journal = readJournal()
        journal.put(newJournalEvent("delete", profileId, deviceKey, null))
        writeProfilesAndJournal(next, journal)
    
        }}

    /** Moves durable repository journal entries into the outbox; stable IDs make retries idempotent. */
    
    fun flushPendingSyncJournal(store: ClimateProfileSyncStore): Int {
        synchronized(ClimateProfileStorageLock.monitor) {
        val journal = readJournal()
        if (journal.length() == 0) {
            check(store.retainImportedJournalIds(emptySet())) {
                "Abgeschlossene Journal-Importmarkierungen konnten nicht bereinigt werden."
            }
            return 0
        }
        var transferred = 0
        for (index in 0 until journal.length()) {
            val event = journal.optJSONObject(index)
                ?: throw IllegalStateException("Das lokale Profiländerungsjournal enthält einen ungültigen Eintrag.")
            check(store.enqueueJournalEvent(JSONObject(event.toString()))) {
                "Ein Profiländerungsereignis konnte nicht dauerhaft in die Synchronisierungswarteschlange übernommen werden."
            }
            transferred++
        }
        check(prefs.edit().putString(KEY_PENDING_SYNC_JOURNAL, JSONArray().toString()).commit()) {
            "Das Profiländerungsjournal konnte nach der Übernahme nicht bereinigt werden."
        }
        // The repository journal is now durably empty. Import markers are only needed
        // while a corresponding journal entry may still be replayed after a crash.
        check(store.retainImportedJournalIds(emptySet())) {
            "Abgeschlossene Journal-Importmarkierungen konnten nicht bereinigt werden."
        }
        return transferred
    
        }}

    private fun readJournal(): JSONArray {
        val raw = prefs.getString(KEY_PENDING_SYNC_JOURNAL, null) ?: return JSONArray()
        return try { JSONArray(raw) } catch (_: Exception) {
            throw IllegalStateException("Das lokale Profiländerungsjournal ist beschädigt und wurde nicht überschrieben.")
        }
    }

    private fun newJournalEvent(type: String, profileId: String, deviceKey: String, profile: JSONObject?): JSONObject {
        val event = JSONObject().put("eventId", "journal:" + UUID.randomUUID().toString())
            .put("type", type).put("profileId", profileId).put("deviceKey", deviceKey)
            .put("createdAt", System.currentTimeMillis())
        if (profile != null) event.put("profile", profile)
        return event
    }

    private fun writeProfilesAndJournal(profiles: List<ClimateProfile>, journal: JSONArray) {
        val json = profilesJson(profiles)
        check(prefs.edit().putString(KEY_PROFILES, json).putString(KEY_PENDING_SYNC_JOURNAL, journal.toString()).commit()) {
            "Klimaprofil und Synchronisierungsvormerkung konnten nicht gemeinsam gespeichert werden."
        }
    }

    private fun validateProfile(profile: ClimateProfile) {
        require(profile.id.isNotBlank() && profile.deviceId.isNotBlank() && profile.name.isNotBlank())
        require(profile.mode == null || profile.mode in SUPPORTED_MODES)
        profile.targetTemp?.let { require(it.isFinite() && it in 16.0..30.0 && abs(it * 2.0 - (it * 2.0).roundToInt()) < 0.001) }
        require(profile.fanSpeed == null || profile.fanSpeed in 0..4)
        require(profile.verticalSwing == null || profile.verticalSwing in 0..4)
        require(profile.horizontalSwing == null || profile.horizontalSwing in 0..7)
        require(profile.power != null || profile.mode != null || profile.targetTemp != null ||
            profile.fanSpeed != null || profile.verticalSwing != null || profile.horizontalSwing != null || profile.threeDAuto != null) {
            "Bitte mindestens eine Einstellung für das Profil auswählen."
        }
    }

    private fun profileToJson(item: ClimateProfile): JSONObject = JSONObject()
        .put("id", item.id).put("name", item.name).put("power", item.power ?: JSONObject.NULL)
        .put("mode", item.mode ?: JSONObject.NULL).put("targetTemp", item.targetTemp ?: JSONObject.NULL)
        .put("fanSpeed", item.fanSpeed ?: JSONObject.NULL).put("verticalSwing", item.verticalSwing ?: JSONObject.NULL)
        .put("horizontalSwing", item.horizontalSwing ?: JSONObject.NULL).put("threeDAuto", item.threeDAuto ?: JSONObject.NULL)
        .put("updatedAt", item.updatedAt)

    /** Applies a remote event locally without creating a new outbound event. */
    
    fun applyRemoteEvent(event: JSONObject, localDeviceId: String) {
        synchronized(ClimateProfileStorageLock.monitor) {
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
                val mode = if (item.isNull("mode")) null else item.optString("mode").also {
                    require(it in SUPPORTED_MODES) { "Synchronisiertes Profil enthält einen unbekannten Betriebsmodus." }
                }
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
    
        }}

    private fun write(profiles: List<ClimateProfile>) {
        check(prefs.edit().putString(KEY_PROFILES, profilesJson(profiles)).commit()) {
            "Klimaprofile konnten nicht lokal gespeichert werden."
        }
    }

    private fun profilesJson(profiles: List<ClimateProfile>): String =
        JSONObject().put("schemaVersion", SCHEMA_VERSION).put("profiles", JSONArray().apply {
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
        }).toString()

    companion object {
        private const val KEY_PROFILES = "climate_profiles_v1"
        private const val KEY_PENDING_SYNC_JOURNAL = "pending_sync_journal_v1"
        private const val SCHEMA_VERSION = 2
        private val SUPPORTED_MODES = setOf("AUTO", "COOL", "HEAT", "DRY", "FAN")
    }
}

@Composable
internal fun ClimateProfilesSection(deviceId: String, syncDeviceKey: String, devices: List<UserDevice>, currentState: AirconState? = null, managerMode: Boolean = false, onApply: (ClimateProfile, (Boolean, String?) -> Unit) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember(context) { ClimateProfileRepository(context) }
    val syncStore = remember(context) { ClimateProfileSyncStore(context) }
    val syncClient = remember(context) { ClimateProfileSyncClient(ClimatePeerEventLog(context)) }
    val settingsPrefs = remember(context) { context.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE) }
    var syncRevision by remember { mutableIntStateOf(0) }
    val profiles = remember { mutableStateListOf<ClimateProfile>() }
    var loaded by remember { mutableStateOf(false) }
    var loadSucceeded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ClimateProfile?>(null) }
    var saveNameDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<ClimateProfile?>(null) }
    var selectedProfileId by remember(deviceId) { mutableStateOf(syncStore.activeProfileId(deviceId)) }

    var profileMenuExpanded by remember { mutableStateOf(false) }
    // True while a profile is being applied: the shown selection already changed, the unit not yet.
    var switching by remember(deviceId) { mutableStateOf(false) }

    fun persistActiveProfile(profileId: String?, requestSync: Boolean = true): String? {
        val error = persistActiveProfileSelection(syncStore, deviceId, syncDeviceKey, profileId)
        if (error != null) return error
        selectedProfileId = profileId
        if (syncDeviceKey.isNotBlank() && requestSync) syncRevision++
        return null
    }

    fun captureIndividualSettings(): String? {
        val state = currentState
            ?: return "Der aktuelle Gerätestatus fehlt. Der Profilwechsel wurde abgebrochen, damit keine individuellen Werte verloren gehen."
        val supportsVaneLR = Codec.modelHasVaneLR(state.modelNrRaw) == true
        val snapshot = ClimateProfile(
            id = "__individual_profile_snapshot__",
            deviceId = deviceId,
            name = "Individuell",
            power = state.power,
            mode = state.mode?.name,
            targetTemp = state.presetTemp,
            fanSpeed = state.airFlow,
            verticalSwing = state.windUD,
            horizontalSwing = state.windLR.takeIf { supportsVaneLR },
            threeDAuto = state.entrust.takeIf { supportsVaneLR },
            updatedAt = System.currentTimeMillis()
        )
        val saved = runCatching {
            if (syncDeviceKey.isBlank()) syncStore.saveIndividualProfile(deviceId, snapshot)
            else syncStore.saveIndividualProfileWithSync(deviceId, snapshot, syncDeviceKey)
        }.getOrElse {
            return it.message ?: "Die individuellen Einstellungen konnten nicht lokal gesichert werden. Der Profilwechsel wurde abgebrochen."
        }
        if (!saved) return "Die individuellen Einstellungen konnten nicht zusammen mit ihrer Synchronisierungsvormerkung gespeichert werden."
        if (syncDeviceKey.isNotBlank()) syncRevision++
        return null
    }
    LaunchedEffect(repository, PeerSyncSignals.dataRevision) {
        if (PeerSyncSignals.dataRevision > 0) selectedProfileId = syncStore.activeProfileId(deviceId)
        runCatching { repository.load() }
            .onSuccess { profiles.clear(); profiles.addAll(it); loadSucceeded = true; loaded = true }
            .onFailure { loadSucceeded = false; status = it.message ?: "Klimaprofile konnten nicht geladen werden."; loaded = true }
    }

    val deviceProfiles = profiles.filter { it.deviceId == deviceId }
    val selectedProfile = deviceProfiles.firstOrNull { it.id == selectedProfileId }
    val modelSupportsVaneLR = currentState?.let { Codec.modelHasVaneLR(it.modelNrRaw) }
    val selectedProfileDirty = selectedProfile != null && currentState != null && (
        (selectedProfile.power != null && selectedProfile.power != currentState.power) ||
        (selectedProfile.mode != null && selectedProfile.mode != currentState.mode?.name) ||
        (selectedProfile.targetTemp != null && abs(selectedProfile.targetTemp - currentState.presetTemp) >= 0.01) ||
        (selectedProfile.fanSpeed != null && selectedProfile.fanSpeed != currentState.airFlow) ||
        (selectedProfile.verticalSwing != null && selectedProfile.verticalSwing != currentState.windUD) ||
        (modelSupportsVaneLR == true && selectedProfile.horizontalSwing != null && selectedProfile.horizontalSwing != currentState.windLR) ||
        (modelSupportsVaneLR == true && selectedProfile.threeDAuto != null && selectedProfile.threeDAuto != currentState.entrust)
    )
    val showDirty = selectedProfileDirty && !switching

    fun updateSelectedProfileFromState() {
        val profile = selectedProfile ?: return
        val state = currentState ?: return
        val updated = profile.copy(
            power = state.power, mode = state.mode?.name,
            targetTemp = state.presetTemp, fanSpeed = state.airFlow,
            verticalSwing = state.windUD,
            horizontalSwing = state.windLR.takeIf { modelSupportsVaneLR == true },
            threeDAuto = state.entrust.takeIf { modelSupportsVaneLR == true },
            updatedAt = System.currentTimeMillis()
        )
        runCatching {
            if (syncDeviceKey.isBlank()) repository.save(updated)
            else repository.saveWithPendingSync(updated, syncDeviceKey)
        }.onSuccess {
            val index = profiles.indexOfFirst { it.id == updated.id }
            if (index >= 0) profiles[index] = updated
            status = "Änderungen in „${updated.name}“ übernommen."
            syncRevision++
        }.onFailure { status = it.message ?: "Profil konnte nicht aktualisiert werden." }
    }

    // "Individuell" keeps whatever is set on the unit: every change is saved right away.
    LaunchedEffect(deviceId, currentState, selectedProfileId, switching, loaded) {
        if (managerMode || !loaded || switching || selectedProfileId != null || deviceId.isBlank()) return@LaunchedEffect
        val state = currentState ?: return@LaunchedEffect
        val stored = runCatching { syncStore.individualProfile(deviceId) }.getOrNull()
        val vaneLR = Codec.modelHasVaneLR(state.modelNrRaw) == true
        val unchanged = stored != null &&
            stored.power == state.power && stored.mode == state.mode?.name &&
            stored.targetTemp != null && abs(stored.targetTemp - state.presetTemp) < 0.01 &&
            stored.fanSpeed == state.airFlow && stored.verticalSwing == state.windUD &&
            stored.horizontalSwing == state.windLR.takeIf { vaneLR } &&
            stored.threeDAuto == state.entrust.takeIf { vaneLR }
        if (!unchanged) captureIndividualSettings()?.let { status = it }
    }

    fun switchTo(profile: ClimateProfile, profileId: String?, failure: String) {
        val previous = selectedProfileId
        selectedProfileId = profileId
        switching = true
        status = ""
        onApply(profile) { success, message ->
            if (success) {
                status = persistActiveProfile(profileId) ?: ""
            } else {
                selectedProfileId = previous
                status = message ?: failure
            }
            switching = false
        }
    }

    val deviceSyncSignature = devices.joinToString("|") { it.id + "=" + it.syncDeviceKey }
    // A single failed connection to ioBroker is retried a minute later anyway; only a repeated
    // failure is worth showing.
    var syncFailures by remember { mutableIntStateOf(0) }
    fun syncStatus(message: String): String {
        val failed = message.contains("fehlgeschlagen", ignoreCase = true)
        syncFailures = if (failed) syncFailures + 1 else 0
        return if (failed && syncFailures < 2) "" else message
    }
    LaunchedEffect(loaded, loadSucceeded, deviceSyncSignature, syncRevision) {
        if (!loaded || !loadSucceeded) return@LaunchedEffect
        runCatching { repository.flushPendingSyncJournal(syncStore) }
            .onFailure { status = it.message ?: "Profiländerungsjournal konnte nicht übernommen werden." }
        devices.filter { it.syncDeviceKey.isNotBlank() }.forEach { device ->
            runCatching { syncStore.enqueueInitialProfiles(profiles.filter { it.deviceId == device.id }, device.syncDeviceKey) }
                .onFailure { status = it.message ?: "Vorhandene Profile konnten nicht zur Synchronisierung vorgemerkt werden." }
        }
        val address = settingsPrefs.getString("iobroker_server", null).orEmpty()
        if (devices.none { it.syncDeviceKey.isNotBlank() } || address.isBlank()) return@LaunchedEffect
        syncClient.syncNow(address, devices, syncStore, repository) { message ->
            status = syncStatus(message)
            runCatching { repository.load() }.onSuccess { latest -> profiles.clear(); profiles.addAll(latest) }
            selectedProfileId = syncStore.activeProfileId(deviceId)
        }
    }
    LaunchedEffect(loaded, loadSucceeded, deviceSyncSignature) {
        if (!loaded || !loadSucceeded || devices.none { it.syncDeviceKey.isNotBlank() }) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            runCatching { repository.flushPendingSyncJournal(syncStore) }
                .onFailure { status = it.message ?: "Profiländerungsjournal konnte nicht übernommen werden." }
            val address = settingsPrefs.getString("iobroker_server", null).orEmpty()
            if (address.isNotBlank()) syncClient.syncNow(address, devices, syncStore, repository) { message ->
                status = syncStatus(message)
                runCatching { repository.load() }.onSuccess { latest -> profiles.clear(); profiles.addAll(latest) }
                selectedProfileId = syncStore.activeProfileId(deviceId)
            }
        }
    }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3EEF7))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (deviceId.isBlank()) Text("Wähle zuerst eine Klimaanlage aus.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            else {
                if (!managerMode) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(Modifier.weight(1f)) {
                            Surface(onClick = { profileMenuExpanded = true }, shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFE3EEF7), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(selectedProfile?.let { profileSymbol(it) } ?: "⚙️", fontSize = 17.sp)
                                    Spacer(Modifier.width(7.dp))
                                    Text(selectedProfile?.name ?: "Individuell", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                                    if (showDirty) Text("  (geändert)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    Spacer(Modifier.weight(1f))
                                    Text("⌄", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            DropdownMenu(expanded = profileMenuExpanded, onDismissRequest = { profileMenuExpanded = false }) {
                                DropdownMenuItem(text = { Text("⚙️  Individuell") },
                                    onClick = {
                                        profileMenuExpanded = false
                                        val snapshot = runCatching { syncStore.individualProfile(deviceId) }.getOrNull()
                                        if (snapshot == null) {
                                            status = persistActiveProfile(null) ?: "Noch keine individuellen Einstellungen gespeichert. Die aktuellen Geräteeinstellungen bleiben unverändert."
                                        } else {
                                            switchTo(snapshot, null, "Individuelle Einstellungen konnten nicht wiederhergestellt werden.")
                                        }
                                    })
                                deviceProfiles.forEach { profile ->
                                    DropdownMenuItem(text = { Text(profileSymbol(profile) + "  " + profile.name) },
                                        onClick = {
                                            profileMenuExpanded = false
                                            val captureError = if (selectedProfileId == null) captureIndividualSettings() else null
                                            if (captureError != null) {
                                                status = captureError
                                            } else {
                                                switchTo(profile, profile.id, "Profil konnte nicht angewendet werden.")
                                            }
                                        })
                                }
                            }
                        }
                        if (showDirty) {
                            IconButton(onClick = { updateSelectedProfileFromState() }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.Check, contentDescription = "Änderungen in dieses Profil übernehmen", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = { newProfileName = ""; saveNameDialog = true },
                            enabled = currentState != null, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Neues Profil anlegen", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
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
            // Only problems are shown; routine sync and save confirmations stay out of the way.
            val failed = status.isNotBlank() && listOf("fehlgeschlagen", "konnte nicht", "ungültig", "abgebrochen", "beschädigt", "nicht erreichbar", "fehler")
                .any { status.contains(it, ignoreCase = true) }
            if (failed) {
                Text(status, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
    }

    val profileToEdit = editing
    if (profileToEdit != null) {
        ClimateProfileEditorDialog(
            deviceId = deviceId, profile = profileToEdit,
            initialState = currentState,
            // Unknown model (e.g. in the profile manager without live state) counts as supported,
            // like in the schedule editor; applying a profile checks the real model anyway.
            modelSupportsVaneLR = modelSupportsVaneLR != false,
            onDismiss = { editing = null },
            onSave = { profile ->
                runCatching {
                    if (syncDeviceKey.isBlank()) repository.save(profile)
                    else repository.saveWithPendingSync(profile, syncDeviceKey)
                }
                    .onSuccess {
                        val index = profiles.indexOfFirst { it.id == profile.id }
                        if (index >= 0) profiles[index] = profile else profiles.add(profile)
                        status = if (syncDeviceKey.isBlank()) {
                            "Lokal gespeichert. Für die Synchronisierung auf anderen Smartphones fehlt noch die gemeinsame Gerätekennung."
                        } else {
                            "Profil lokal gespeichert und dauerhaft zur Synchronisierung vorgemerkt."
                        }
                        syncRevision++
                        editing = null
                    }
                    .onFailure { status = it.message ?: "Profil konnte nicht gespeichert werden." }
            }
        )
    }


    if (saveNameDialog && currentState != null) {
        KlimaPilotAlertDialog(
            onDismissRequest = { saveNameDialog = false },
            title = { Text("Neues Klimaprofil") },
            text = {
                OutlinedTextField(
                    value = newProfileName, onValueChange = { newProfileName = it },
                    label = { Text("Profilname") }, placeholder = { Text("z. B. Nachtmodus") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(enabled = newProfileName.isNotBlank(), onClick = {
                    val created = ClimateProfile(
                        id = UUID.randomUUID().toString(), deviceId = deviceId, name = newProfileName.trim(),
                        power = currentState.power, mode = currentState.mode?.name,
                        targetTemp = currentState.presetTemp, fanSpeed = currentState.airFlow,
                        verticalSwing = currentState.windUD,
                        horizontalSwing = currentState.windLR.takeIf { modelSupportsVaneLR == true },
                        threeDAuto = currentState.entrust.takeIf { modelSupportsVaneLR == true },
                        updatedAt = System.currentTimeMillis()
                    )
                    runCatching {
                        if (syncDeviceKey.isBlank()) repository.save(created)
                        else repository.saveWithPendingSync(created, syncDeviceKey)
                    }.onSuccess {
                        profiles.add(created)
                        val selectionError = persistActiveProfile(created.id, requestSync = false)
                        status = selectionError ?: if (syncDeviceKey.isBlank()) "" else
                            "Profil lokal gespeichert und dauerhaft zur Synchronisierung vorgemerkt."
                        syncRevision++
                        saveNameDialog = false
                    }.onFailure { status = it.message ?: "Profil konnte nicht gespeichert werden." }
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { saveNameDialog = false }) { Text("Abbrechen") } }
        )
    }

    deleting?.let { profile ->
        KlimaPilotAlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Profil löschen?") },
            text = { Text("„${profile.name}“ wird von diesem Smartphone gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        if (syncDeviceKey.isBlank()) repository.delete(profile.id)
                        else repository.deleteWithPendingSync(profile.id, syncDeviceKey)
                    }
                        .onSuccess {
                            profiles.removeAll { it.id == profile.id }
                            val selectionError = if (selectedProfileId == profile.id) persistActiveProfile(null, requestSync = false) else null
                            syncRevision++
                            status = selectionError ?: if (syncDeviceKey.isBlank())
                                "Lokal gelöscht. Ohne gemeinsame Gerätekennung kann die Löschung nicht auf andere Smartphones übertragen werden."
                            else "Profil gelöscht und Löschung dauerhaft zur Synchronisierung vorgemerkt."
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
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Zurück", tint = MaterialTheme.colorScheme.onBackground) }
            Text("Klimaprofile verwalten", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            devices.forEach { device ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(device.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    ClimateProfilesSection(deviceId = device.id, syncDeviceKey = device.syncDeviceKey, devices = devices, managerMode = true, onApply = { _, _ -> })
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
    modelSupportsVaneLR: Boolean?,
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
    var includeHorizontal by remember(profile?.id) { mutableStateOf(modelSupportsVaneLR == true && (profile?.horizontalSwing != null || initialState != null)) }
    var horizontalSwing by remember(profile?.id) { mutableIntStateOf(profile?.horizontalSwing ?: initialState?.windLR ?: 0) }
    var include3d by remember(profile?.id) { mutableStateOf(modelSupportsVaneLR == true && (profile?.threeDAuto != null || initialState != null)) }
    var threeDAuto by remember(profile?.id) { mutableStateOf(profile?.threeDAuto ?: initialState?.entrust ?: false) }

    val temp = tempText.trim().replace(',', '.').toDoubleOrNull()
    val tempValid = !includeTemp || (temp != null && temp.isFinite() && temp in 16.0..30.0 &&
        abs(temp * 2.0 - (temp * 2.0).roundToInt()) < 0.001)
    val preserveUnknownAdvanced = modelSupportsVaneLR == null &&
        (profile?.horizontalSwing != null || profile?.threeDAuto != null)
    val anySetting = includePower || includeMode || includeTemp || includeFan || includeVertical ||
        includeHorizontal || include3d || preserveUnknownAdvanced
    val modes = listOf("AUTO" to "Automatik", "COOL" to "Kühlen", "HEAT" to "Heizen", "DRY" to "Entfeuchten", "FAN" to "Lüfter")
    val fanOptions = listOf("Automatik", "Leise", "Stufe 1", "Stufe 2", "Stufe 3")
    val verticalOptions = listOf("Automatik", "Ganz oben", "Mitte oben", "Mitte unten", "Ganz unten")
    val horizontalOptions = listOf("Automatik", "Links außen", "Links", "Mitte links", "Mitte", "Mitte rechts", "Rechts", "Rechts außen")

    KlimaPilotAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) "Neues Klimaprofil" else "Klimaprofil bearbeiten") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Profilname") },
                    placeholder = { Text("z. B. Nachtmodus") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                HorizontalDivider()
                Text("Was das Profil einstellt", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
                if (modelSupportsVaneLR == true) {
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
                }
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
                    horizontalSwing = when (modelSupportsVaneLR) {
                        true -> if (includeHorizontal) horizontalSwing else null
                        false -> null
                        null -> profile?.horizontalSwing
                    },
                    threeDAuto = when (modelSupportsVaneLR) {
                        true -> if (include3d) threeDAuto else null
                        false -> null
                        null -> profile?.threeDAuto
                    },
                    updatedAt = System.currentTimeMillis()
                ))
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
