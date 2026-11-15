package de.klimapilot.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Durable local outbox. Events are retained until an explicit upload acknowledgement. */
internal class ClimateProfileSyncStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun installationId(): String {
        val root = readRoot()
        root.optString(KEY_INSTALLATION_ID).takeIf { it.isNotBlank() }?.let { return it }
        val created = UUID.randomUUID().toString()
        root.put(KEY_INSTALLATION_ID, created)
        check(prefs.edit().putString(KEY_STATE, root.toString()).commit()) { "Installationskennung konnte nicht gespeichert werden." }
        return created
    }

    @Synchronized
    fun enqueueUpsert(profile: ClimateProfile, deviceKey: String): Boolean {
        require(deviceKey.isNotBlank()) { "Für die Synchronisierung fehlt die gemeinsame Gerätekennung." }
        return appendEvent("upsert", profile.id, deviceKey, profile.toSyncJson())
    }

    @Synchronized
    fun enqueueDelete(profileId: String, deviceKey: String): Boolean {
        require(profileId.isNotBlank())
        require(deviceKey.isNotBlank()) { "Für die Synchronisierung fehlt die gemeinsame Gerätekennung." }
        return appendEvent("delete", profileId, deviceKey, null)
    }

    @Synchronized
    fun enqueueInitialProfiles(profiles: List<ClimateProfile>, deviceKey: String): Boolean {
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
    }

    @Synchronized
    fun pendingEvents(): List<JSONObject> {
        val outbox = readRoot().optJSONArray(KEY_OUTBOX) ?: return emptyList()
        return (0 until outbox.length()).mapNotNull { outbox.optJSONObject(it)?.let { event -> JSONObject(event.toString()) } }
    }

    @Synchronized
    fun acknowledgeUploaded(eventIds: Set<String>): Boolean {
        if (eventIds.isEmpty()) return true
        val root = readRoot()
        val current = root.optJSONArray(KEY_OUTBOX) ?: JSONArray()
        val remaining = JSONArray()
        for (index in 0 until current.length()) {
            val event = current.optJSONObject(index) ?: continue
            if (event.optString("eventId") !in eventIds) remaining.put(event)
        }
        root.put(KEY_OUTBOX, remaining)
        return prefs.edit().putString(KEY_STATE, root.toString()).commit()
    }

    @Synchronized
    fun isRemoteEventNewer(event: JSONObject): Boolean {
        val key = versionKey(event.optString("deviceKey"), event.optString("profileId"))
        val current = readRoot().optJSONObject(KEY_PROFILE_VERSIONS)?.optJSONObject(key) ?: return true
        return compareVersion(event, current) > 0
    }

    @Synchronized
    fun recordRemoteEvent(event: JSONObject): Boolean {
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
    }

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

    @Synchronized
    fun observeRemoteClock(remoteClock: Long) {
        if (remoteClock <= 0L) return
        val root = readRoot()
        val current = root.optLong(KEY_LOGICAL_CLOCK, 0L).coerceAtLeast(0L)
        if (remoteClock > current) {
            root.put(KEY_LOGICAL_CLOCK, remoteClock)
            check(prefs.edit().putString(KEY_STATE, root.toString()).commit()) { "Synchronisierungszähler konnte nicht gespeichert werden." }
        }
    }

    private fun appendEvent(type: String, profileId: String, deviceKey: String, profile: JSONObject?): Boolean {
        val root = readRoot()
        val installation = root.optString(KEY_INSTALLATION_ID).takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val sequence = root.optLong(KEY_SEQUENCE, 0L).coerceAtLeast(0L) + 1L
        val logicalClock = root.optLong(KEY_LOGICAL_CLOCK, 0L).coerceAtLeast(0L) + 1L
        val outbox = root.optJSONArray(KEY_OUTBOX) ?: JSONArray()
        val event = JSONObject().put("eventId", "$installation:$sequence").put("installationId", installation)
            .put("sequence", sequence).put("logicalClock", logicalClock).put("type", type)
            .put("deviceKey", deviceKey).put("profileId", profileId).put("createdAt", System.currentTimeMillis())
        if (profile != null) event.put("profile", profile)
        outbox.put(event)
        root.put(KEY_INSTALLATION_ID, installation).put(KEY_SEQUENCE, sequence)
            .put(KEY_LOGICAL_CLOCK, logicalClock).put(KEY_OUTBOX, outbox)
        val versions = root.optJSONObject(KEY_PROFILE_VERSIONS) ?: JSONObject()
        versions.put(versionKey(deviceKey, profileId), versionOf(event))
        root.put(KEY_PROFILE_VERSIONS, versions)
        return prefs.edit().putString(KEY_STATE, root.toString()).commit()
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
        private const val KEY_INITIALIZED = "initializedDeviceKeys"
        private const val KEY_PROFILE_VERSIONS = "profileVersions"
    }
}
