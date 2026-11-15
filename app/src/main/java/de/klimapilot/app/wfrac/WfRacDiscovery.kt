package de.klimapilot.app.wfrac

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.util.ArrayDeque

const val WFRAC_MDNS_SERVICE_TYPE = "_beaver._tcp."

data class WfRacDiscoveredService(
    val name: String,
    val host: String,
    val port: Int,
)

/**
 * Short-lived, user-triggered discovery of WF-RAC modules on the current local network.
 * The scanner owns its NSD listener and multicast lock and releases both on stop().
 */
class WfRacDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var onFound: ((WfRacDiscoveredService) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private val pending = ArrayDeque<NsdServiceInfo>()
    private val seen = mutableSetOf<String>()
    private var resolving = false

    @Synchronized
    fun start(
        onFound: (WfRacDiscoveredService) -> Unit,
        onError: (String) -> Unit,
    ): Boolean {
        if (discoveryListener != null) return false
        this.onFound = onFound
        this.onError = onError
        pending.clear()
        seen.clear()
        resolving = false

        try {
            multicastLock = wifi.createMulticastLock("KlimaPilot-WF-RAC-mDNS").apply {
                setReferenceCounted(false)
                acquire()
            }
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    this@WfRacDiscovery.onError?.invoke("mDNS-Suche fehlgeschlagen (Fehler $errorCode)")
                    stop()
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (serviceInfo.serviceType.trimEnd('.').equals(WFRAC_MDNS_SERVICE_TYPE.trimEnd('.'), ignoreCase = true)) {
                        synchronized(this@WfRacDiscovery) {
                            if (seen.add(serviceInfo.serviceName)) {
                                pending.addLast(serviceInfo)
                                resolveNext()
                            }
                        }
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    this@WfRacDiscovery.onError?.invoke("mDNS-Suche konnte nicht sauber beendet werden (Fehler $errorCode)")
                    releaseMulticastLock()
                }
            }
            discoveryListener = listener
            nsd.discoverServices(WFRAC_MDNS_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            return true
        } catch (error: Exception) {
            discoveryListener = null
            releaseMulticastLock()
            this.onError?.invoke(error.message ?: "mDNS-Suche konnte nicht gestartet werden")
            return false
        }
    }

    @Synchronized
    private fun resolveNext() {
        if (resolving || pending.isEmpty() || discoveryListener == null) return
        val service = pending.removeFirst()
        resolving = true
        try {
            nsd.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    synchronized(this@WfRacDiscovery) {
                        resolving = false
                        resolveNext()
                    }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    synchronized(this@WfRacDiscovery) {
                        val address = serviceInfo.host?.hostAddress
                        if (address != null && serviceInfo.port == WFRAC_PORT) {
                            onFound?.invoke(WfRacDiscoveredService(serviceInfo.serviceName, address, serviceInfo.port))
                        } else if (address != null) {
                            onError?.invoke("Gefundenes Modul meldet unerwarteten Port ${serviceInfo.port}")
                        }
                        resolving = false
                        resolveNext()
                    }
                }
            })
        } catch (_: Exception) {
            resolving = false
            resolveNext()
        }
    }

    @Synchronized
    fun stop() {
        val listener = discoveryListener
        discoveryListener = null
        pending.clear()
        resolving = false
        if (listener != null) {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
        releaseMulticastLock()
        onFound = null
        onError = null
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        multicastLock = null
    }
}
