package de.klimapilot.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Uses its own native ioBroker WebSocket and never touches the main app connection. */
internal class InvalidRemoteProfileEventException(message: String, cause: Throwable) : RuntimeException(message, cause)

/** [peerLog] collects every event seen here so paired phones receive them as well. */
internal class ClimateProfileSyncClient(private val peerLog: ClimatePeerEventLog? = null) {
    private val main = Handler(Looper.getMainLooper())

    fun syncNow(
        address: String,
        devices: List<UserDevice>,
        store: ClimateProfileSyncStore,
        repository: ClimateProfileRepository,
        callback: (String) -> Unit
    ) {
        if (!ClimateProfileSyncCoordinator.tryAcquire()) { callback("Synchronisierung läuft bereits."); return }
        val url = address.trim().trimEnd('/')
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            ClimateProfileSyncCoordinator.release()
            callback("Keine gültige ioBroker-WebSocket-Adresse hinterlegt.")
            return
        }
        val socket = IoBrokerLease(url)
        val completed = AtomicBoolean(false)
        fun finish(message: String) {
            if (!completed.compareAndSet(false, true)) return
            try {
                runCatching { socket.off() }
                runCatching { socket.disconnect() }
            } finally {
                ClimateProfileSyncCoordinator.release()
                main.post { callback(message) }
            }
        }
        socket.on("connect") {
            socket.emit("authenticate", IoBrokerAck authCallback@{ authArgs ->
                IoBrokerMessages.authError(authArgs)?.let { finish(it); return@authCallback }
                socket.emit("getState", "system.adapter.admin.0.alive", IoBrokerAck aliveCallback@{ aliveArgs ->
                    val error = aliveArgs.firstOrNull()?.takeUnless { it == JSONObject.NULL }
                    val state = aliveArgs.getOrNull(1) as? JSONObject
                    if (error != null || state?.optBoolean("val", false) != true) {
                        finish("ioBroker ist nicht als aktiv bestätigt; Synchronisierung wurde abgebrochen.")
                        return@aliveCallback
                    }
                    runCatching { peerLog?.remember(store.pendingEvents()) }
                    uploadPending(socket, store) { uploaded, uploadError ->
                        if (uploadError != null) { finish(uploadError); return@uploadPending }
                        readRemoteEvents(socket) { events, readError ->
                            try {
                                if (readError != null) { finish(readError); return@readRemoteEvents }
                                runCatching { peerLog?.remember(events) }
                                store.observeRemoteClock(events.maxOfOrNull { it.optLong("logicalClock", 0L) } ?: 0L)
                                val deviceMap = devices.filter { it.syncDeviceKey.isNotBlank() }
                                    .groupBy { it.syncDeviceKey }.filterValues { it.size == 1 }
                                    .mapValues { it.value.single().id }
                                val summary = applyRemoteEvents(events, deviceMap, store, repository)
                                val suffix = buildList {
                                    if (summary.skipped > 0) add("${summary.skipped} Ereignisse waren bereits bekannt oder keinem eindeutig zugeordneten Gerät zuweisbar.")
                                    if (summary.rejected > 0) add("${summary.rejected} ungültige Remote-Ereignisse wurden einzeln abgelehnt; erstes Problem: ${summary.firstRejectedReason ?: "Validierungsfehler"}.")
                                }.joinToString(" ")
                                finish("Synchronisierung abgeschlossen: $uploaded Änderungen übertragen, ${summary.applied} Änderungen übernommen." + if (suffix.isBlank()) "" else " $suffix")
                            } catch (error: Exception) {
                                finish("Synchronisierung abgebrochen: ${error.message ?: error.javaClass.simpleName}")
                            }
                        }
                    }
                })
            })
        }
        socket.on("connect_error") { args -> finish("ioBroker-Synchronisierung fehlgeschlagen: ${args.firstOrNull() ?: "Verbindung fehlgeschlagen"}") }
        socket.on("disconnect") { if (!completed.get()) finish("ioBroker-Verbindung während der Synchronisierung getrennt.") }
        socket.connect()
    }

    internal data class RemoteApplySummary(
        val applied: Int,
        val skipped: Int,
        val rejected: Int,
        val firstRejectedReason: String?
    )

    private val syncEngine = ClimateProfileSyncEngine()

    internal fun applyRemoteEvents(
        events: List<JSONObject>,
        deviceMap: Map<String, String>,
        store: ClimateProfileSyncStore,
        repository: ClimateProfileRepository
    ): RemoteApplySummary = syncEngine.applyRemoteEvents(events, deviceMap, store, repository)

    internal fun applyRemoteEventIfNewer(
        event: JSONObject,
        deviceId: String,
        store: ClimateProfileSyncStore,
        repository: ClimateProfileRepository
    ): Boolean = syncEngine.applyRemoteEventIfNewer(event, deviceId, store, repository)

    private fun uploadPending(socket: IoBrokerLease, store: ClimateProfileSyncStore, done: (Int, String?) -> Unit) {
        val events = store.pendingEvents()
        if (events.isEmpty()) { done(0, null); return }
        var index = 0
        var uploaded = 0
        val acknowledged = mutableListOf<JSONObject>()
        fun next() {
            if (index >= events.size) {
                if (!store.acknowledgeUploaded(acknowledged)) done(uploaded, "Übertragene Änderungen konnten lokal nicht bestätigt werden; sie bleiben zur sicheren Wiederholung vorgemerkt.")
                else done(uploaded, null)
                return
            }
            val event = events[index++]
            val installation = event.optString("installationId")
            val sequence = event.optLong("sequence", -1L)
            if (installation.isBlank() || sequence < 1L) { done(uploaded, "Eine lokale Synchronisierungsänderung ist ungültig und bleibt zur Prüfung erhalten."); return }
            val id = "$ROOT_ID.devices.$installation.$sequence"
            val definition = JSONObject().put("type", "state")
                .put("common", JSONObject().put("name", "KlimaPilot profile sync event").put("type", "string").put("role", "json").put("read", true).put("write", true))
                .put("native", JSONObject())
            socket.emit("setObject", id, definition, IoBrokerAck objectCallback@{ objectArgs ->
                val objectError = objectArgs.firstOrNull()?.takeUnless { it == JSONObject.NULL }
                if (objectError != null) { done(uploaded, "Synchronisierungsereignis konnte in ioBroker nicht angelegt werden: $objectError"); return@objectCallback }
                socket.emit("setState", id, JSONObject().put("val", event.toString()).put("ack", false), IoBrokerAck stateCallback@{ stateArgs ->
                    val stateError = stateArgs.firstOrNull()?.takeUnless { it == JSONObject.NULL }
                    if (stateError != null) { done(uploaded, "Synchronisierungsereignis konnte nicht übertragen werden: $stateError"); return@stateCallback }
                    acknowledged.add(JSONObject(event.toString()))
                    uploaded++
                    next()
                })
            })
        }
        next()
    }

    private fun readRemoteEvents(socket: IoBrokerLease, done: (List<JSONObject>, String?) -> Unit) {
        socket.emit("getStates", "$ROOT_ID.devices.*", IoBrokerAck statesCallback@{ args ->
            val error = args.firstOrNull()?.takeUnless { it == JSONObject.NULL }
            if (error != null) { done(emptyList(), "Synchronisierungsdaten konnten aus ioBroker nicht gelesen werden: $error"); return@statesCallback }
            val raw = args.getOrNull(1)
            val states = when (raw) {
                is JSONObject -> raw
                is Map<*, *> -> JSONObject().apply { raw.forEach { (key, value) -> if (key is String) put(key, value) } }
                else -> null
            }
            if (states == null) { done(emptyList(), "ioBroker lieferte keine lesbare Synchronisierungsübersicht."); return@statesCallback }
            val events = mutableListOf<JSONObject>()
            val iterator = states.keys()
            while (iterator.hasNext()) {
                val id = iterator.next()
                val state = states.optJSONObject(id) ?: continue
                val value = state.opt("val")
                val event = when (value) {
                    is JSONObject -> value
                    is String -> runCatching { JSONObject(value) }.getOrNull()
                    is Map<*, *> -> JSONObject().apply { value.forEach { (key, item) -> if (key is String) put(key, item) } }
                    else -> null
                } ?: continue
                if (event.optString("eventId").isNotBlank() && event.optString("deviceKey").isNotBlank()) events.add(event)
            }
            done(events, null)
        })
    }

    companion object {
        private const val ROOT_ID = "0_userdata.0.KlimaPilot.profileSync"
    }
}
