package de.klimapilot.app.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.io.IOException
import java.net.ServerSocket
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

const val KLIMAPILOT_PEER_SERVICE_TYPE = "_klimapilot-sync._tcp."
private const val PEER_PROTOCOL_MAJOR = "1"
private const val TXT_PROTOCOL_VERSION = "v"
private const val TXT_DISCOVERY_ID = "id"

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
        if (instanceName.isBlank() || instanceName.length > 63) return null
        if (instanceName in ownInstanceNames) return null
        if (hostAddress.isNullOrBlank() || hostAddress.length > 64) return null
        if (port !in 1..65535) return null
        val version = attributes[TXT_PROTOCOL_VERSION]?.toString(Charsets.UTF_8) ?: return null
        if (version != PEER_PROTOCOL_MAJOR) return null
        val discoveryId = attributes[TXT_DISCOVERY_ID]?.toString(Charsets.UTF_8) ?: return null
        if (!discoveryId.matches(Regex("[a-f0-9]{32}")) || discoveryId == ownDiscoveryId) return null
        if (attributes.keys != setOf(TXT_PROTOCOL_VERSION, TXT_DISCOVERY_ID)) return null
        return UnverifiedClimatePeer(instanceName, hostAddress, port, version.toInt(), discoveryId)
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

    @Volatile private var generation = 0L
    @Volatile private var active = false
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var ownServiceName: String? = null
    private var ownDiscoveryId = ""
    private var onPeerFound: ((UnverifiedClimatePeer) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private val pending = ArrayDeque<NsdServiceInfo>()
    private val seenNames = mutableSetOf<String>()
    private var resolving = false

    @Synchronized
    fun start(
        onPeerFound: (UnverifiedClimatePeer) -> Unit,
        onError: (String) -> Unit,
    ): Boolean {
        if (active) return false
        active = true
        generation += 1
        val run = generation
        this.onPeerFound = onPeerFound
        this.onError = onError
        ownDiscoveryId = UUID.randomUUID().toString().replace("-", "").lowercase(Locale.ROOT)
        ownServiceName = "KlimaPilot-${ownDiscoveryId.take(8)}"
        pending.clear()
        seenNames.clear()
        resolving = false

        try {
            multicastLock = wifi.createMulticastLock("KlimaPilot-Peer-Discovery").apply {
                setReferenceCounted(false)
                acquire()
            }
            val socket = ServerSocket(0).apply { soTimeout = 1000 }
            serverSocket = socket
            startRejectOnlyAcceptLoop(socket, run)

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
                        if (seenNames.add(serviceInfo.serviceName)) {
                            pending.addLast(serviceInfo)
                            resolveNext(run)
                        }
                    }
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    synchronized(this@ClimateProfilePeerDiscovery) {
                        if (isCurrent(run)) seenNames.remove(serviceInfo.serviceName)
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

    @Synchronized
    private fun resolveNext(run: Long) {
        if (!isCurrent(run) || resolving || pending.isEmpty()) return
        resolving = true
        val candidate = pending.removeFirst()
        try {
            @Suppress("DEPRECATION")
            nsd.resolveService(candidate, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    synchronized(this@ClimateProfilePeerDiscovery) {
                        if (!isCurrent(run)) return
                        resolving = false
                        resolveNext(run)
                    }
                }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    synchronized(this@ClimateProfilePeerDiscovery) {
                        if (!isCurrent(run)) return
                        resolving = false
                        val peer = ClimatePeerAnnouncement.parse(
                            instanceName = serviceInfo.serviceName,
                            hostAddress = serviceInfo.host?.hostAddress,
                            port = serviceInfo.port,
                            attributes = serviceInfo.attributes ?: emptyMap(),
                            ownInstanceNames = setOfNotNull(ownServiceName),
                            ownDiscoveryId = ownDiscoveryId,
                        )
                        if (peer != null) onPeerFound?.invoke(peer)
                        resolveNext(run)
                    }
                }
            })
        } catch (_: Exception) {
            resolving = false
            resolveNext(run)
        }
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
        resolving = false
        ownServiceName = null
        ownDiscoveryId = ""
        multicastLock?.let { lock -> if (lock.isHeld) runCatching { lock.release() } }
        multicastLock = null
        onPeerFound = null
        onError = null
    }
}
