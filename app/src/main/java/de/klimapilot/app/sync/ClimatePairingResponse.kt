package de.klimapilot.app.sync

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Signed answer to a scanned invitation.
 *
 * The response binds the responder's key to the inviter's session and nonce. It
 * proves possession of the responder private key and prevents swapping a response
 * between invitation sessions. It does not itself record trust or confirm user intent.
 */
internal class ClimatePairingResponse(
    private val identityStore: ClimatePeerIdentityStore,
    private val random: SecureRandom = SecureRandom()
) {
    data class Response(
        val sessionId: String,
        val invitationPeerId: String,
        val invitationNonceBase64: String,
        val responderPeerId: String,
        val responderPublicKeyBase64: String,
        val responderFingerprint: String,
        val responderNonceBase64: String,
        val createdAtMillis: Long,
        val signatureBase64: String
    ) {
        fun signingBytes(): ByteArray = listOf(
            DOMAIN,
            PROTOCOL_VERSION.toString(),
            sessionId,
            invitationPeerId,
            invitationNonceBase64,
            responderPeerId,
            responderPublicKeyBase64,
            responderFingerprint,
            responderNonceBase64,
            createdAtMillis.toString()
        ).joinToString("\n").toByteArray(StandardCharsets.UTF_8)

        fun toPayload(): String = JSONObject()
            .put("v", PROTOCOL_VERSION)
            .put("sid", sessionId)
            .put("inviter", invitationPeerId)
            .put("inviterNonce", invitationNonceBase64)
            .put("responder", responderPeerId)
            .put("publicKey", responderPublicKeyBase64)
            .put("fingerprint", responderFingerprint)
            .put("nonce", responderNonceBase64)
            .put("created", createdAtMillis)
            .put("signature", signatureBase64)
            .toString()
    }

    fun create(
        invitation: ClimatePairingInvitation.Invitation,
        nowMillis: Long = System.currentTimeMillis()
    ): Response {
        require(nowMillis >= invitation.createdAtMillis - MAX_CLOCK_SKEW_MS)
        require(nowMillis <= invitation.expiresAtMillis) { "Die Pairing-Einladung ist abgelaufen." }
        val identity = identityStore.getOrCreateIdentity()
        val unsigned = Response(
            sessionId = invitation.sessionId,
            invitationPeerId = invitation.peerId,
            invitationNonceBase64 = invitation.nonceBase64,
            responderPeerId = identity.peerId,
            responderPublicKeyBase64 = identity.publicKeyBase64,
            responderFingerprint = identity.fingerprint,
            responderNonceBase64 = Base64.encodeToString(randomBytes(NONCE_BYTES), Base64.NO_WRAP),
            createdAtMillis = nowMillis,
            signatureBase64 = ""
        )
        return unsigned.copy(
            signatureBase64 = Base64.encodeToString(identityStore.sign(unsigned.signingBytes()), Base64.NO_WRAP)
        )
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    companion object {
        private const val PROTOCOL_VERSION = 1
        private const val DOMAIN = "KlimaPilot-Pairing-Response-v1"
        private const val NONCE_BYTES = 32
        private const val MAX_CLOCK_SKEW_MS = 30_000L
        private const val MAX_RESPONSE_CHARS = 4096

        internal fun parseAndVerify(
            payload: String,
            invitation: ClimatePairingInvitation.Invitation,
            nowMillis: Long = System.currentTimeMillis()
        ): Response? {
            if (payload.isBlank() || payload.length > MAX_RESPONSE_CHARS) return null
            return runCatching {
                val json = JSONObject(payload)
                require(json.length() == 10 && json.getInt("v") == PROTOCOL_VERSION)
                val response = Response(
                    sessionId = json.getString("sid"),
                    invitationPeerId = json.getString("inviter"),
                    invitationNonceBase64 = json.getString("inviterNonce"),
                    responderPeerId = json.getString("responder"),
                    responderPublicKeyBase64 = json.getString("publicKey"),
                    responderFingerprint = json.getString("fingerprint"),
                    responderNonceBase64 = json.getString("nonce"),
                    createdAtMillis = json.getLong("created"),
                    signatureBase64 = json.getString("signature")
                )
                require(response.sessionId == invitation.sessionId)
                require(response.invitationPeerId == invitation.peerId)
                require(MessageDigest.isEqual(
                    response.invitationNonceBase64.toByteArray(StandardCharsets.US_ASCII),
                    invitation.nonceBase64.toByteArray(StandardCharsets.US_ASCII)
                ))
                require(response.createdAtMillis >= invitation.createdAtMillis - MAX_CLOCK_SKEW_MS)
                require(response.createdAtMillis <= invitation.expiresAtMillis)
                require(nowMillis <= invitation.expiresAtMillis)
                require(kotlin.math.abs(nowMillis - response.createdAtMillis) <= MAX_CLOCK_SKEW_MS + INVITATION_LIFETIME_MS)
                require(response.responderPeerId.matches(Regex("[a-f0-9]{64}")))
                val responderKey = Base64.decode(response.responderPublicKeyBase64, Base64.NO_WRAP)
                require(responderKey.isNotEmpty() && responderKey.size <= MAX_PUBLIC_KEY_BYTES)
                val digest = MessageDigest.getInstance("SHA-256").digest(responderKey)
                val computedPeerId = digest.toHex()
                require(computedPeerId == response.responderPeerId)
                require(response.responderFingerprint == computedPeerId.chunked(4).joinToString(":"))
                require(Base64.decode(response.invitationNonceBase64, Base64.NO_WRAP).size == NONCE_BYTES)
                require(Base64.decode(response.responderNonceBase64, Base64.NO_WRAP).size == NONCE_BYTES)
                val signature = Base64.decode(response.signatureBase64, Base64.NO_WRAP)
                require(ClimatePeerIdentityStore.verify(
                    response.responderPublicKeyBase64,
                    response.signingBytes(),
                    signature
                ))
                response
            }.getOrNull()
        }

        private const val INVITATION_LIFETIME_MS = 120_000L
        private const val MAX_PUBLIC_KEY_BYTES = 1024
    }
}

private fun ByteArray.toHex(): String =
    joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
