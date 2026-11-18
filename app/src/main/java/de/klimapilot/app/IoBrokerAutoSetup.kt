package de.klimapilot.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import com.google.zxing.common.BitMatrix
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Einheitliche Ablage der ioBroker-Adresse.
 *
 * Maßgeblich für Verbindungen ist `iobroker_server` (vollständige ws://-URL). Die
 * Einstellungskarte nutzt zusätzlich Host, Port und Protokoll in getrennten Schlüsseln.
 * Ältere Testversionen haben in `klimapilot_iobroker_address` teilweise eine vollständige
 * URL abgelegt; beides wird hier tolerant gelesen.
 */
internal object IoBrokerAddress {
    const val DEFAULT_PORT = 8084

    /** Wandelt Nutzereingaben wie `192.168.178.54`, `host:8084` oder `ws://host:8084` in eine ws(s)-URL um. */
    fun normalize(input: String?): String? {
        var value = input?.trim()?.trimEnd('/') ?: return null
        if (value.isEmpty()) return null
        val secure = value.startsWith("wss://", ignoreCase = true)
        value = when {
            value.startsWith("ws://", ignoreCase = true) -> value.substring(5)
            secure -> value.substring(6)
            value.contains("://") -> return null
            else -> value
        }
        val authority = value.substringBefore('/').substringBefore('?')
        if (authority.isEmpty() || authority.any { it.isWhitespace() }) return null
        val host: String
        val port: Int
        if (authority.startsWith("[")) {
            val end = authority.indexOf(']')
            if (end <= 1) return null
            host = authority.substring(0, end + 1)
            val rest = authority.substring(end + 1)
            port = if (rest.isEmpty()) DEFAULT_PORT else rest.removePrefix(":").toIntOrNull() ?: return null
        } else if (authority.count { it == ':' } == 1) {
            host = authority.substringBefore(':')
            port = authority.substringAfter(':').toIntOrNull() ?: return null
        } else if (authority.contains(':')) {
            return null
        } else {
            host = authority
            port = DEFAULT_PORT
        }
        if (host.isEmpty() || port !in 1..65535) return null
        return (if (secure) "wss://" else "ws://") + host + ":" + port
    }

    fun saved(prefs: SharedPreferences): String? {
        normalize(prefs.getString("iobroker_server", null))?.let { return it }
        val configured = prefs.getString("klimapilot_iobroker_address", null)?.trim().orEmpty()
        if (configured.isEmpty()) return null
        if (configured.contains("://")) return normalize(configured)
        val port = prefs.getString("klimapilot_iobroker_port", null)?.toIntOrNull() ?: DEFAULT_PORT
        val scheme = if (prefs.getString("klimapilot_iobroker_protocol", null) == "wss") "wss://" else "ws://"
        return normalize("$scheme$configured:$port")
    }

    fun store(prefs: SharedPreferences, url: String) {
        val normalized = normalize(url) ?: return
        val secure = normalized.startsWith("wss://")
        val authority = normalized.substringAfter("://")
        val host = authority.substringBeforeLast(':')
        val port = authority.substringAfterLast(':')
        prefs.edit()
            .putString("iobroker_server", normalized)
            .putString("klimapilot_iobroker_address", host)
            .putString("klimapilot_iobroker_port", port)
            .putString("klimapilot_iobroker_protocol", if (secure) "wss" else "ws")
            .apply()
    }
}

/**
 * Findet ioBroker ohne Nutzereingabe: zuerst die gespeicherte Adresse, danach alle Geräte
 * im eigenen WLAN-Subnetz mit offenem WebSocket-Port 8084. Jeder Kandidat wird erst als
 * gefunden gewertet, wenn die reguläre Verbindungsprüfung (inkl. Alive-Test) erfolgreich war.
 */
internal object IoBrokerAutoSetup {
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val CANDIDATE_TIMEOUT_MS = 10_000L
    private const val PORT_PROBE_TIMEOUT_MS = 500
    private const val MAX_SCANNED_CANDIDATES = 8

    /**
     * Ergebnis auf dem Main-Thread: verbundene URL (die Verbindung bleibt offen) oder null
     * mit einer kurzen Fehlerbeschreibung.
     */
    fun connect(context: Context, preferred: List<String>, scanNetwork: Boolean, callback: (String?, String) -> Unit) {
        val appContext = context.applicationContext
        val first = preferred.mapNotNull { IoBrokerAddress.normalize(it) }.distinct()
        tryCandidates(first, lastError = null) { url, error ->
            if (url != null || !scanNetwork) {
                callback(url, error ?: "ioBroker wurde nicht gefunden.")
                return@tryCandidates
            }
            Thread {
                val scanned = runCatching { scanLocalNetwork(appContext) }.getOrDefault(emptyList())
                    .filterNot { it in first }
                mainHandler.post {
                    if (scanned.isEmpty()) {
                        callback(null, error ?: "Im WLAN wurde kein ioBroker gefunden.")
                    } else {
                        tryCandidates(scanned, error) { found, scanError ->
                            callback(found, scanError ?: "Im WLAN wurde kein ioBroker gefunden.")
                        }
                    }
                }
            }.apply { name = "klimapilot-iobroker-scan"; isDaemon = true }.start()
        }
    }

    private fun tryCandidates(candidates: List<String>, lastError: String?, done: (String?, String?) -> Unit) {
        val url = candidates.firstOrNull()
        if (url == null) {
            done(null, lastError)
            return
        }
        var finished = false
        val timeout = Runnable {
            if (!finished) {
                finished = true
                IoBrokerClient.disconnect()
                tryCandidates(candidates.drop(1), "Zeitüberschreitung bei $url", done)
            }
        }
        mainHandler.postDelayed(timeout, CANDIDATE_TIMEOUT_MS)
        IoBrokerClient.connect(url, subscribeAll = false) { connected, message ->
            if (finished) return@connect
            finished = true
            mainHandler.removeCallbacks(timeout)
            if (connected) done(url, null)
            else {
                IoBrokerClient.disconnect()
                tryCandidates(candidates.drop(1), message, done)
            }
        }
    }

    /** Blockierend; nicht auf dem Main-Thread aufrufen. */
    private fun scanLocalNetwork(context: Context): List<String> {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        @Suppress("DEPRECATION")
        val wifi = connectivity.allNetworks.firstOrNull { network ->
            connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: return emptyList()
        val link = connectivity.getLinkProperties(wifi) ?: return emptyList()
        val own = link.linkAddresses.firstOrNull { it.address is Inet4Address && it.address.isSiteLocalAddress }
            ?: return emptyList()
        val hosts = subnetHosts(own.address.address, own.prefixLength)
        val pool = Executors.newFixedThreadPool(48)
        try {
            val probes = hosts.map { host ->
                pool.submit(Callable {
                    runCatching {
                        wifi.socketFactory.createSocket().use { socket ->
                            socket.connect(InetSocketAddress(host, IoBrokerAddress.DEFAULT_PORT), PORT_PROBE_TIMEOUT_MS)
                        }
                        host
                    }.getOrNull()
                })
            }
            return probes.mapNotNull { runCatching { it.get(5, TimeUnit.SECONDS) }.getOrNull() }
                .take(MAX_SCANNED_CANDIDATES)
                .map { "ws://${it.hostAddress}:${IoBrokerAddress.DEFAULT_PORT}" }
        } finally {
            pool.shutdownNow()
        }
    }

    /** Alle Hosts des eigenen Subnetzes; größere Netze werden auf das eigene /24 begrenzt. */
    internal fun subnetHosts(ownAddress: ByteArray, prefixLength: Int): List<InetAddress> {
        val prefix = prefixLength.coerceIn(24, 30)
        val own = ownAddress.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val network = own and mask
        val size = 1L shl (32 - prefix)
        return (1L until size - 1).map { network + it }.filter { it != own }.map { value ->
            InetAddress.getByAddress(byteArrayOf(
                (value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte()
            ))
        }
    }
}

/**
 * Fallback: Kontenliste und ioBroker-Adresse per QR-Code von einem eingerichteten Smartphone
 * übernehmen. Der QR enthält nur Passwort-Hashes (PBKDF2), keine Klartextpasswörter, und ist
 * zeitlich begrenzt gültig.
 */
internal object KlimaPilotSetupTransfer {
    const val PREFIX = "KLIMAPILOT-SETUP:"
    const val VALIDITY_MS = 10 * 60 * 1000L
    private const val CLOCK_SKEW_MS = 5 * 60 * 1000L

    data class Payload(val registry: KlimaPilotAccountRegistry, val ioBrokerUrl: String?)

    fun create(registry: KlimaPilotAccountRegistry, ioBrokerUrl: String?, now: Long = System.currentTimeMillis()): String =
        PREFIX + JSONObject().apply {
            put("v", 1)
            put("ts", now)
            IoBrokerAddress.normalize(ioBrokerUrl)?.let { put("iobroker", it) }
            put("registry", registry.toJson())
        }.toString()

    fun parse(raw: String?, now: Long = System.currentTimeMillis()): Payload? {
        if (raw == null || !raw.startsWith(PREFIX) || raw.length > 8_000) return null
        return runCatching {
            val json = JSONObject(raw.removePrefix(PREFIX))
            if (json.getInt("v") != 1) return null
            val ts = json.getLong("ts")
            if (ts < now - VALIDITY_MS || ts > now + CLOCK_SKEW_MS) return null
            val registry = KlimaPilotAccountRegistry.fromJson(json.getJSONObject("registry")) ?: return null
            if (registry.accounts.isEmpty()) return null
            val url = json.optString("iobroker", "").takeIf { it.isNotBlank() }?.let { IoBrokerAddress.normalize(it) ?: return null }
            Payload(registry, url)
        }.getOrNull()
    }
}

internal fun BitMatrix.toSetupBitmap(): Bitmap {
    val pixels = IntArray(width * height) { index ->
        if (this[index % width, index / width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}
