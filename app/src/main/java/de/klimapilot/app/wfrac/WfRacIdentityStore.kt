package de.klimapilot.app.wfrac

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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
