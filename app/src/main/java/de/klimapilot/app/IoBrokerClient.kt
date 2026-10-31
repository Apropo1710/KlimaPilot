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
                client.emit("authenticate", IoBrokerAck { args ->
                    val authenticated = args.firstOrNull() as? Boolean ?: false
                    if (!authenticated) {
                        mainHandler.post {
                            if (!reported) { reported = true; callback(false, "ioBroker hat die WebSocket-Verbindung nicht authentifiziert.") }
                        }
                    } else {
                        connected = true
                        client.emit("subscribe", "*")
                        client.emit("subscribeObjects", "*")
                        client.emit("getState", "system.adapter.admin.0.alive", IoBrokerAck { stateArgs ->
                            val error = stateArgs.firstOrNull()
                            val state = stateArgs.getOrNull(1) as? JSONObject
                            val alive = state?.opt("val") as? Boolean
                            mainHandler.post {
                                if (!reported) {
                                    reported = true
                                    if (error == null && state != null && alive == true) callback(true, "WebSocket-Verbindung zu ioBroker ws.0 hergestellt.")
                                    else callback(true, "WebSocket-Verbindung zu ioBroker ws.0 hergestellt.")
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

    fun isConnected(): Boolean = connected && socket?.connected() == true
}
