package de.klimapilot.app.sync

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Creates and validates short-lived, signed QR invitation payloads.
 *
 * A valid invitation proves possession of the advertised installation key. It does
 * NOT pair, trust, or authorize profile transfer. The response/challenge and
 * two-sided confirmation steps must still complete before a peer is trusted.
 */
internal class ClimatePairingInvitation(
    private val identityStore: ClimatePeerIdentityStore,
    private val random: SecureRandom = SecureRandom()
) {
    data class Invitation(
        val sessionId: String,
        val createdAtMillis: Long,
        val expiresAtMillis: Long,
        val peerId: String,
        val publicKeyBase64: String,
        val fingerprint: String,
        val nonceBase64: String,
        val signatureBase64: String
    ) {
        fun toQrPayload(): String = JSONObject()
            .put("v", PROTOCOL_VERSION)
            .put("sid", sessionId)
            .put("created", createdAtMillis)
            .put("expires", expiresAtMillis)
            .put("peerId", peerId)
            .put("publicKey", publicKeyBase64)
            .put("fingerprint", fingerprint)
            .put("nonce", nonceBase64)
            .put("signature", signatureBase64)
            .toString()

        fun signingBytes(): ByteArray = listOf(
            DOMAIN,
            PROTOCOL_VERSION.toString(),
            sessionId,
            createdAtMillis.toString(),
            expiresAtMillis.toString(),
            peerId,
            publicKeyBase64,
            fingerprint,
            nonceBase64
        ).joinToString("\n").toByteArray(StandardCharsets.UTF_8)
    }

    fun create(nowMillis: Long = System.currentTimeMillis()): Invitation {
        val identity = identityStore.getOrCreateIdentity()
        val sessionId = randomBytes(SESSION_ID_BYTES).toHex()
        val nonce = randomBytes(NONCE_BYTES)
        val expiresAt = nowMillis + INVITATION_LIFETIME_MS
        val unsigned = Invitation(
            sessionId = sessionId,
            createdAtMillis = nowMillis,
            expiresAtMillis = expiresAt,
            peerId = identity.peerId,
            publicKeyBase64 = identity.publicKeyBase64,
            fingerprint = identity.fingerprint,
            nonceBase64 = Base64.encodeToString(nonce, Base64.NO_WRAP),
            signatureBase64 = ""
        )
        val signature = identityStore.sign(unsigned.signingBytes())
        return unsigned.copy(signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP))
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    companion object {
        private const val PROTOCOL_VERSION = 1
        private const val DOMAIN = "KlimaPilot-Pairing-Invitation-v1"
        private const val SESSION_ID_BYTES = 16
        private const val NONCE_BYTES = 32
        private const val INVITATION_LIFETIME_MS = 120_000L
        private const val MAX_CLOCK_SKEW_MS = 30_000L
        private const val MAX_QR_PAYLOAD_CHARS = 4096

        internal fun parseAndVerify(
            payload: String,
            nowMillis: Long = System.currentTimeMillis()
        ): Invitation? {
            if (payload.isBlank() || payload.length > MAX_QR_PAYLOAD_CHARS) return null
            return runCatching {
                val json = JSONObject(payload)
                require(json.length() == 9)
                require(json.getInt("v") == PROTOCOL_VERSION)
                val invitation = Invitation(
                    sessionId = json.getString("sid"),
                    createdAtMillis = json.getLong("created"),
                    expiresAtMillis = json.getLong("expires"),
                    peerId = json.getString("peerId"),
                    publicKeyBase64 = json.getString("publicKey"),
                    fingerprint = json.getString("fingerprint"),
                    nonceBase64 = json.getString("nonce"),
                    signatureBase64 = json.getString("signature")
                )
                require(invitation.sessionId.matches(Regex("[a-f0-9]{32}")))
                require(invitation.peerId.matches(Regex("[a-f0-9]{64}")))
                require(invitation.createdAtMillis > 0L)
                require(invitation.expiresAtMillis - invitation.createdAtMillis == INVITATION_LIFETIME_MS)
                require(nowMillis >= invitation.createdAtMillis - MAX_CLOCK_SKEW_MS)
                require(nowMillis <= invitation.expiresAtMillis)
                val nonce = Base64.decode(invitation.nonceBase64, Base64.NO_WRAP)
                require(nonce.size == NONCE_BYTES)
                val publicKeyBytes = Base64.decode(invitation.publicKeyBase64, Base64.NO_WRAP)
                require(publicKeyBytes.isNotEmpty() && publicKeyBytes.size <= 1024)
                val computedPeerId = MessageDigest.getInstance("SHA-256")
                    .digest(publicKeyBytes).toHex()
                require(MessageDigest.isEqual(
                    computedPeerId.toByteArray(StandardCharsets.US_ASCII),
                    invitation.peerId.toByteArray(StandardCharsets.US_ASCII)
                ))
                require(invitation.fingerprint == computedPeerId.chunked(4).joinToString(":"))
                val signature = Base64.decode(invitation.signatureBase64, Base64.NO_WRAP)
                require(ClimatePeerIdentityStore.verify(
                    invitation.publicKeyBase64,
                    invitation.signingBytes(),
                    signature
                ))
                invitation
            }.getOrNull()
        }

    }
}

private fun ByteArray.toHex(): String =
    joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
