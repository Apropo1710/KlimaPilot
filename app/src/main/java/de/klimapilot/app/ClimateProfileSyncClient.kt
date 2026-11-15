package de.klimapilot.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Uses its own native ioBroker WebSocket and never touches the main app connection. */
internal class ClimateProfileSyncClient {
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)

    fun syncNow(
        address: String,
        devices: List<UserDevice>,
        store: ClimateProfileSyncStore,
        repository: ClimateProfileRepository,
        callback: (String) -> Unit
    ) {
        if (!running.compareAndSet(false, true)) { callback("Synchronisierung läuft bereits."); return }
        val url = address.trim().trimEnd('/')
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            running.set(false)
            callback("Keine gültige ioBroker-WebSocket-Adresse hinterlegt.")
            return
        }
        val socket = IoBrokerSocket(url, "name=klimapilot.profile-sync")
        val completed = AtomicBoolean(false)
        fun finish(message: String) {
            if (!completed.compareAndSet(false, true)) return
            socket.off()
            socket.disconnect()
            running.set(false)
            main.post { callback(message) }
        }
        socket.on("connect") {
            socket.emit("authenticate", IoBrokerAck { authArgs ->
                val authenticated = authArgs.firstOrNull() as? Boolean ?: false
                if (!authenticated) { finish("ioBroker hat die Synchronisierungsverbindung nicht authentifiziert."); return@IoBrokerAck }
                socket.emit("getState", "system.adapter.admin.0.alive", IoBrokerAck { aliveArgs ->
                    val error = aliveArgs.firstOrNull()?.takeUnless { it == JSONObject.NULL }
                    val state = aliveArgs.getOrNull(1) as? JSONObject
                    if (error != null || state?.optBoolean("val", false) != true) {
                        finish("ioBroker ist nicht als aktiv bestätigt; Synchronisierung wurde abgebrochen.")
                        return@IoBrokerAck
                    }
                    uploadPending(socket, store) { uploaded, uploadError ->
                        if (uploadError != null) { finish(uploadError); return@uploadPending }
                        readRemoteEvents(socket) { events, readError ->
                            if (readError != null) { finish(readError); return@readRemoteEvents }
                            val deviceMap = devices.filter { it.syncDeviceKey.isNotBlank() }
                                .groupBy { it.syncDeviceKey }.filterValues { it.size == 1 }
                                .mapValues { it.value.single().id }
                            var applied = 0
                            var skipped = 0
                            val sorted = events.sortedWith(compareBy<JSONObject> { it.optLong("logicalClock", 0L) }
                                .thenBy { it.optString("installationId") }.thenBy { it.optLong("sequence", 0L) })
                            for (event in sorted) {
                                val key = event.optString("deviceKey")
                                val deviceId = deviceMap[key]
                                if (deviceId == null || !store.isRemoteEventNewer(event)) { skipped++; continue }
                                try {
                                    repository.applyRemoteEvent(event, deviceId)
                                    check(store.recordRemoteEvent(event)) { "Versionsstand konnte nicht lokal gespeichert werden." }
                                    applied++
                                } catch (error: Exception) {
                                    finish("Ein empfangenes Profil konnte nicht sicher übernommen werden: ${error.message ?: "Ungültige Daten"}.")
                                    return@readRemoteEvents
                                }
                            }
                            val suffix = if (skipped > 0) " $skipped Ereignisse waren bereits bekannt oder keinem eindeutig zugeordneten Gerät zuweisbar." else ""
                            finish("Synchronisierung abgeschlossen: $uploaded Änderungen übertragen, $applied Änderungen übernommen.$suffix")
                        }
                    }
                })
            })
        }
        socket.on("connect_error") { args -> finish("ioBroker-Synchronisierung fehlgeschlagen: ${args.firstOrNull() ?: "Verbindung fehlgeschlagen"}") }
        socket.on("disconnect") { if (!completed.get()) finish("ioBroker-Verbindung während der Synchronisierung getrennt.") }
        socket.connect()
    }

    private fun uploadPending(socket: IoBrokerSocket, store: ClimateProfileSyncStore, done: (Int, String?) -> Unit) {
        val events = store.pendingEvents()
        if (events.isEmpty()) { done(0, null); return }
        var index = 0
        var uploaded = 0
        val acknowledged = linkedSetOf<String>()
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
            socket.emit("setObject", id, definition, IoBrokerAck { objectArgs ->
                val objectError = objectArgs.firstOrNull()?.takeUnless { it == JSONObject.NULL }
                if (objectError != null) { done(uploaded, "Synchronisierungsereignis konnte in ioBroker nicht angelegt werden: $objectError"); return@IoBrokerAck }
                socket.emit("setState", id, JSONObject().put("val", event.toString()).put("ack", false), IoBrokerAck { stateArgs ->
                    val stateError = stateArgs.firstOrNull()?.takeUnless { it == JSONObject.NULL }
                    if (stateError != null) { done(uploaded, "Synchronisierungsereignis konnte nicht übertragen werden: $stateError"); return@IoBrokerAck }
                    acknowledged.add(event.optString("eventId"))
                    uploaded++
                    next()
                })
            })
        }
        next()
    }

    private fun readRemoteEvents(socket: IoBrokerSocket, done: (List<JSONObject>, String?) -> Unit) {
        socket.emit("getStates", "$ROOT_ID.devices.*", IoBrokerAck { args ->
            val error = args.firstOrNull()?.takeUnless { it == JSONObject.NULL }
            if (error != null) { done(emptyList(), "Synchronisierungsdaten konnten aus ioBroker nicht gelesen werden: $error"); return@IoBrokerAck }
            val raw = args.getOrNull(1)
            val states = when (raw) {
                is JSONObject -> raw
                is Map<*, *> -> JSONObject().apply { raw.forEach { (key, value) -> if (key is String) put(key, value) } }
                else -> null
            }
            if (states == null) { done(emptyList(), "ioBroker lieferte keine lesbare Synchronisierungsübersicht."); return@IoBrokerAck }
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
