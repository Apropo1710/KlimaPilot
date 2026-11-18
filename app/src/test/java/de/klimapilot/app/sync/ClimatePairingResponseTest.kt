package de.klimapilot.app.sync

import android.util.Base64
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClimatePairingResponseTest {
    @Test
    fun acceptsResponseBoundToInvitationAndRejectsDifferentSession() {
        val now = 1_800_000_000_000L
        val invitation = makeInvitation(now)
        val response = makeResponse(invitation, now)
        assertNotNull(ClimatePairingResponse.parseAndVerify(response.toPayload(), invitation, now))

        val tampered = JSONObject(response.toPayload()).put("sid", "b2".repeat(16))
        assertNull(ClimatePairingResponse.parseAndVerify(tampered.toString(), invitation, now))
    }

    @Test
    fun rejectsChangedNonceAndExpiredInvitation() {
        val now = 1_800_000_000_000L
        val invitation = makeInvitation(now)
        val response = makeResponse(invitation, now)
        val changedNonce = JSONObject(response.toPayload()).put("inviterNonce", Base64.encodeToString(ByteArray(32) { 9 }, Base64.NO_WRAP))
        assertNull(ClimatePairingResponse.parseAndVerify(changedNonce.toString(), invitation, now))
        assertNull(ClimatePairingResponse.parseAndVerify(response.toPayload(), invitation, now + 120_001L))
    }

    private fun makeInvitation(now: Long): ClimatePairingInvitation.Invitation {
        val pair = newPair()
        val key = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
        val peerId = pair.public.encoded.sha256Hex()
        val unsigned = ClimatePairingInvitation.Invitation(
            "a1".repeat(16), now, now + 120_000L, peerId, key,
            peerId.chunked(4).joinToString(":"),
            Base64.encodeToString(ByteArray(32) { it.toByte() }, Base64.NO_WRAP),
            pairingHost = "192.168.1.10",
            pairingPort = 43210,
            signatureBase64 = ""
        )
        val signature = sign(pair, unsigned.signingBytes())
        return unsigned.copy(signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP))
    }

    private fun makeResponse(
        invitation: ClimatePairingInvitation.Invitation,
        now: Long
    ): ClimatePairingResponse.Response {
        val pair = newPair()
        val key = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
        val peerId = pair.public.encoded.sha256Hex()
        val unsigned = ClimatePairingResponse.Response(
            invitation.sessionId, invitation.peerId, invitation.nonceBase64,
            peerId, key, peerId.chunked(4).joinToString(":"),
            Base64.encodeToString(ByteArray(32) { (it + 3).toByte() }, Base64.NO_WRAP),
            now, ""
        )
        return unsigned.copy(signatureBase64 = Base64.encodeToString(sign(pair, unsigned.signingBytes()), Base64.NO_WRAP))
    }

    private fun newPair(): KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    private fun sign(pair: KeyPair, bytes: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(pair.private)
            update(bytes)
            sign()
        }

    private fun ByteArray.sha256Hex(): String =
        MessageDigest.getInstance("SHA-256").digest(this)
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
