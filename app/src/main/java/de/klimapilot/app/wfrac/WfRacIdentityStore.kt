package de.klimapilot.app.wfrac

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A locally trusted WF-RAC module identity and its transport security binding. */
data class WfRacStoredIdentity(
    val airconId: String,
    val host: String,
    val scheme: Scheme,
    val certificatePin: String?,
    val lastVerifiedAt: Long,
)

class WfRacIdentityStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("wf_rac_identities", Context.MODE_PRIVATE)

    fun all(): List<WfRacStoredIdentity> {
        val raw = prefs.getString(KEY_IDENTITIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val parsed = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val airconId = item.getString("airconId").takeIf { it.isNotBlank() }
                    ?: throw SecurityException("Gespeicherte WF-RAC-Geräte-ID ist leer.")
                val host = item.getString("host").takeIf { it.isNotBlank() }
                    ?: throw SecurityException("Gespeicherte WF-RAC-Adresse ist leer.")
                val scheme = Scheme.valueOf(item.getString("scheme"))
                val pin = (item.opt("certificatePin") as? String)?.takeIf { it.isNotBlank() }
                if (scheme == Scheme.HTTPS && pin == null) {
                    throw SecurityException("Für ein gespeichertes HTTPS-Modul fehlt der Zertifikat-Fingerprint.")
                }
                WfRacStoredIdentity(
                    airconId = airconId,
                    host = host,
                    scheme = scheme,
                    certificatePin = pin,
                    lastVerifiedAt = item.optLong("lastVerifiedAt", 0L),
                )
            }
            if (parsed.map { it.airconId }.distinct().size != parsed.size ||
                parsed.map { it.host.lowercase() }.distinct().size != parsed.size) {
                throw SecurityException("Die gespeicherten WF-RAC-Identitäten sind nicht eindeutig.")
            }
            parsed
        } catch (error: Exception) {
            throw SecurityException(
                "Gespeicherte WF-RAC-Vertrauensdaten sind ungültig. Aus Sicherheitsgründen wird kein neues Modul automatisch akzeptiert.",
                error
            )
        }
    }

    fun findByAirconId(airconId: String): WfRacStoredIdentity? =
        all().firstOrNull { it.airconId == airconId }

    fun findByHost(host: String): WfRacStoredIdentity? =
        all().firstOrNull { it.host.equals(host.trim(), ignoreCase = true) }

    fun remove(airconId: String): Boolean {
        val next = all().filterNot { it.airconId == airconId }
        if (next.size == all().size) return true
        val array = JSONArray().apply {
            next.forEach { item ->
                put(JSONObject()
                    .put("airconId", item.airconId)
                    .put("host", item.host)
                    .put("scheme", item.scheme.name)
                    .put("certificatePin", item.certificatePin)
                    .put("lastVerifiedAt", item.lastVerifiedAt))
            }
        }
        return prefs.edit().putString(KEY_IDENTITIES, array.toString()).commit()
    }

    fun save(identity: WfRacStoredIdentity): Boolean {
        if (identity.airconId.isBlank() || identity.host.isBlank()) return false
        val current = all()
        if (current.any { it.airconId != identity.airconId && it.host.equals(identity.host, ignoreCase = true) }) {
            return false
        }
        val next = current.filterNot { it.airconId == identity.airconId } + identity
        val array = JSONArray().apply {
            next.forEach { item ->
                put(JSONObject()
                    .put("airconId", item.airconId)
                    .put("host", item.host)
                    .put("scheme", item.scheme.name)
                    .put("certificatePin", item.certificatePin)
                    .put("lastVerifiedAt", item.lastVerifiedAt))
            }
        }
        return prefs.edit().putString(KEY_IDENTITIES, array.toString()).commit()
    }

    companion object {
        private const val KEY_IDENTITIES = "trusted_wf_rac_identities_v1"
    }
}

data class WfRacConnectionResult(
    val airconId: String,
    val status: UnitStatus,
    val module: RequestTrace.Module?,
    val scheme: Scheme,
    val certificatePin: String?,
)

/**
 * Performs a read-only connection and enforces the identity/pin binding before reading status.
 * A new certificate is accepted only for the very first trusted module. Known modules must
 * retain their scheme and HTTPS SHA-256 fingerprint.
 */
class WfRacConnectionRepository(context: Context) {
    private val appContext = context.applicationContext
    private val settings = appContext.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE)
    val identities = WfRacIdentityStore(appContext)

    suspend fun readStatus(host: String): WfRacConnectionResult {
        val address = host.trim()
        require(address.isNotBlank()) { "IP-Adresse des WLAN-Moduls fehlt." }

        val deviceId = settings.getString("wf_rac_device_id", null) ?: UUID.randomUUID().toString().also {
            settings.edit().putString("wf_rac_device_id", it).apply()
        }
        val knownAtHost = identities.findByHost(address)
        val knownScheme = knownAtHost?.scheme
        val client = WfRacClient(
            host = address,
            deviceId = deviceId,
            scheme = knownScheme,
            certificatePin = knownAtHost?.certificatePin,
        )

        // getDeviceInfo is read-only. It establishes the module identity and observes the TLS pin.
        val airconId = client.getDeviceInfo()
        val actualScheme = client.scheme
            ?: throw SecurityException("Das WF-RAC-Modul hat kein gültiges Verbindungsverfahren gemeldet.")

        if (knownAtHost != null && knownAtHost.airconId != airconId) {
            throw SecurityException(
                "Sicherheitsprüfung abgebrochen: An dieser IP wurde eine andere Klimaanlagen-ID erkannt."
            )
        }

        val knownIdentity = identities.findByAirconId(airconId)
        if (knownIdentity != null) {
            if (knownIdentity.scheme != actualScheme) {
                throw SecurityException(
                    "Sicherheitsprüfung abgebrochen: Das Verbindungsverfahren des bekannten Moduls hat sich geändert."
                )
            }
            if (actualScheme == Scheme.HTTPS && knownIdentity.certificatePin != client.certificatePin) {
                throw SecurityException(
                    "Sicherheitsprüfung abgebrochen: Der HTTPS-Zertifikat-Fingerprint stimmt nicht mit dem gespeicherten Wert überein."
                )
            }
        } else if (identities.all().isNotEmpty()) {
            throw SecurityException(
                "Unbekanntes WF-RAC-Modul erkannt. Es wurde nicht automatisch als vertrauenswürdig gespeichert."
            )
        }

        // No status request is sent until the known identity and, for HTTPS, its fingerprint match.
        val status = client.getStatus(airconId)
        val pin = client.certificatePin
        if (actualScheme == Scheme.HTTPS && pin.isNullOrBlank()) {
            throw SecurityException("Das HTTPS-Zertifikat konnte nicht dauerhaft identifiziert werden.")
        }

        val saved = identities.save(
            WfRacStoredIdentity(
                airconId = airconId,
                host = address,
                scheme = actualScheme,
                certificatePin = pin,
                lastVerifiedAt = System.currentTimeMillis(),
            )
        )
        if (!saved) throw IllegalStateException("Die Geräteidentität konnte nicht dauerhaft gespeichert werden.")

        settings.edit().putString("wf_rac_host", address).apply()
        return WfRacConnectionResult(airconId, status, client.trace.module, actualScheme, pin)
    }
}
