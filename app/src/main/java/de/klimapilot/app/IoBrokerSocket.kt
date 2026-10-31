package de.klimapilot.app

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class IoBrokerAck(val callback: (Array<Any?>) -> Unit)

internal class IoBrokerSocket(
    private val address: String,
    private val query: String = ""
) {
    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(0, TimeUnit.SECONDS)
        .build()
    private val ids = AtomicInteger(0)
    private val callbacks = ConcurrentHashMap<Int, (Array<Any?>) -> Unit>()
    private val callbackTimeouts = ConcurrentHashMap<Int, Runnable>()
    private val callbackTimeoutMs = 12_000L
    private val handlers = ConcurrentHashMap<String, MutableList<(Array<Any?>) -> Unit>>()
    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var isConnected = false
    @Volatile private var stopped = false
    @Volatile private var readyReceived = false
    @Volatile private var lastPong = System.currentTimeMillis()
    private var reconnectDelay = 1000L
    private val pingTask = object : Runnable {
        override fun run() {
            if (stopped) return
            val ws = webSocket
            if (ws != null && isConnected) {
                if (System.currentTimeMillis() - lastPong > 60_000L) {
                    ws.close(1001, "Pong timeout")
                    onLost("Zeitüberschreitung bei der WebSocket-Verbindung")
                    return
                }
                ws.send(JSONArray().put(1).toString())
            }
            main.postDelayed(this, 5000L)
        }
    }

    fun on(event: String, handler: (Array<Any?>) -> Unit) {
        handlers.compute(event) { _, old ->
            (old ?: mutableListOf()).also { it.add(handler) }
        }
    }

    fun off() {
        handlers.clear()
    }

    fun connect() {
        stopped = false
        openSocket()
    }

    private fun openSocket() {
        if (stopped) return
        readyReceived = false
        val separator = if (address.contains("?")) "&" else "?"
        val sid = System.currentTimeMillis()
        val fullUrl = address + separator + "sid=" + sid +
            (if (query.isNotBlank()) "&" + query.trimStart('&', '?') else "")
        val request = Request.Builder().url(fullUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                lastPong = System.currentTimeMillis()
                reconnectDelay = 1000L
                main.removeCallbacks(pingTask)
                main.postDelayed(pingTask, 5000L)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                lastPong = System.currentTimeMillis()
                val frame = try { JSONArray(text) } catch (_: Exception) { return }
                when (frame.optInt(0, -1)) {
                    0 -> {
                        val name = frame.optString(2)
                        val args = frame.optJSONArray(3).toAnyArray()
                        if (name == "___ready___") {
                            isConnected = true
                            readyReceived = true
                            reconnectDelay = 1000L
                            notifyHandlers("connect", emptyArray())
                        } else {
                            notifyHandlers(name, args)
                        }
                    }
                    1 -> webSocket.send(JSONArray().put(2).toString())
                    2 -> lastPong = System.currentTimeMillis()
                    3 -> {
                        val id = frame.optInt(1, -1)
                        val args = frame.optJSONArray(3).toAnyArray()
                        callbackTimeouts.remove(id)?.let { main.removeCallbacks(it) }
                        callbacks.remove(id)?.invoke(args)
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onLost(reason.ifBlank { "WebSocket getrennt" })
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onLost(t.message ?: "WebSocket-Verbindung fehlgeschlagen")
            }
        })
    }

    private fun JSONArray?.toAnyArray(): Array<Any?> {
        if (this == null) return emptyArray()
        return Array(length()) { index ->
            when (val value = opt(index)) {
                JSONObject.NULL -> null
                else -> value
            }
        }
    }

    private fun notifyHandlers(event: String, args: Array<Any?>) {
        handlers[event]?.toList()?.forEach { handler ->
            try { handler(args) } catch (_: Exception) { }
        }
    }

    private fun onLost(message: String) {
        val wasReady = isConnected || readyReceived
        isConnected = false
        readyReceived = false
        main.removeCallbacks(pingTask)
        callbackTimeouts.values.toList().forEach { main.removeCallbacks(it) }
        callbackTimeouts.clear()
        callbacks.values.toList().forEach { it(arrayOf("disconnected")) }
        callbacks.clear()
        if (wasReady) notifyHandlers("disconnect", emptyArray())
        else notifyHandlers("connect_error", arrayOf(message))
        if (!stopped) {
            val wait = reconnectDelay
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(10_000L)
            main.postDelayed({ if (!stopped && !isConnected) openSocket() }, wait)
        }
    }

    fun emit(event: String, vararg values: Any?) {
        if (!isConnected) return
        val args = values.toMutableList()
        val ack = args.lastOrNull() as? IoBrokerAck
        if (ack != null) args.removeAt(args.lastIndex)
        val id = ids.incrementAndGet()
        val frame = JSONArray()
        if (ack != null) {
            frame.put(3).put(id).put(event).put(JSONArray(args))
            callbacks[id] = ack.callback
            val timeout = Runnable {
                val pending = callbacks.remove(id)
                callbackTimeouts.remove(id)
                pending?.invoke(arrayOf("timeout: ioBroker antwortet auf $event nicht innerhalb von 12 Sekunden"))
            }
            callbackTimeouts[id] = timeout
            main.postDelayed(timeout, callbackTimeoutMs)
        } else {
            frame.put(0).put(id).put(event)
            if (args.isNotEmpty()) frame.put(JSONArray(args))
        }
        if (webSocket?.send(frame.toString()) != true && ack != null) {
            callbackTimeouts.remove(id)?.let { main.removeCallbacks(it) }
            callbacks.remove(id)?.invoke(arrayOf("send failed: WebSocket konnte $event nicht senden"))
        }
    }

    fun connected(): Boolean = isConnected

    fun disconnect() {
        stopped = true
        isConnected = false
        readyReceived = false
        main.removeCallbacks(pingTask)
        callbackTimeouts.values.toList().forEach { main.removeCallbacks(it) }
        callbackTimeouts.clear()
        callbacks.clear()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }

    fun close() = disconnect()
}
