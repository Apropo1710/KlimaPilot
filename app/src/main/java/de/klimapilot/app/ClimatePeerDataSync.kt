package de.klimapilot.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.klimapilot.app.sync.ClimatePeerSessionHandler
import de.klimapilot.app.wfrac.Scheme
import de.klimapilot.app.wfrac.WfRacIdentityStore
import de.klimapilot.app.wfrac.WfRacStoredIdentity
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.Socket
import java.security.MessageDigest

/** UI signals for data received from paired phones. */
internal object PeerSyncSignals {
    /** Incremented on the main thread whenever peer data changed local accounts or profiles. */
    var dataRevision by mutableIntStateOf(0)
    /** Incremented after a new pairing so the automatic peer sync restarts immediately. */
    var pairingRevision by mutableIntStateOf(0)
    var lastStatus by mutableStateOf("")
}

/**
 * Keeps the newest known profile event per (deviceKey, profileId), including events that came
 * from ioBroker or another phone, so they can be forwarded to paired phones. Ordering uses the
 * same version rule as [ClimateProfileSyncStore] (logical clock, installation id, sequence).
 */
internal class ClimatePeerEventLog(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("klimapilot_peer_event_log", Context.MODE_PRIVATE)

    fun all(): List<JSONObject> = synchronized(LOCK) {
        val root = readRoot()
        root.keys().asSequence().mapNotNull { root.optJSONObject(it)?.let { e -> JSONObject(e.toString()) } }.toList()
    }

    fun remember(events: List<JSONObject>): Boolean = synchronized(LOCK) {
        if (events.isEmpty()) return true
        val root = readRoot()
        var changed = false
        for (event in events) {
            if (!isPlausible(event)) continue
            val key = keyOf(event)
            val current = root.optJSONObject(key)
            if (current == null || compareVersion(event, current) > 0) {
                root.put(key, JSONObject(event.toString()))
                changed = true
            }
        }
        if (!changed) return true
        prefs.edit().putString(KEY, root.toString()).commit()
    }

    private fun readRoot(): JSONObject =
        prefs.getString(KEY, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()

    companion object {
        private const val KEY = "latest_events_v1"
        private val LOCK = Any()

        fun keyOf(event: JSONObject) = event.optString("deviceKey") + "|" + event.optString("profileId")

        fun isPlausible(event: JSONObject): Boolean =
            event.optString("eventId").isNotBlank() && event.optString("deviceKey").isNotBlank() &&
                event.optString("profileId").isNotBlank() && event.optString("installationId").isNotBlank() &&
                event.optLong("sequence", 0L) > 0L && event.optString("type") in ALLOWED_TYPES

        fun compareVersion(left: JSONObject, right: JSONObject): Int {
            val clock = left.optLong("logicalClock", 0L).compareTo(right.optLong("logicalClock", 0L))
            if (clock != 0) return clock
            val installation = left.optString("installationId").compareTo(right.optString("installationId"))
            if (installation != 0) return installation
            return left.optLong("sequence", 0L).compareTo(right.optLong("sequence", 0L))
        }

        /** Newest event per profile key across all given lists. */
        fun newestPerKey(events: List<JSONObject>): List<JSONObject> {
            val newest = LinkedHashMap<String, JSONObject>()
            for (event in events) {
                if (!isPlausible(event)) continue
                val key = keyOf(event)
                val current = newest[key]
                if (current == null || compareVersion(event, current) > 0) newest[key] = event
            }
            return newest.values.toList()
        }

        private val ALLOWED_TYPES = setOf("upsert", "delete", "selection", "individual")
    }
}

/**
 * Exchanges accounts and climate-profile events with a paired phone over an already mutually
 * authenticated socket. Both sides send one snapshot; received data is merged locally:
 * profile events through [ClimateProfileSyncEngine] (same rules as ioBroker), the account
 * registry by revision (deterministic tie-break so both phones converge).
 */
internal class ClimatePeerDataSync(context: Context) : ClimatePeerSessionHandler {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onAuthenticated(socket: Socket, asServer: Boolean): String {
        socket.soTimeout = IO_TIMEOUT_MS
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        val remote: JSONObject
        if (asServer) {
            remote = readFrame(input)
            writeFrame(output, buildSnapshot())
        } else {
            writeFrame(output, buildSnapshot())
            remote = readFrame(input)
        }
        val result = applySnapshot(remote)
        val message = "Mit gekoppeltem Gerät abgeglichen: " +
            (if (result.accountsChanged) "Konten aktualisiert, " else "") +
            (if (result.configChanged) "Räume und Geräte aktualisiert, " else "") +
            "${result.profileEventsApplied} Profiländerungen übernommen."
        mainHandler.post {
            PeerSyncSignals.lastStatus = message
            if (result.accountsChanged || result.configChanged || result.profileEventsApplied > 0) PeerSyncSignals.dataRevision++
        }
        return message
    }

    data class ApplyResult(val accountsChanged: Boolean, val profileEventsApplied: Int, val configChanged: Boolean = false)

    internal fun buildSnapshot(): JSONObject = synchronized(APPLY_LOCK) {
        val snapshot = JSONObject().put("v", PROTOCOL_VERSION)
        val accounts = KlimaPilotAccountRepository(appContext).load()
        if (accounts is KlimaPilotAccountLoadResult.Loaded && accounts.registry.accounts.isNotEmpty()) {
            snapshot.put("accounts", accounts.registry.toJson())
        }
        val settings = settings()
        val configUpdatedAt = settings.getLong(KEY_CONFIG_UPDATED_AT, 0L)
        if (configUpdatedAt > 0L) {
            val identities = runCatching { WfRacIdentityStore(appContext).all() }.getOrDefault(emptyList())
            snapshot.put("config", JSONObject()
                .put("updatedAt", configUpdatedAt)
                .put("rooms", JSONArray(settings.getStringSet("rooms", emptySet()).orEmpty().sorted()))
                .put("devices", runCatching { JSONArray(settings.getString("devices", "[]")) }.getOrDefault(JSONArray()))
                .put("wfRac", JSONArray().apply {
                    identities.forEach { put(JSONObject().put("airconId", it.airconId).put("host", it.host)
                        .put("scheme", it.scheme.name).put("certificatePin", it.certificatePin ?: JSONObject.NULL)) }
                }))
        }
        IoBrokerAddress.saved(settings)?.let { snapshot.put("iobroker", it) }
        ScheduleIoBroker.peerJson(appContext)?.let { snapshot.put("schedules", it) }
        val store = ClimateProfileSyncStore(appContext)
        val repository = ClimateProfileRepository(appContext)
        val log = ClimatePeerEventLog(appContext)
        runCatching { synchronized(ClimateProfileStorageLock.monitor) { repository.flushPendingSyncJournal(store) } }
        val pending = runCatching { store.pendingEvents() }.getOrDefault(emptyList())
        log.remember(pending)
        val events = ClimatePeerEventLog.newestPerKey(log.all() + pending)
        snapshot.put("events", JSONArray().apply { events.forEach { put(it) } })
        snapshot
    }

    internal fun applySnapshot(remote: JSONObject): ApplyResult = synchronized(APPLY_LOCK) {
        require(remote.optInt("v", -1) == PROTOCOL_VERSION) { "Inkompatible KlimaPilot-Version auf dem anderen Gerät." }
        var accountsChanged = false
        remote.optJSONObject("accounts")?.let { KlimaPilotAccountRegistry.fromJson(it) }?.let { registry ->
            accountsChanged = KlimaPilotAccountRepository(appContext).mergeFromPeer(registry)
        }
        val schedulesChanged = remote.optJSONObject("schedules")?.let { ScheduleIoBroker.applyPeer(appContext, it) } ?: false
        val configChanged = (remote.optJSONObject("config")?.let { applyConfig(it) } ?: false) || schedulesChanged
        remote.optString("iobroker").takeIf { it.isNotBlank() }?.let { url ->
            val settings = settings()
            if (IoBrokerAddress.saved(settings) == null) IoBrokerAddress.normalize(url)?.let { IoBrokerAddress.store(settings, it) }
        }
        val array = remote.optJSONArray("events") ?: JSONArray()
        require(array.length() <= MAX_EVENTS) { "Zu viele Profilereignisse vom anderen Gerät." }
        val events = (0 until array.length()).mapNotNull { array.optJSONObject(it) }.filter { ClimatePeerEventLog.isPlausible(it) }
        if (events.isEmpty()) return@synchronized ApplyResult(accountsChanged, 0, configChanged)
        val store = ClimateProfileSyncStore(appContext)
        val repository = ClimateProfileRepository(appContext)
        ClimatePeerEventLog(appContext).remember(events)
        store.observeRemoteClock(events.maxOf { it.optLong("logicalClock", 0L) })
        val summary = ClimateProfileSyncEngine().applyRemoteEvents(events, deviceMap(), store, repository)
        ApplyResult(accountsChanged, summary.applied, configChanged)
    }

    private fun settings() = appContext.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE)

    /**
     * Rooms, devices and WF-RAC module identities are maintained by the administrator.
     * The newer configuration (by change time) replaces the local one, so a non-admin phone
     * gets the air conditioners without configuring them itself. Each phone still registers
     * itself at the module on its first command.
     */
    private fun applyConfig(config: JSONObject): Boolean {
        val settings = settings()
        val remoteUpdatedAt = config.optLong("updatedAt", 0L)
        if (remoteUpdatedAt <= settings.getLong(KEY_CONFIG_UPDATED_AT, 0L)) return false
        val roomsArray = config.optJSONArray("rooms") ?: return false
        val rooms = (0 until roomsArray.length()).map { roomsArray.optString(it).trim() }.filter { it.isNotBlank() }.toSet()
        val devices = config.optJSONArray("devices") ?: return false
        val validDevices = (0 until devices.length()).all { index ->
            val item = devices.optJSONObject(index)
            item != null && item.optString("id").isNotBlank() && item.optString("name").isNotBlank() && item.optString("room").isNotBlank()
        }
        if (rooms.isEmpty() || !validDevices) return false
        val identities = WfRacIdentityStore(appContext)
        val wfRac = config.optJSONArray("wfRac") ?: JSONArray()
        for (index in 0 until wfRac.length()) {
            val item = wfRac.optJSONObject(index) ?: continue
            val identity = runCatching {
                WfRacStoredIdentity(
                    airconId = item.getString("airconId"),
                    host = item.getString("host"),
                    scheme = Scheme.valueOf(item.getString("scheme")),
                    certificatePin = item.optString("certificatePin").takeIf { !item.isNull("certificatePin") && it.isNotBlank() },
                    lastVerifiedAt = 0L,
                )
            }.getOrNull() ?: continue
            if (identity.scheme == Scheme.HTTPS && identity.certificatePin == null) continue
            runCatching {
                val known = identities.findByAirconId(identity.airconId)
                if (known == null || known.host != identity.host) identities.save(identity)
            }
        }
        return settings.edit()
            .putStringSet("rooms", rooms)
            .putString("devices", devices.toString())
            .putLong(KEY_CONFIG_UPDATED_AT, remoteUpdatedAt)
            .commit()
    }

    /** syncDeviceKey -> local device id, only for keys that are unique on this phone. */
    private fun deviceMap(): Map<String, String> {
        val raw = appContext.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE).getString("devices", null)
            ?: return emptyMap()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyMap()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
            .map { it.optString("syncDeviceKey").trim() to it.optString("id") }
            .filter { it.first.isNotBlank() && it.second.isNotBlank() }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }
    }

    private fun writeFrame(output: DataOutputStream, json: JSONObject) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_FRAME_BYTES) { "Die lokalen Daten sind für die Übertragung zu groß." }
        output.writeInt(MAGIC)
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }

    private fun readFrame(input: DataInputStream): JSONObject {
        require(input.readInt() == MAGIC) { "Ungültige Daten vom gekoppelten Gerät." }
        val length = input.readInt()
        require(length in 2..MAX_FRAME_BYTES) { "Ungültige Datenmenge vom gekoppelten Gerät." }
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(bytes, offset, length - offset)
            if (count < 0) throw EOFException("Verbindung während des Abgleichs beendet.")
            offset += count
        }
        return JSONObject(String(bytes, Charsets.UTF_8))
    }

    companion object {
        private const val PROTOCOL_VERSION = 1
        internal const val KEY_CONFIG_UPDATED_AT = "config_updated_at"
        private const val MAGIC = 0x4B505344 // "KPSD"
        private const val MAX_FRAME_BYTES = 4 * 1024 * 1024
        private const val MAX_EVENTS = 20_000
        private const val IO_TIMEOUT_MS = 20_000
        private val APPLY_LOCK = Any()

        /** Deterministic order for two different registries with the same revision. */
        internal fun registryDigest(registry: KlimaPilotAccountRegistry): String =
            MessageDigest.getInstance("SHA-256").digest(registry.toJson().toString().toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
