package de.klimapilot.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

/** Durable local outbox. Events are retained until an explicit upload acknowledgement. */
internal class ClimateProfileSyncStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    
    fun installationId(): String {
        synchronized(ClimateProfileStorageLock.monitor) {
        val root = readRoot()
        root.optString(KEY_INSTALLATION_ID).takeIf { it.isNotBlank() }?.let { return it }
        val created = UUID.randomUUID().toString()
        root.put(KEY_INSTALLATION_ID, created)
        check(prefs.edit().putString(KEY_STATE, root.toString()).commit()) { "Installationskennung konnte nicht gespeichert werden." }
        return created
    
        }}

    
    fun enqueueUpsert(profile: ClimateProfile, deviceKey: String): Boolean {
        synchronized(ClimateProfileStorageLock.monitor) {
        require(deviceKey.isNotBlank()) { "Für die Synchronisierung fehlt die gemeinsame Gerätekennung." }
        return appendEvent("upsert", profile.id, deviceKey, profile.toSyncJson())
    
        }}

    
    fun enqueueDelete(profileId: String, deviceKey: String): Boolean {
        synchronized(ClimateProfileStorageLock.monitor) {
        require(profileId.isNotBlank())
        require(deviceKey.isNotBlank()) { "Für die Synchronisierung fehlt die gemeinsame Gerätekennung." }
        return appendEvent("delete", profileId, deviceKey, null)
    
        }}

    
    fun activeProfileId(deviceId: String): String? {
        synchronized(ClimateProfileStorageLock.monitor) {
        require(deviceId.isNotBlank())
        return readRoot().optJSONObject(KEY_ACTIVE_PROFILES)?.optString(deviceId)?.takeIf { it.isNotBlank() }
    
        }}

    
    fun saveActiveProfileId(deviceId: String, profileId: String?): Boolean {
        synchronized(ClimateProfileStorageLock.monitor) {
        require(deviceId.isNotBlank())
        val root = readRoot()
        val active = root.optJSONObject(KEY_ACTIVE_PROFILES) ?: JSONObject()
        active.put(deviceId, profileId ?: "")
        root.put(KEY_ACTIVE_PROFILES, active)
        return prefs.edit().putString(KEY_STATE, root.toString()).commit()
    
        }}

    
    fun saveActiveProfileIdWithSync(deviceId: String, profileId: String?, deviceKey: String): Boolean {
        synchronized(ClimateProfileStorageLock.monitor) {
        require(deviceId.isNotBlank() && deviceKey.isNotBlank())
        val root = readRoot()
        val active = root.optJSONObject(KEY_ACTIVE_PROFILES) ?: JSONObject()
        active.put(deviceId, profileId ?: "")
        root.put(KEY_ACTIVE_PROFILES, active)
        val payload = JSONObject().put("activeProfileId", profileId ?: JSONObject.NULL)
        appendEventToRoot(root, "selection", ACTIVE_SELECTION_EVENT_ID, deviceKey, payload)
        return prefs.edit().putString(KEY_STATE, root.toString()).commit()
    
        }}

    
    fun enqueueActiveProfileSelection(profileId: String?, deviceKey: String): Boolean {
        synchronized(ClimateProfileStorageLock.monitor) {
        require(deviceKey.isNotBlank()) { "Für die Synchronisierung fehlt die gemeinsame Gerätekennung." }
        val payload = JSONObject().put("activeProfileId", profileId ?: JSONObject.NULL)
        return appendEvent("selection", ACTIVE_SELECTION_EVENT_ID, deviceKey, payload)
    
        }}

    
    fun individualProfile(deviceId: String): ClimateProfile? {
        synchronized(ClimateProfileStorageLock.monitor) {
        require(deviceId.isNotBlank())
        val item = readRoot().optJSONObject(KEY_INDIVIDUAL_PROFILES)?.optJSONObject(deviceId) ?: return null
        return profileFromJson(deviceId, item)
    
        }}

    
    fun saveIndividualProfile(deviceId: String, profile: ClimateProfile): Boolean {
        synchronized(ClimateProfileStorageLock.monitor) {
        require(deviceId.isNotBlank() && profile.deviceId == deviceId)
        val root = readRoot()
        val snapshots = root.optJSONObject(KEY_INDIVIDUAL_PROFILES) ?: JSONObject()
        snapshots.put(deviceId, profile.toSyncJson())
        root.put(KEY_INDIVIDUAL_PROFILES, snapshots)
        return prefs.edit().putString(KEY_STATE, root.toString()).commit()
    
        }}

    
    fun saveIndividualProfileWithSync(deviceId: String, profile: ClimateProfile, deviceKey: String): Boolean {
        synchronized(ClimateProfileStorageLock.monitor) {
        require(deviceId.isNotBlank() && profile.deviceId == deviceId && deviceKey.isNotBlank())
        val root = readRoot()
        val snapshots = root.optJSONObject(KEY_INDIVIDUAL_PROFILES) ?: JSONObject()
        snapshots.put(deviceId, profile.toSyncJson())
        root.put(KEY_INDIVIDUAL_PROFILES, snapshots)
        appendEventToRoot(root, "individual", INDIVIDUAL_PROFILE_EVENT_ID, deviceKey, profile.toSyncJson())
        return prefs.edit().putString(KEY_STATE, root.toString()).commit()
    
        }}

    
    fun enqueueIndividualProfile(profile: ClimateProfile, deviceKey: String): Boolean {
        synchronized(ClimateProfileStorageLock.monitor) {
        require(deviceKey.isNotBlank()) { "Für die Synchronisierung fehlt die gemeinsame Gerätekennung." }
        return appendEvent("individual", INDIVIDUAL_PROFILE_EVENT_ID, deviceKey, profile.toSyncJson())
    
        }}

    
    fun applyRemoteIndividualProfile(deviceId: String, payload: JSONObject): Boolean {
        synchronized(ClimateProfileStorageLock.monitor) {
        val profile = profileFromJson(deviceId, payload)
        return saveIndividualProfile(deviceId, profile)
    
        }}

    private fun profileFromJson(deviceId: String, item: JSONObject): ClimateProfile {
        val mode = if (item.isNull("mode")) null else item.optString("mode").also {
            require(it in setOf("AUTO", "COOL", "HEAT", "DRY", "FAN")) { "Individuelle Einstellungen enthalten einen ungültigen Modus." }
        }
        val temperature = if (item.isNull("targetTemp")) null else item.optDouble("targetTemp").also {
            require(it.isFinite() && it in 16.0..30.0 && kotlin.math.abs(it * 2.0 - (it * 2.0).roundToInt()) < 0.001) {
                "Individuelle Einstellungen enthalten eine ungültige Solltemperatur."
            }
        }
        fun optionalIndex(key: String, max: Int): Int? =
            if (!item.has(key) || item.isNull(key)) null else item.optInt(key).also {
                require(it in 0..max) { "Individuelle Einstellungen enthalten einen ungültigen Wert." }
            }
        return ClimateProfile(
            id = INDIVIDUAL_PROFILE_ID,
            deviceId = deviceId,
            name = "Individuell",
            power = if (!item.has("power") || item.isNull("power")) null else item.optBoolean("power"),
            mode = mode,
            targetTemp = temperature,
            fanSpeed = optionalIndex("fanSpeed", 4),
            verticalSwing = optionalIndex("verticalSwing", 4),
            horizontalSwing = optionalIndex("horizontalSwing", 7),
            threeDAuto = if (!item.has("threeDAuto") || item.isNull("threeDAuto")) null else item.optBoolean("threeDAuto"),
            updatedAt = item.optLong("updatedAt", 0L).coerceAtLeast(0L)
        )
    }

    
    fun enqueueInitialProfiles(profiles: List<ClimateProfile>, deviceKey: String): Boolean {
        synchronized(ClimateProfileStorageLock.monitor) {
        if (deviceKey.isBlank()) return false
        val root = readRoot()
        if (root.optJSONArray(KEY_INITIALIZED)?.let { array -> (0 until array.length()).any { array.optString(it) == deviceKey } } == true) return true
        val outbox = root.optJSONArray(KEY_OUTBOX) ?: JSONArray()
        var sequence = root.optLong(KEY_SEQUENCE, 0L).coerceAtLeast(0L)
        var logicalClock = root.optLong(KEY_LOGICAL_CLOCK, 0L).coerceAtLeast(0L)
        val installation = root.optString(KEY_INSTALLATION_ID).takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        profiles.forEach { profile ->
            sequence += 1L
            logicalClock += 1L
            outbox.put(JSONObject().put("eventId", "$installation:$sequence").put("installationId", installation)
                .put("sequence", sequence).put("logicalClock", logicalClock).put("type", "upsert")
                .put("deviceKey", deviceKey).put("profileId", profile.id).put("profile", profile.toSyncJson())
                .put("createdAt", System.currentTimeMillis()))
        }
        val initialized = root.optJSONArray(KEY_INITIALIZED) ?: JSONArray()
        initialized.put(deviceKey)
        val versions = root.optJSONObject(KEY_PROFILE_VERSIONS) ?: JSONObject()
        for (index in 0 until outbox.length()) {
            val event = outbox.optJSONObject(index) ?: continue
            if (event.optString("deviceKey") == deviceKey && event.optString("installationId") == installation) {
                versions.put(versionKey(deviceKey, event.optString("profileId")), versionOf(event))
            }
        }
        root.put(KEY_INSTALLATION_ID, installation).put(KEY_SEQUENCE, sequence)
            .put(KEY_LOGICAL_CLOCK, logicalClock).put(KEY_OUTBOX, outbox)
            .put(KEY_INITIALIZED, initialized).put(KEY_PROFILE_VERSIONS, versions)
        check(prefs.edit().putString(KEY_STATE, root.toString()).commit()) { "Initialprofile konnten nicht zur Synchronisierung vorgemerkt werden." }
        return true
    
        }}

    /**
     * Reassigns still-pending local events when an automatically generated WF-RAC key changes.
     * Already acknowledged events stay associated with their original physical module.
     */
    
    fun rekeyPendingEvents(oldDeviceKey: String, newDeviceKey: String): Boolean {
        synchronized(ClimateProfileStorageLock.monitor) {
        require(oldDeviceKey.isNotBlank() && newDeviceKey.isNotBlank() && oldDeviceKey != newDeviceKey)
        val root = readRoot()
        val current = root.optJSONArray(KEY_OUTBOX) ?: JSONArray()
        val updated = JSONArray()
        val versions = root.optJSONObject(KEY_PROFILE_VERSIONS) ?: JSONObject()
        for (index in 0 until current.length()) {
            val event = current.optJSONObject(index) ?: continue
            if (event.optString("deviceKey") == oldDeviceKey) {
                event.put("deviceKey", newDeviceKey)
                val profileId = event.optString("profileId")
                if (profileId.isNotBlank()) {
                    val key = versionKey(newDeviceKey, profileId)
                    val previous = versions.optJSONObject(key)
                    if (previous == null || compareVersion(event, previous) > 0) {
                        versions.put(key, versionOf(event))
                    }
                }
            }
            updated.put(event)
        }
        root.put(KEY_OUTBOX, updated).put(KEY_PROFILE_VERSIONS, versions)
        return prefs.edit().putString(KEY_STATE, root.toString()).commit()
    
        }}

    
    fun pendingEvents(): List<JSONObject> {
        synchronized(ClimateProfileStorageLock.monitor) {
        val outbox = readRoot().optJSONArray(KEY_OUTBOX) ?: return emptyList()
        return (0 until outbox.length()).mapNotNull { outbox.optJSONObject(it)?.let { event -> JSONObject(event.toString()) } }
    
        }}

    
    fun acknowledgeUploaded(uploadedEvents: List<JSONObject>): Boolean {
        synchronized(ClimateProfileStorageLock.monitor) {
            if (uploadedEvents.isEmpty()) return true
            val uploadedById = uploadedEvents.associateBy { it.optString("eventId") }
            val root = readRoot()
            val current = root.optJSONArray(KEY_OUTBOX) ?: JSONArray()
            val remaining = JSONArray()
            for (index in 0 until current.length()) {
                val event = current.optJSONObject(index) ?: continue
                val uploaded = uploadedById[event.optString("eventId")]
                val sameRevision = uploaded != null &&
                    uploaded.optString("deviceKey") == event.optString("deviceKey") &&
                    uploaded.optString("installationId") == event.optString("installationId") &&
                    uploaded.optLong("sequence", -1L) == event.optLong("sequence", -1L)
                if (!sameRevision) remaining.put(event)
            }
            root.put(KEY_OUTBOX, remaining)
            return prefs.edit().putString(KEY_STATE, root.toString()).commit()
        }
    }

    
    fun isRemoteEventNewer(event: JSONObject): Boolean {
        synchronized(ClimateProfileStorageLock.monitor) {
        val key = versionKey(event.optString("deviceKey"), event.optString("profileId"))
        val current = readRoot().optJSONObject(KEY_PROFILE_VERSIONS)?.optJSONObject(key) ?: return true
        return compareVersion(event, current) > 0
    
        }}

    
    fun recordRemoteEvent(event: JSONObject): Boolean {
        synchronized(ClimateProfileStorageLock.monitor) {
        val key = versionKey(event.optString("deviceKey"), event.optString("profileId"))
        val root = readRoot()
        val versions = root.optJSONObject(KEY_PROFILE_VERSIONS) ?: JSONObject()
        val current = versions.optJSONObject(key)
        if (current != null && compareVersion(event, current) <= 0) return true
        versions.put(key, versionOf(event))
        root.put(KEY_PROFILE_VERSIONS, versions)
        val clock = event.optLong("logicalClock", 0L)
        if (clock > root.optLong(KEY_LOGICAL_CLOCK, 0L)) root.put(KEY_LOGICAL_CLOCK, clock)
        return prefs.edit().putString(KEY_STATE, root.toString()).commit()
    
        }}

    private fun compareVersion(left: JSONObject, right: JSONObject): Int {
        val clock = left.optLong("logicalClock", 0L).compareTo(right.optLong("logicalClock", 0L))
        if (clock != 0) return clock
        val installation = left.optString("installationId").compareTo(right.optString("installationId"))
        if (installation != 0) return installation
        return left.optLong("sequence", 0L).compareTo(right.optLong("sequence", 0L))
    }

    private fun versionOf(event: JSONObject): JSONObject = JSONObject()
        .put("logicalClock", event.optLong("logicalClock", 0L))
        .put("installationId", event.optString("installationId"))
        .put("sequence", event.optLong("sequence", 0L))

    private fun versionKey(deviceKey: String, profileId: String): String =
        deviceKey + "|" + profileId

    
    fun observeRemoteClock(remoteClock: Long) {
        synchronized(ClimateProfileStorageLock.monitor) {
        if (remoteClock <= 0L) return
        val root = readRoot()
        val current = root.optLong(KEY_LOGICAL_CLOCK, 0L).coerceAtLeast(0L)
        if (remoteClock > current) {
            root.put(KEY_LOGICAL_CLOCK, remoteClock)
            check(prefs.edit().putString(KEY_STATE, root.toString()).commit()) { "Synchronisierungszähler konnte nicht gespeichert werden." }
        }
    
        }}

    /** Removes import markers only after the repository journal no longer contains those IDs. */
    
    fun retainImportedJournalIds(pendingJournalIds: Set<String>): Boolean {
        synchronized(ClimateProfileStorageLock.monitor) {
        val root = readRoot()
        val current = root.optJSONArray(KEY_IMPORTED_JOURNAL_IDS) ?: return true
        val retained = JSONArray()
        for (index in 0 until current.length()) {
            val eventId = current.optString(index)
            if (eventId.isNotBlank() && eventId in pendingJournalIds) retained.put(eventId)
        }
        if (retained.length() == current.length()) return true
        root.put(KEY_IMPORTED_JOURNAL_IDS, retained)
        return prefs.edit().putString(KEY_STATE, root.toString()).commit()
    
        }}

    
    fun enqueueJournalEvent(journalEvent: JSONObject): Boolean {
        synchronized(ClimateProfileStorageLock.monitor) {
        val eventId = journalEvent.optString("eventId").takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Journal-Ereignis ohne stabile ID.")
        val type = journalEvent.optString("type")
        require(type == "upsert" || type == "delete") { "Unbekannter Journal-Ereignistyp." }
        val profileId = journalEvent.optString("profileId").takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Journal-Ereignis ohne Profil-ID.")
        val deviceKey = journalEvent.optString("deviceKey").takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Journal-Ereignis ohne Gerätekennung.")
        val profile = journalEvent.optJSONObject("profile")?.let { JSONObject(it.toString()) }
        if (type == "upsert" && profile == null) throw IllegalArgumentException("Journal-Upsert ohne Profilinhalt.")
        return appendEvent(type, profileId, deviceKey, profile, eventId)
    
        }}

    private fun appendEvent(type: String, profileId: String, deviceKey: String, profile: JSONObject?, stableEventId: String? = null): Boolean {
        val root = readRoot()
        if (!appendEventToRoot(root, type, profileId, deviceKey, profile, stableEventId)) return true
        return prefs.edit().putString(KEY_STATE, root.toString()).commit()
    }

    /** Adds an event to an already-mutated root so local data and its outbox entry commit together. */
    private fun appendEventToRoot(
        root: JSONObject,
        type: String,
        profileId: String,
        deviceKey: String,
        profile: JSONObject?,
        stableEventId: String? = null
    ): Boolean {
        val outbox = root.optJSONArray(KEY_OUTBOX) ?: JSONArray()
        if (stableEventId != null) {
            val imported = root.optJSONArray(KEY_IMPORTED_JOURNAL_IDS) ?: JSONArray()
            if ((0 until imported.length()).any { imported.optString(it) == stableEventId } ||
                (0 until outbox.length()).any { outbox.optJSONObject(it)?.optString("eventId") == stableEventId }) return false
        }
        val installation = root.optString(KEY_INSTALLATION_ID).takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val sequence = root.optLong(KEY_SEQUENCE, 0L).coerceAtLeast(0L) + 1L
        val logicalClock = root.optLong(KEY_LOGICAL_CLOCK, 0L).coerceAtLeast(0L) + 1L
        val event = JSONObject().put("eventId", stableEventId ?: "$installation:$sequence").put("installationId", installation)
            .put("sequence", sequence).put("logicalClock", logicalClock).put("type", type)
            .put("deviceKey", deviceKey).put("profileId", profileId).put("createdAt", System.currentTimeMillis())
        if (profile != null) event.put("profile", profile)
        outbox.put(event)
        root.put(KEY_INSTALLATION_ID, installation).put(KEY_SEQUENCE, sequence)
            .put(KEY_LOGICAL_CLOCK, logicalClock).put(KEY_OUTBOX, outbox)
        if (stableEventId != null) {
            val imported = root.optJSONArray(KEY_IMPORTED_JOURNAL_IDS) ?: JSONArray()
            imported.put(stableEventId)
            root.put(KEY_IMPORTED_JOURNAL_IDS, imported)
        }
        val versions = root.optJSONObject(KEY_PROFILE_VERSIONS) ?: JSONObject()
        versions.put(versionKey(deviceKey, profileId), versionOf(event))
        root.put(KEY_PROFILE_VERSIONS, versions)
        return true
    }

    private fun readRoot(): JSONObject {
        val raw = prefs.getString(KEY_STATE, null) ?: return JSONObject()
        return try { JSONObject(raw) } catch (_: Exception) {
            throw IllegalStateException("Die lokale Synchronisierungswarteschlange ist beschädigt. Sie wurde nicht überschrieben.")
        }
    }

    private fun ClimateProfile.toSyncJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("power", power ?: JSONObject.NULL)
        .put("mode", mode ?: JSONObject.NULL).put("targetTemp", targetTemp ?: JSONObject.NULL)
        .put("fanSpeed", fanSpeed ?: JSONObject.NULL).put("verticalSwing", verticalSwing ?: JSONObject.NULL)
        .put("horizontalSwing", horizontalSwing ?: JSONObject.NULL).put("threeDAuto", threeDAuto ?: JSONObject.NULL)
        .put("updatedAt", updatedAt)

    companion object {
        private const val PREFS_NAME = "klimapilot_profile_sync"
        private const val KEY_STATE = "sync_state_v1"
        private const val KEY_INSTALLATION_ID = "installationId"
        private const val KEY_SEQUENCE = "sequence"
        private const val KEY_LOGICAL_CLOCK = "logicalClock"
        private const val KEY_OUTBOX = "outbox"
        private const val KEY_IMPORTED_JOURNAL_IDS = "importedJournalEventIds"
        private const val KEY_INITIALIZED = "initializedDeviceKeys"
        private const val KEY_PROFILE_VERSIONS = "profileVersions"
        private const val KEY_ACTIVE_PROFILES = "activeProfiles"
        private const val ACTIVE_SELECTION_EVENT_ID = "__active_profile_selection__"
        private const val KEY_INDIVIDUAL_PROFILES = "individualProfiles"
        private const val INDIVIDUAL_PROFILE_ID = "__individual_profile_snapshot__"
        private const val INDIVIDUAL_PROFILE_EVENT_ID = "__individual_profile_snapshot__"
    }
}
