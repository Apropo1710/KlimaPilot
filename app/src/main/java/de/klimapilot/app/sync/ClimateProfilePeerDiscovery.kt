package de.klimapilot.app.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.ServerSocket
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

const val KLIMAPILOT_PEER_SERVICE_TYPE = "_klimapilot-sync._tcp."
private const val PEER_PROTOCOL_MAJOR = "1"
private const val TXT_PROTOCOL_VERSION = "v"
private const val TXT_DISCOVERY_ID = "id"
private const val MAX_DISCOVERED_PEERS = 20
private const val MAX_PENDING_RESOLVES = 20
private const val MAX_RESOLVE_RETRIES = 2
private const val RESOLVE_TIMEOUT_MS = 8_000L

/** A discovered announcement only. It is deliberately not a trusted/paired device. */
internal data class UnverifiedClimatePeer(
    val instanceName: String,
    val hostAddress: String,
    val port: Int,
    val protocolMajor: Int,
    val discoveryId: String,
)

/** Strict validation for untrusted DNS-SD metadata. No value here authenticates a peer. */
internal object ClimatePeerAnnouncement {
    fun parse(
        instanceName: String,
        hostAddress: String?,
        port: Int,
        attributes: Map<String, ByteArray>,
        ownInstanceNames: Set<String>,
        ownDiscoveryId: String,
    ): UnverifiedClimatePeer? {
        if (!instanceName.matches(Regex("KlimaPilot-[a-f0-9]{8}"))) return null
        if (instanceName in ownInstanceNames) return null
        val resolvedHost = hostAddress ?: return null
        if (!isAllowedLocalAddress(resolvedHost)) return null
        if (port !in 1..65535) return null
        val version = attributes[TXT_PROTOCOL_VERSION]?.toString(Charsets.UTF_8) ?: return null
        if (version != PEER_PROTOCOL_MAJOR) return null
        val discoveryId = attributes[TXT_DISCOVERY_ID]?.toString(Charsets.UTF_8) ?: return null
        if (!discoveryId.matches(Regex("[a-f0-9]{32}")) || discoveryId == ownDiscoveryId) return null
        if (attributes.keys != setOf(TXT_PROTOCOL_VERSION, TXT_DISCOVERY_ID)) return null
        return UnverifiedClimatePeer(instanceName, resolvedHost, port, version.toInt(), discoveryId)
    }

    private fun isAllowedLocalAddress(value: String?): Boolean {
        if (value.isNullOrBlank() || value.length > 64 || value.any { it.isWhitespace() }) return false
        if (value.matches(Regex("[0-9.]+"))) {
            val octets = value.split('.').mapNotNull { it.toIntOrNull() }
            if (octets.size != 4 || octets.any { it !in 0..255 }) return false
            val (a, b) = octets
            return a == 10 || a == 192 && b == 168 || a == 172 && b in 16..31 || a == 169 && b == 254
        }
        if (!value.contains(':')) return false
        val addressPart = value.substringBefore('%')
        if (addressPart.isBlank() || value.count { it == '%' } > 1) return false
        val address = runCatching { InetAddress.getByName(addressPart) }.getOrNull() as? Inet6Address ?: return false
        val bytes = address.address
        val uniqueLocal = (bytes[0].toInt() and 0xfe) == 0xfc
        return !address.isAnyLocalAddress && !address.isLoopbackAddress && !address.isMulticastAddress &&
            (address.isLinkLocalAddress || address.isSiteLocalAddress || uniqueLocal)
    }
}

/**
 * Foreground-only DNS-SD discovery. It never exchanges application data.
 * The announced TCP socket accepts connections only to close them immediately.
 * The owner must call stop() when the visible diagnostic surface is left or the app stops.
 */
internal class ClimateProfilePeerDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Volatile private var generation = 0L
    @Volatile private var active = false
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var activeWifiNetwork: Network? = null
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var ownServiceName: String? = null
    private var ownDiscoveryId = ""
    private var onPeerFound: ((UnverifiedClimatePeer) -> Unit)? = null
    private var onPeerLost: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private val pending = ArrayDeque<NsdServiceInfo>()
    private val seenNames = mutableSetOf<String>()
    private val resolveRetries = mutableMapOf<String, Int>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var resolving = false
    private var resolveTokenCounter = 0L
    private var activeResolveToken: Long? = null
    private var resolveTimeout: Runnable? = null

    @Synchronized
    fun start(
        onPeerFound: (UnverifiedClimatePeer) -> Unit,
        onPeerLost: (String) -> Unit,
        onError: (String) -> Unit,
    ): Boolean {
        if (active) return false
        active = true
        generation += 1
        val run = generation
        this.onPeerFound = onPeerFound
        this.onPeerLost = onPeerLost
        this.onError = onError
        ownDiscoveryId = UUID.randomUUID().toString().replace("-", "").lowercase(Locale.ROOT)
        ownServiceName = "KlimaPilot-${ownDiscoveryId.take(8)}"
        pending.clear()
        seenNames.clear()
        resolveRetries.clear()
        resolving = false

        try {
            multicastLock = wifi.createMulticastLock("KlimaPilot-Peer-Discovery").apply {
                setReferenceCounted(false)
                acquire()
            }
            val socket = ServerSocket(0).apply { soTimeout = 1000 }
            serverSocket = socket
            startRejectOnlyAcceptLoop(socket, run)
            registerWifiNetworkCallback(run)

            val service = NsdServiceInfo().apply {
                serviceName = ownServiceName
                serviceType = KLIMAPILOT_PEER_SERVICE_TYPE
                port = socket.localPort
                setAttribute(TXT_PROTOCOL_VERSION, PEER_PROTOCOL_MAJOR)
                setAttribute(TXT_DISCOVERY_ID, ownDiscoveryId)
            }
            val registration = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    synchronized(this@ClimateProfilePeerDiscovery) {
                        if (isCurrent(run)) ownServiceName = serviceInfo.serviceName
                    }
                }
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    fail(run, "KlimaPilot-Dienst konnte nicht angekündigt werden (Fehler $errorCode)")
                }
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    fail(run, "KlimaPilot-Dienst konnte nicht sauber abgemeldet werden (Fehler $errorCode)")
                }
            }
            registrationListener = registration
            nsd.registerService(service, NsdManager.PROTOCOL_DNS_SD, registration)

            val discovery = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    fail(run, "Suche nach KlimaPilot-Geräten fehlgeschlagen (Fehler $errorCode)")
                }
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (!serviceInfo.serviceType.trimEnd('.').equals(
                            KLIMAPILOT_PEER_SERVICE_TYPE.trimEnd('.'), ignoreCase = true
                        )) return
                    synchronized(this@ClimateProfilePeerDiscovery) {
                        if (!isCurrent(run) || serviceInfo.serviceName == ownServiceName) return
                        if (serviceInfo.serviceName !in seenNames && seenNames.size < MAX_DISCOVERED_PEERS && pending.size < MAX_PENDING_RESOLVES) {
                            seenNames.add(serviceInfo.serviceName)
                            pending.addLast(serviceInfo)
                            resolveNext(run)
                        }
                    }
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    synchronized(this@ClimateProfilePeerDiscovery) {
                        if (isCurrent(run) && seenNames.remove(serviceInfo.serviceName)) {
                            pending.removeAll { it.serviceName == serviceInfo.serviceName }
                            resolveRetries.remove(serviceInfo.serviceName)
                            onPeerLost?.invoke(serviceInfo.serviceName)
                        }
                    }
                }
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    fail(run, "Suche konnte nicht sauber beendet werden (Fehler $errorCode)")
                }
            }
            discoveryListener = discovery
            nsd.discoverServices(KLIMAPILOT_PEER_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
            return true
        } catch (error: Exception) {
            val callback = this.onError
            cleanupLocked()
            callback?.invoke(error.message ?: "KlimaPilot-WLAN-Suche konnte nicht gestartet werden")
            return false
        }
    }

    private fun registerWifiNetworkCallback(run: Long) {
        val current = connectivity.activeNetwork
        activeWifiNetwork = current?.takeIf {
            connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                synchronized(this@ClimateProfilePeerDiscovery) {
                    if (!isCurrent(run)) return
                    val previous = activeWifiNetwork
                    if (previous == null) {
                        activeWifiNetwork = network
                    } else if (previous != network) {
                        fail(run, "Das WLAN-Netzwerk hat gewechselt. Bitte die Suche erneut starten.")
                    }
                }
            }

            override fun onLost(network: Network) {
                synchronized(this@ClimateProfilePeerDiscovery) {
                    if (isCurrent(run) && network == activeWifiNetwork) {
                        fail(run, "Die WLAN-Verbindung wurde unterbrochen. Bitte nach Wiederherstellung erneut suchen.")
                    }
                }
            }
        }
        networkCallback = callback
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivity.registerNetworkCallback(request, callback, mainHandler)
    }

    @Synchronized
    private fun resolveNext(run: Long) {
        if (!isCurrent(run) || resolving || pending.isEmpty()) return
        resolving = true
        val candidate = pending.removeFirst()
        val token = ++resolveTokenCounter
        activeResolveToken = token
        val timeout = Runnable {
            synchronized(this@ClimateProfilePeerDiscovery) {
                if (isCurrent(run) && activeResolveToken == token) {
                    activeResolveToken = null
                    resolveTimeout = null
                    resolving = false
                    scheduleResolveRetry(run, candidate)
                    resolveNext(run)
                }
            }
        }
        resolveTimeout = timeout
        mainHandler.postDelayed(timeout, RESOLVE_TIMEOUT_MS)
        try {
            @Suppress("DEPRECATION")
            nsd.resolveService(candidate, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    synchronized(this@ClimateProfilePeerDiscovery) {
                        if (!isCurrent(run) || activeResolveToken != token) return
                        resolveTimeout?.let(mainHandler::removeCallbacks)
                        resolveTimeout = null
                        activeResolveToken = null
                        resolving = false
                        scheduleResolveRetry(run, serviceInfo)
                        resolveNext(run)
                    }
                }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    synchronized(this@ClimateProfilePeerDiscovery) {
                        if (!isCurrent(run) || activeResolveToken != token) return
                        resolveTimeout?.let(mainHandler::removeCallbacks)
                        resolveTimeout = null
                        activeResolveToken = null
                        resolving = false
                        resolveRetries.remove(serviceInfo.serviceName)
                        val peer = ClimatePeerAnnouncement.parse(
                            instanceName = serviceInfo.serviceName,
                            hostAddress = serviceInfo.host?.hostAddress,
                            port = serviceInfo.port,
                            attributes = serviceInfo.attributes ?: emptyMap(),
                            ownInstanceNames = setOfNotNull(ownServiceName),
                            ownDiscoveryId = ownDiscoveryId,
                        )
                        if (peer != null && serviceInfo.serviceName in seenNames) onPeerFound?.invoke(peer)
                        resolveNext(run)
                    }
                }
            })
        } catch (_: Exception) {
            resolveTimeout?.let(mainHandler::removeCallbacks)
            resolveTimeout = null
            activeResolveToken = null
            resolving = false
            scheduleResolveRetry(run, candidate)
            resolveNext(run)
        }
    }

    private fun scheduleResolveRetry(run: Long, candidate: NsdServiceInfo) {
        val name = candidate.serviceName
        if (!isCurrent(run) || name !in seenNames) return
        val retries = (resolveRetries[name] ?: 0) + 1
        resolveRetries[name] = retries
        if (retries > MAX_RESOLVE_RETRIES) {
            seenNames.remove(name)
            resolveRetries.remove(name)
            onPeerLost?.invoke(name)
            return
        }
        mainHandler.postDelayed({
            synchronized(this@ClimateProfilePeerDiscovery) {
                if (isCurrent(run) && name in seenNames && pending.none { it.serviceName == name }) {
                    if (pending.size < MAX_PENDING_RESOLVES) pending.addLast(candidate)
                    else seenNames.remove(name)
                    resolveNext(run)
                }
            }
        }, 750L * retries)
    }

    private fun startRejectOnlyAcceptLoop(socket: ServerSocket, run: Long) {
        val running = AtomicBoolean(true)
        acceptThread = Thread({
            while (running.get() && !socket.isClosed) {
                try {
                    socket.accept().use { /* No reads, writes, or application protocol in discovery phase. */ }
                } catch (_: java.net.SocketTimeoutException) {
                    if (!isCurrent(run)) running.set(false)
                } catch (_: IOException) {
                    running.set(false)
                }
            }
        }, "KlimaPilot-Peer-Reject-$run").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    fun stop() {
        if (!active && registrationListener == null && discoveryListener == null && serverSocket == null) return
        generation += 1
        cleanupLocked()
    }

    @Synchronized
    private fun fail(run: Long, message: String) {
        if (!isCurrent(run)) return
        val callback = onError
        cleanupLocked()
        callback?.invoke(message)
    }

    private fun isCurrent(run: Long): Boolean = active && generation == run

    private fun cleanupLocked() {
        active = false
        resolveTimeout?.let(mainHandler::removeCallbacks)
        resolveTimeout = null
        activeResolveToken = null
        val callback = networkCallback
        networkCallback = null
        if (callback != null) runCatching { connectivity.unregisterNetworkCallback(callback) }
        activeWifiNetwork = null
        val discovery = discoveryListener
        discoveryListener = null
        if (discovery != null) runCatching { nsd.stopServiceDiscovery(discovery) }
        val registration = registrationListener
        registrationListener = null
        if (registration != null) runCatching { nsd.unregisterService(registration) }
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread = null
        pending.clear()
        seenNames.clear()
        resolveRetries.clear()
        resolving = false
        ownServiceName = null
        ownDiscoveryId = ""
        multicastLock?.let { lock -> if (lock.isHeld) runCatching { lock.release() } }
        multicastLock = null
        onPeerFound = null
        onPeerLost = null
        onError = null
    }
}
