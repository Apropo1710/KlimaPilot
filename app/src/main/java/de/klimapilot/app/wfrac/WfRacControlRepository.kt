package de.klimapilot.app.wfrac

import android.content.Context
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns one serialised WF-RAC client per physical air conditioner.
 *
 * Pairing is always an explicit user action. Once paired, every reconnect verifies the
 * airconId, transport and (for HTTPS) the pinned certificate before reading or writing state.
 */
class WfRacControlRepository(context: Context) {
    private val appContext = context.applicationContext
    private val settings = appContext.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE)
    val identities = WfRacIdentityStore(appContext)
    private val deviceId: String by lazy {
        settings.getString("wf_rac_device_id", null) ?: java.util.UUID.randomUUID().toString().also {
            check(settings.edit().putString("wf_rac_device_id", it).commit()) {
                "Die lokale Installationskennung konnte nicht gespeichert werden."
            }
        }
    }
    private val clients = ConcurrentHashMap<String, WfRacClient>()
    private val registered = ConcurrentHashMap.newKeySet<String>()
    private val unitLocks = ConcurrentHashMap<String, Mutex>()

    private fun clientFor(identity: WfRacStoredIdentity): WfRacClient =
        clients.compute(identity.airconId) { _, existing ->
            existing?.takeIf {
                it.host == identity.host && it.scheme == identity.scheme &&
                    it.certificatePin == identity.certificatePin
            } ?: WfRacClient(
                host = identity.host,
                deviceId = deviceId,
                scheme = identity.scheme,
                certificatePin = identity.certificatePin,
            )
        }!!

    /**
     * Explicitly pairs a module. Unlike background reconnect, this method may establish trust
     * for an additional physical unit after the user has requested pairing in the UI.
     */
    suspend fun pair(host: String): WfRacConnectionResult {
        val address = host.trim()
        require(address.isNotBlank()) { "IP-Adresse des WLAN-Moduls fehlt." }
        // An explicit pairing by the user re-establishes trust from scratch: the transport is
        // detected anew, and stale identities (old IP of this unit, or another unit that
        // used to have this IP) are replaced instead of blocking the pairing.
        val probe = WfRacClient(host = address, deviceId = deviceId)
        val airconId = probe.getDeviceInfo()
        val actualScheme = probe.scheme
            ?: throw SecurityException("Das WF-RAC-Modul hat kein gültiges Verbindungsverfahren gemeldet.")
        if (actualScheme == Scheme.HTTPS && probe.certificatePin.isNullOrBlank()) {
            throw SecurityException("Das HTTPS-Zertifikat konnte nicht dauerhaft identifiziert werden.")
        }
        val staleAtHost = identities.findByHost(address)
        if (staleAtHost != null && staleAtHost.airconId != airconId) {
            if (!identities.remove(staleAtHost.airconId)) {
                throw IllegalStateException("Die alte Zuordnung dieser IP konnte nicht entfernt werden.")
            }
            clients.remove(staleAtHost.airconId)
        }

        // Only after the explicit pairing action and identity checks do we read the full status.
        val status = probe.getStatus(airconId)
        val identity = WfRacStoredIdentity(
            airconId = airconId,
            host = address,
            scheme = actualScheme,
            certificatePin = probe.certificatePin,
            lastVerifiedAt = System.currentTimeMillis(),
        )
        if (!identities.save(identity)) {
            throw IllegalStateException("Die Geräteidentität konnte nicht dauerhaft gespeichert werden.")
        }
        clients[airconId] = probe
        settings.edit().putString("wf_rac_host", address).apply()
        return WfRacConnectionResult(airconId, status, probe.trace.module, actualScheme, probe.certificatePin)
    }

    /**
     * Restores a lost module identity for a device that still remembers its airconId and host.
     * Trust is re-established only when the module at that host reports exactly the expected
     * airconId; anything else needs an explicit pairing by the user.
     */
    suspend fun restore(host: String, expectedAirconId: String) {
        val address = host.trim()
        require(address.isNotBlank()) { "IP-Adresse des WLAN-Moduls fehlt." }
        if (identities.findByAirconId(expectedAirconId) != null) return
        val knownAtHost = identities.findByHost(address)
        if (knownAtHost != null && knownAtHost.airconId != expectedAirconId) {
            throw SecurityException("An der gespeicherten IP ist eine andere Klimaanlage gekoppelt. Bitte in den Geräteeinstellungen neu koppeln.")
        }
        val probe = WfRacClient(host = address, deviceId = deviceId)
        val airconId = probe.getDeviceInfo()
        if (airconId != expectedAirconId) {
            throw SecurityException("An der gespeicherten IP antwortet eine andere Klimaanlage. Bitte in den Geräteeinstellungen neu koppeln.")
        }
        val scheme = probe.scheme
            ?: throw SecurityException("Das WF-RAC-Modul hat kein gültiges Verbindungsverfahren gemeldet.")
        if (scheme == Scheme.HTTPS && probe.certificatePin.isNullOrBlank()) {
            throw SecurityException("Das HTTPS-Zertifikat konnte nicht dauerhaft identifiziert werden.")
        }
        val identity = WfRacStoredIdentity(
            airconId = airconId,
            host = address,
            scheme = scheme,
            certificatePin = probe.certificatePin,
            lastVerifiedAt = System.currentTimeMillis(),
        )
        if (!identities.save(identity)) {
            throw IllegalStateException("Die Geräteidentität konnte nicht dauerhaft gespeichert werden.")
        }
        clients[airconId] = probe
    }

    suspend fun readStatus(airconId: String): WfRacConnectionResult {
        val identity = identities.findByAirconId(airconId)
            ?: throw IllegalArgumentException("Für dieses Gerät ist noch kein WF-RAC-Modul gekoppelt.")
        val client = clientFor(identity)
        val verified = verifyIdentity(identity, client)
        val status = client.getStatus(airconId)
        val updated = identity.copy(lastVerifiedAt = System.currentTimeMillis())
        if (updated != identity && !identities.save(updated)) {
            throw IllegalStateException("Der Prüfzeitpunkt der Geräteidentität konnte nicht gespeichert werden.")
        }
        return WfRacConnectionResult(airconId, status, client.trace.module, verified.scheme!!, verified.certificatePin)
    }

    /**
     * Changes only the requested fields on top of a freshly read state. All model, firmware,
     * vane and vendor flags not explicitly changed are carried through unchanged.
     */
    suspend fun apply(
        airconId: String,
        waitUpToS: Int = 0,
        onWait: (Int) -> Unit = {},
        change: (AirconState) -> AirconState,
    ): AirconState {
        require(waitUpToS in 0..60) { "Ungültige Wartezeit." }
        val lock = unitLocks.computeIfAbsent(airconId) { Mutex() }
        return lock.withLock {
            val identity = identities.findByAirconId(airconId)
                ?: throw IllegalArgumentException("Für dieses Gerät ist noch kein WF-RAC-Modul gekoppelt.")
            val client = clientFor(identity)

            suspend fun attempt(): AirconState {
                verifyIdentity(identity, client)
                val current = client.getStatus(airconId).state
                val next = change(current)
                require(next.modelNrRaw == current.modelNrRaw && next.modelNr == current.modelNr) {
                    "Die Gerätekennung darf durch einen Steuerbefehl nicht verändert werden."
                }
                val echoed = client.setStat(airconId, next)
                return echoed
            }

            if (airconId !in registered) {
                client.registerAccount(airconId, TimeZone.getDefault().id)
                registered += airconId
            }

            try {
                attempt()
            } catch (error: WfRacException) {
                if (error.code != WfRacException.UNIT_REFUSED) throw error
                val locked = client.getStatus(airconId).lockedForS
                if (locked > 0) {
                    if (locked > waitUpToS) {
                        throw WfRacException(
                            WfRacException.UNIT_BUSY,
                            "Die Klimaanlage wird gerade von einem anderen Gerät gesteuert.",
                            error.result,
                            locked,
                        )
                    }
                    onWait(locked)
                    delay((locked + 1) * 1000L)
                } else {
                    // The module may have forgotten our account. Registration is idempotent.
                    registered.remove(airconId)
                    client.registerAccount(airconId, TimeZone.getDefault().id)
                    registered += airconId
                }
                try {
                    attempt()
                } catch (again: WfRacException) {
                    if (again.code != WfRacException.UNIT_REFUSED) throw again
                    val remaining = runCatching { client.getStatus(airconId).lockedForS }.getOrDefault(0)
                    if (remaining > 0) {
                        throw WfRacException(
                            WfRacException.UNIT_BUSY,
                            "Die Klimaanlage wird gerade von einem anderen Gerät gesteuert.",
                            again.result,
                            remaining,
                        )
                    }
                    throw again
                }
            }
        }
    }

    private suspend fun verifyIdentity(identity: WfRacStoredIdentity, client: WfRacClient): WfRacClient {
        val actualId = client.getDeviceInfo()
        if (actualId != identity.airconId) {
            throw SecurityException("Sicherheitsprüfung abgebrochen: Die Geräte-ID stimmt nicht mit der Kopplung überein.")
        }
        if (client.scheme != identity.scheme) {
            throw SecurityException("Sicherheitsprüfung abgebrochen: Das Verbindungsverfahren hat sich geändert.")
        }
        if (identity.scheme == Scheme.HTTPS && client.certificatePin != identity.certificatePin) {
            throw SecurityException("Sicherheitsprüfung abgebrochen: Der HTTPS-Zertifikat-Fingerprint hat sich geändert.")
        }
        return client
    }

    fun forget(airconId: String): Boolean {
        // Pairing reset must never detach a mutex that an active command is using.
        // Keep the per-unit mutex in the map: queued commands then observe the removed
        // identity after this critical section instead of running against a new lock.
        val lock = unitLocks.computeIfAbsent(airconId) { Mutex() }
        if (!lock.tryLock()) return false
        try {
            val removed = identities.remove(airconId)
            if (removed) {
                clients.remove(airconId)
                registered.remove(airconId)
            }
            return removed
        } finally {
            lock.unlock()
        }
    }
}
