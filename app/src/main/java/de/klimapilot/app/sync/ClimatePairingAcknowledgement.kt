package de.klimapilot.app.sync

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Signed proof that the inviter accepted and stored the responder for this exact
 * response. The responder verifies it against the public key in the signed invitation.
 */
internal object ClimatePairingAcknowledgement {
    data class Acknowledgement(
        val sessionId: String,
        val inviterPeerId: String,
        val responderPeerId: String,
        val responderNonceBase64: String,
        val createdAtMillis: Long,
        val signatureBase64: String
    ) {
        fun signingBytes(): ByteArray = listOf(
            DOMAIN,
            PROTOCOL_VERSION.toString(),
            sessionId,
            inviterPeerId,
            responderPeerId,
            responderNonceBase64,
            createdAtMillis.toString()
        ).joinToString("\n").toByteArray(StandardCharsets.UTF_8)

        fun toPayload(): String = JSONObject()
            .put("v", PROTOCOL_VERSION)
            .put("sid", sessionId)
            .put("inviter", inviterPeerId)
            .put("responder", responderPeerId)
            .put("responderNonce", responderNonceBase64)
            .put("created", createdAtMillis)
            .put("signature", signatureBase64)
            .toString()
    }

    fun create(
        identityStore: ClimatePeerIdentityStore,
        response: ClimatePairingResponse.Response,
        nowMillis: Long = System.currentTimeMillis()
    ): Acknowledgement {
        require(nowMillis >= response.createdAtMillis - MAX_CLOCK_SKEW_MS)
        require(nowMillis <= response.createdAtMillis + MAX_ACK_AGE_MS)
        val identity = identityStore.getOrCreateIdentity()
        require(identity.peerId == response.invitationPeerId) {
            "Das bestätigende Gerät ist nicht der Einladende."
        }
        val unsigned = Acknowledgement(
            sessionId = response.sessionId,
            inviterPeerId = identity.peerId,
            responderPeerId = response.responderPeerId,
            responderNonceBase64 = response.responderNonceBase64,
            createdAtMillis = nowMillis,
            signatureBase64 = ""
        )
        return unsigned.copy(
            signatureBase64 = Base64.encodeToString(
                identityStore.sign(unsigned.signingBytes()), Base64.NO_WRAP
            )
        )
    }

    fun parseAndVerify(
        payload: String,
        invitation: ClimatePairingInvitation.Invitation,
        response: ClimatePairingResponse.Response,
        nowMillis: Long = System.currentTimeMillis()
    ): Acknowledgement? {
        if (payload.isBlank() || payload.length > MAX_ACK_CHARS) return null
        return runCatching {
            val json = JSONObject(payload)
            require(json.length() == 7 && json.getInt("v") == PROTOCOL_VERSION)
            val ack = Acknowledgement(
                sessionId = json.getString("sid"),
                inviterPeerId = json.getString("inviter"),
                responderPeerId = json.getString("responder"),
                responderNonceBase64 = json.getString("responderNonce"),
                createdAtMillis = json.getLong("created"),
                signatureBase64 = json.getString("signature")
            )
            require(ack.sessionId == invitation.sessionId && ack.sessionId == response.sessionId)
            require(ack.inviterPeerId == invitation.peerId && ack.inviterPeerId == response.invitationPeerId)
            require(ack.responderPeerId == response.responderPeerId)
            require(MessageDigest.isEqual(
                ack.responderNonceBase64.toByteArray(StandardCharsets.US_ASCII),
                response.responderNonceBase64.toByteArray(StandardCharsets.US_ASCII)
            ))
            require(ack.createdAtMillis >= response.createdAtMillis - MAX_CLOCK_SKEW_MS)
            require(ack.createdAtMillis <= response.createdAtMillis + MAX_ACK_AGE_MS)
            require(nowMillis <= invitation.expiresAtMillis)
            require(ack.createdAtMillis <= nowMillis + MAX_CLOCK_SKEW_MS)
            require(ClimatePeerIdentityStore.verify(
                invitation.publicKeyBase64,
                ack.signingBytes(),
                Base64.decode(ack.signatureBase64, Base64.NO_WRAP)
            ))
            ack
        }.getOrNull()
    }

    private const val PROTOCOL_VERSION = 1
    private const val DOMAIN = "KlimaPilot-Pairing-Acknowledgement-v1"
    private const val MAX_ACK_CHARS = 2048
    private const val MAX_CLOCK_SKEW_MS = 30_000L
    private const val MAX_ACK_AGE_MS = 120_000L
}
