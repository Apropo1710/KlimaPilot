package de.klimapilot.app.sync

import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClimatePairingInvitationTest {
    @Test
    fun acceptsValidSignedInvitationAndRejectsItAfterExpiry() {
        val now = 1_800_000_000_000L
        val payload = signedInvitation(now)
        val parsed = ClimatePairingInvitation.parseAndVerify(payload, now)

        assertNotNull(parsed)
        assertEquals(now + 120_000L, parsed?.expiresAtMillis)
        assertNull(ClimatePairingInvitation.parseAndVerify(payload, now + 120_001L))
    }

    @Test
    fun rejectsTamperedFieldsAndOversizedPayload() {
        val now = 1_800_000_000_000L
        val original = JSONObject(signedInvitation(now))
        original.put("peerId", "0".repeat(64))
        assertNull(ClimatePairingInvitation.parseAndVerify(original.toString(), now))
        assertNull(ClimatePairingInvitation.parseAndVerify("x".repeat(4097), now))
    }

    @Test
    fun invitationPayloadContainsNoProfileOrNetworkAddress() {
        val now = 1_800_000_000_000L
        val json = JSONObject(signedInvitation(now))
        assertTrue(json.has("peerId"))
        assertTrue(json.has("publicKey"))
        assertFalse(json.has("profile"))
        assertFalse(json.has("host"))
        assertFalse(json.has("address"))
    }

    private fun signedInvitation(now: Long): String {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        val publicKey = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
        val peerId = MessageDigest.getInstance("SHA-256").digest(pair.public.encoded)
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        val unsigned = ClimatePairingInvitation.Invitation(
            sessionId = "a1".repeat(16),
            createdAtMillis = now,
            expiresAtMillis = now + 120_000L,
            peerId = peerId,
            publicKeyBase64 = publicKey,
            fingerprint = peerId.chunked(4).joinToString(":"),
            nonceBase64 = Base64.encodeToString(ByteArray(32) { it.toByte() }, Base64.NO_WRAP),
            signatureBase64 = ""
        )
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(pair.private)
            update(unsigned.signingBytes())
            sign()
        }
        return unsigned.copy(
            signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP)
        ).toQrPayload()
    }
}
