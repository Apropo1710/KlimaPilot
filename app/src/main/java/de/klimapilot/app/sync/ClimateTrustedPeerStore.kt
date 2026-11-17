package de.klimapilot.app.sync

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec

/**
 * Persists only peers that have completed an explicit, mutually confirmed pairing flow.
 *
 * Discovery announcements must never be passed here. The caller is responsible for
 * completing QR/challenge verification and showing the fingerprint confirmation on
 * both devices before calling trustPeer().
 */
internal class ClimateTrustedPeerStore(context: Context) {
    data class TrustedPeer(
        val peerId: String,
        val publicKeyBase64: String,
        val fingerprint: String,
        val displayName: String
    )

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun trustedPeers(): List<TrustedPeer> {
        val array = readArray()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val peer = parsePeer(item) ?: continue
                add(peer)
            }
        }.sortedBy { it.displayName.lowercase() }
    }

    /**
     * Adds/replaces a peer after the pairing protocol has authenticated its key and
     * the user has confirmed the matching fingerprint on both devices.
     */
    @Synchronized
    fun trustPeer(publicKeyBase64: String, displayName: String): TrustedPeer {
        val normalizedName = displayName.trim()
        require(normalizedName.isNotEmpty() && normalizedName.length <= MAX_NAME_LENGTH) {
            "Ungültiger Gerätename."
        }
        val peer = peerFromPublicKey(publicKeyBase64, normalizedName)
        val current = readArray()
        val updated = JSONArray()
        for (index in 0 until current.length()) {
            val item = current.optJSONObject(index) ?: continue
            if (item.optString("peerId") != peer.peerId) updated.put(item)
        }
        updated.put(peer.toJson())
        check(prefs.edit().putString(KEY_PEERS, updated.toString()).commit()) {
            "Gekoppeltes Gerät konnte nicht gespeichert werden."
        }
        return peer
    }

    @Synchronized
    fun revokePeer(peerId: String): Boolean {
        if (!peerId.matches(Regex("[a-f0-9]{64}"))) return false
        val current = readArray()
        val updated = JSONArray()
        var removed = false
        for (index in 0 until current.length()) {
            val item = current.optJSONObject(index) ?: continue
            if (item.optString("peerId") == peerId) removed = true else updated.put(item)
        }
        if (!removed) return false
        return prefs.edit().putString(KEY_PEERS, updated.toString()).commit()
    }

    @Synchronized
    fun isTrusted(peerId: String, publicKeyBase64: String): Boolean {
        val candidate = runCatching { peerFromPublicKey(publicKeyBase64, "peer") }.getOrNull() ?: return false
        if (candidate.peerId != peerId) return false
        return trustedPeers().any { it.peerId == peerId && it.publicKeyBase64 == candidate.publicKeyBase64 }
    }

    private fun peerFromPublicKey(publicKeyBase64: String, displayName: String): TrustedPeer {
        require(publicKeyBase64.length in 1..MAX_PUBLIC_KEY_BASE64_LENGTH) {
            "Ungültiger öffentlicher Geräteschlüssel."
        }
        val encoded = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
        require(encoded.isNotEmpty() && encoded.size <= MAX_PUBLIC_KEY_BYTES) {
            "Ungültige Schlüssellänge."
        }
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
        require(key.algorithm.equals("EC", ignoreCase = true)) { "Nur EC-Geräteschlüssel werden unterstützt." }
        val canonical = Base64.encodeToString(key.encoded, Base64.NO_WRAP)
        val digest = MessageDigest.getInstance("SHA-256").digest(key.encoded)
        val peerId = digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        val fingerprint = peerId.chunked(4).joinToString(":")
        return TrustedPeer(peerId, canonical, fingerprint, displayName)
    }

    private fun parsePeer(item: JSONObject): TrustedPeer? = runCatching {
        val publicKey = item.getString("publicKey")
        val name = item.getString("displayName")
        val peer = peerFromPublicKey(publicKey, name)
        require(peer.peerId == item.getString("peerId"))
        peer
    }.getOrNull()

    private fun readArray(): JSONArray = runCatching {
        JSONArray(prefs.getString(KEY_PEERS, "[]"))
    }.getOrDefault(JSONArray())

    private fun TrustedPeer.toJson(): JSONObject = JSONObject()
        .put("peerId", peerId)
        .put("publicKey", publicKeyBase64)
        .put("displayName", displayName)

    companion object {
        private const val PREFS_NAME = "klimapilot_trusted_peers_v1"
        private const val KEY_PEERS = "trusted_peers"
        private const val MAX_NAME_LENGTH = 48
        private const val MAX_PUBLIC_KEY_BYTES = 1024
        private const val MAX_PUBLIC_KEY_BASE64_LENGTH = 2048
    }
}
