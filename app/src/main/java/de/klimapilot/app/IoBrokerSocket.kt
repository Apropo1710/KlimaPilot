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
    private val client = sharedClient
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
    /** Failed attempts before the first successful connection; the first ones are retried silently. */
    private var failedOpens = 0
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
                            failedOpens = 0
                            AppDiagnostics.reach("ioBroker", true, gapMs = 10 * 60_000L)
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
        if (!wasReady && !stopped && ++failedOpens <= SILENT_RETRIES) {
            // A single lost TCP connect (WLAN waking up, short stall on the ioBroker host) is
            // retried right away instead of being reported to the caller as an error.
            main.postDelayed({ if (!stopped && !isConnected) openSocket() }, 500L)
            return
        }
        if (wasReady) notifyHandlers("disconnect", emptyArray())
        else {
            // Closing our own finished session is not a failure; repeated failures are logged once.
            if (!stopped) AppDiagnostics.reach("ioBroker", false, message, gapMs = 10 * 60_000L)
            notifyHandlers("connect_error", arrayOf(message))
        }
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

    companion object {
        private const val SILENT_RETRIES = 1

        /**
         * One HTTP client for all ioBroker connections of the app. A client per connection
         * left its thread and connection pools behind on every short-lived connection.
         */
        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(0, TimeUnit.SECONDS)
                .build()
        }
    }
}

/**
 * Shared ioBroker connection for the app's short tasks (energy, schedules, profile sync,
 * diagnostics, script check). Instead of one WebSocket per task, all tasks borrow one connection
 * per address; it closes after [IDLE_MS] without a task. This keeps the ioBroker log and load low.
 * All methods are called on the main thread.
 */
internal object IoBrokerPool {
    private const val IDLE_MS = 120_000L
    private val main = Handler(Looper.getMainLooper())

    private class Entry(val url: String) {
        val socket = IoBrokerSocket(url, "name=klimapilot.app")
        val leases = mutableSetOf<IoBrokerLease>()
        var ready = false
        var started = false
        val idleClose = Runnable { close(this) }
    }

    private val entries = mutableMapOf<String, Entry>()

    private fun entry(url: String): Entry = entries.getOrPut(url) {
        Entry(url).also { e ->
            // Socket events arrive on the network thread; the pool is only touched on the main thread.
            e.socket.on("connect") {
                main.post {
                    e.ready = true
                    e.leases.toList().forEach { it.fire("connect") }
                }
            }
            e.socket.on("connect_error") { args ->
                main.post {
                    e.ready = false
                    e.leases.toList().forEach { it.fire("connect_error", args) }
                    // Nobody waits any more: stop retrying in the background.
                    if (e.leases.isEmpty()) close(e)
                }
            }
            e.socket.on("disconnect") {
                main.post {
                    e.ready = false
                    e.leases.toList().forEach { it.fire("disconnect") }
                    if (e.leases.isEmpty()) close(e)
                }
            }
        }
    }

    private fun close(e: Entry) {
        main.removeCallbacks(e.idleClose)
        if (entries[e.url] === e) entries.remove(e.url)
        runCatching { e.socket.off() }
        runCatching { e.socket.disconnect() }
    }

    fun attach(lease: IoBrokerLease) {
        val e = entry(lease.url)
        main.removeCallbacks(e.idleClose)
        e.leases += lease
        when {
            e.ready && e.socket.connected() -> main.post { lease.fire("connect") }
            !e.started -> { e.started = true; e.socket.connect() }
        }
    }

    fun detach(lease: IoBrokerLease) {
        val e = entries[lease.url] ?: return
        e.leases -= lease
        if (e.leases.isEmpty()) {
            main.removeCallbacks(e.idleClose)
            main.postDelayed(e.idleClose, IDLE_MS)
        }
    }

    fun emit(url: String, event: String, values: Array<out Any?>) {
        entries[url]?.socket?.emit(event, *values)
    }

    fun connected(url: String): Boolean = entries[url]?.let { it.ready && it.socket.connected() } == true
}

/** A task's share of the pooled connection; same calls as [IoBrokerSocket]. */
internal class IoBrokerLease(val url: String) {
    private val main = Handler(Looper.getMainLooper())
    private val handlers = mutableMapOf<String, MutableList<(Array<Any?>) -> Unit>>()
    private var attached = false

    fun on(event: String, handler: (Array<Any?>) -> Unit) {
        handlers.getOrPut(event) { mutableListOf() }.add(handler)
    }

    fun off() = runOnMain { handlers.clear() }

    fun connect() = runOnMain {
        if (attached) return@runOnMain
        attached = true
        IoBrokerPool.attach(this)
    }

    fun emit(event: String, vararg values: Any?) = runOnMain {
        if (attached) IoBrokerPool.emit(url, event, values)
    }

    fun connected(): Boolean = attached && IoBrokerPool.connected(url)

    fun disconnect() = runOnMain {
        if (!attached) return@runOnMain
        attached = false
        IoBrokerPool.detach(this)
    }

    internal fun fire(event: String, args: Array<Any?> = emptyArray()) {
        if (!attached) return
        handlers[event]?.toList()?.forEach { handler -> runCatching { handler(args) } }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
