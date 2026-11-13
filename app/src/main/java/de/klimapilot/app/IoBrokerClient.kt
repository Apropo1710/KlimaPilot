package de.klimapilot.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject

internal object IoBrokerClient {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var socket: IoBrokerSocket? = null
    @Volatile private var connected = false

    fun disconnect() {
        connected = false
        socket?.off()
        socket?.disconnect()
        socket = null
    }

    fun connect(url: String, callback: (Boolean, String) -> Unit) {
        disconnect()
        val address = url.trim().trimEnd('/')
        try {
            require(address.startsWith("ws://") || address.startsWith("wss://")) {
                "WebSocket-Adresse muss mit ws:// oder wss:// beginnen."
            }
            val client = IoBrokerSocket(address, "name=klimapilot.android")
            socket = client
            var reported = false
            client.on("connect") {
                reported = false
                client.emit("authenticate", IoBrokerAck { args ->
                    val authenticated = args.firstOrNull() as? Boolean ?: false
                    if (!authenticated) {
                        mainHandler.post {
                            if (!reported) { reported = true; callback(false, "ioBroker hat die WebSocket-Verbindung nicht authentifiziert.") }
                        }
                    } else {
                        client.emit("subscribe", "*")
                        client.emit("subscribeObjects", "*")
                        client.emit("getState", "system.adapter.admin.0.alive", IoBrokerAck { stateArgs ->
                            val error = stateArgs.firstOrNull()
                            val state = stateArgs.getOrNull(1) as? JSONObject
                            val alive = state?.opt("val") as? Boolean
                            val diagnostic = when {
                                error != null -> "WebSocket steht, aber der ioBroker-Test konnte nicht gelesen werden: $error"
                                state == null -> "WebSocket steht, aber ioBroker hat auf den Test-Datenpunkt system.adapter.admin.0.alive nicht geantwortet."
                                alive != true -> "WebSocket steht, aber der Admin-Adapter meldet sich nicht als aktiv."
                                else -> null
                            }
                            mainHandler.post {
                                if (!reported) {
                                    reported = true
                                    connected = diagnostic == null
                                    callback(diagnostic == null, diagnostic ?: "WebSocket-Verbindung zu ioBroker ws.0 hergestellt.")
                                }
                            }
                        })
                    }
                })
            }
            client.on("disconnect") { connected = false }
            client.on("connect_error") { args ->
                connected = false
                val message = args.firstOrNull()?.toString() ?: "Verbindung fehlgeschlagen"
                mainHandler.post { if (!reported) { reported = true; callback(false, "WebSocket: $message") } }
            }
            client.connect()
        } catch (error: Exception) {
            connected = false
            callback(false, error.message ?: error.javaClass.simpleName)
        }
    }


    private fun requireSocket(): IoBrokerSocket =
        socket?.takeIf { connected && it.connected() }
            ?: throw IllegalStateException("Keine WebSocket-Verbindung zu ioBroker")

    fun getObject(id: String, callback: (JSONObject?, String?) -> Unit) {
        val client = try { requireSocket() } catch (e: Exception) {
            mainHandler.post { callback(null, e.message ?: "Nicht verbunden") }
            return
        }
        client.emit("getObject", id, IoBrokerAck { args ->
            val error = args.firstOrNull()?.takeUnless { it == JSONObject.NULL }
            val raw = args.getOrNull(1)
            val obj = when (raw) {
                is JSONObject -> raw
                is String -> try { JSONObject(raw) } catch (_: Exception) { null }
                is Map<*, *> -> JSONObject().apply {
                    raw.forEach { (key, value) -> if (key is String) put(key, value) }
                }
                else -> null
            }
            mainHandler.post { callback(obj, error?.toString()) }
        })
    }

    fun setObject(id: String, definition: JSONObject, callback: (Boolean, String?) -> Unit) {
        val client = try { requireSocket() } catch (e: Exception) {
            mainHandler.post { callback(false, e.message ?: "Nicht verbunden") }
            return
        }
        client.emit("setObject", id, definition, IoBrokerAck { args ->
            val error = args.firstOrNull()?.takeUnless { it == JSONObject.NULL }
            mainHandler.post { callback(error == null, error?.toString()) }
        })
    }

    fun getJsonState(id: String, callback: (JSONObject?, String?) -> Unit) {
        val client = try { requireSocket() } catch (e: Exception) {
            mainHandler.post { callback(null, e.message ?: "Nicht verbunden") }
            return
        }
        client.emit("getState", id, IoBrokerAck { args ->
            val error = args.firstOrNull()?.takeUnless { it == JSONObject.NULL }
            val state = args.getOrNull(1) as? JSONObject
            val raw = state?.opt("val")
            val json = when (raw) {
                is JSONObject -> raw
                is String -> try { JSONObject(raw) } catch (_: Exception) { null }
                else -> null
            }
            val resultError = when {
                error != null -> error.toString()
                state == null -> "Datenpunkt $id existiert noch nicht oder liefert keinen Zustand"
                json == null -> "Datenpunkt $id enthält kein gültiges JSON-Objekt"
                else -> null
            }
            mainHandler.post { callback(json, resultError) }
        })
    }

    fun setJsonState(id: String, value: JSONObject, callback: (Boolean, String?) -> Unit) {
        val client = try { requireSocket() } catch (e: Exception) {
            mainHandler.post { callback(false, e.message ?: "Nicht verbunden") }
            return
        }
        client.emit("setState", id, JSONObject().put("val", value.toString()).put("ack", false), IoBrokerAck { args ->
            val error = args.firstOrNull()?.takeUnless { it == JSONObject.NULL }
            mainHandler.post { callback(error == null, error?.toString()) }
        })
    }

    fun ensureJsonState(
        id: String,
        displayName: String,
        initialValue: JSONObject,
        callback: (JSONObject?, Boolean, String?) -> Unit
    ) {
        getObject(id) { existingObject, objectError ->
            if (objectError != null) {
                callback(null, false, "Objekt $id konnte nicht gelesen werden: $objectError")
                return@getObject
            }
            if (existingObject != null) {
                getJsonState(id) { json, stateError ->
                    if (stateError != null || json == null) {
                        callback(null, false, "Vorhandenes Objekt $id hat keinen lesbaren JSON-Zustand. Keine automatische Initialisierung. $stateError")
                    } else callback(json, false, null)
                }
                return@getObject
            }
            val definition = JSONObject()
                .put("type", "state")
                .put("common", JSONObject()
                    .put("name", displayName)
                    .put("type", "string")
                    .put("role", "json")
                    .put("read", true)
                    .put("write", true))
                .put("native", JSONObject())
            setObject(id, definition) { created, createError ->
                if (!created) {
                    callback(null, false, "Objekt $id konnte nicht angelegt werden: $createError")
                    return@setObject
                }
                setJsonState(id, initialValue) { written, writeError ->
                    if (!written) callback(null, false, "Erstzustand für $id konnte nicht geschrieben werden: $writeError")
                    else callback(initialValue, true, null)
                }
            }
        }
    }

    fun isConnected(): Boolean = connected && socket?.connected() == true
}
