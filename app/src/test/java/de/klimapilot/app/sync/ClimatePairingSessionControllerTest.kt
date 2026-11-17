package de.klimapilot.app.sync

import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClimatePairingSessionControllerTest {
    private lateinit var store: ClimateTrustedPeerStore
    private lateinit var pairA: KeyPair
    private lateinit var pairB: KeyPair
    private lateinit var controllerA: ClimatePairingSessionController
    private lateinit var controllerB: ClimatePairingSessionController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("klimapilot_trusted_peers_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = ClimateTrustedPeerStore(context)
        pairA = newPair()
        pairB = newPair()
        controllerA = controller(pairA, "A")
        controllerB = controller(pairB, "B")
    }

    @Test
    fun peersAreStoredOnlyAfterTheirLocalConfirmationAndSessionCannotBeReused() {
        val now = 1_800_000_000_000L
        val invitationPayload = controllerA.createOutgoingInvitation(now)

        val reviewA = controllerB.prepareIncomingInvitation(invitationPayload, now)
        assertNotNull(reviewA)
        assertTrue(store.trustedPeers().isEmpty())

        val responsePayload = controllerB.confirmIncomingInvitation(
            reviewA!!, "Telefon A", now + 1
        )
        assertEquals(peerId(pairA), store.trustedPeers().single().peerId)

        val reviewB = controllerA.prepareOutgoingResponse(responsePayload, now + 2)
        assertNotNull(reviewB)
        assertEquals(1, store.trustedPeers().size)

        val trustedB = controllerA.confirmOutgoingResponse(reviewB!!, "Telefon B", now + 3)
        assertEquals(peerId(pairB), trustedB.peerId)
        assertEquals(setOf(peerId(pairA), peerId(pairB)), store.trustedPeers().map { it.peerId }.toSet())

        assertNull(controllerA.prepareOutgoingResponse(responsePayload, now + 4))
        assertNull(controllerB.prepareIncomingInvitation(invitationPayload, now + 4))
    }

    @Test
    fun cancellationAndExpiredInvitationDoNotAddTrust() {
        val now = 1_800_000_000_000L
        val invitationPayload = controllerA.createOutgoingInvitation(now)

        val pending = controllerB.prepareIncomingInvitation(invitationPayload, now)
        assertNotNull(pending)
        controllerB.cancel()
        assertTrue(store.trustedPeers().isEmpty())

        assertNull(controllerB.prepareIncomingInvitation(invitationPayload, now + 120_001))
        assertFalse(store.isTrusted(peerId(pairA), publicKey(pairA)))
    }

    @Test
    fun confirmationObjectFromAnotherSessionIsRejected() {
        val now = 1_800_000_000_000L
        val first = controllerA.createOutgoingInvitation(now)
        val pending = controllerB.prepareIncomingInvitation(first, now)!!
        controllerB.cancel()
        controllerA.cancel()

        val second = controllerA.createOutgoingInvitation(now + 10)
        val otherPending = controllerB.prepareIncomingInvitation(second, now + 10)!!
        assertFalse(pending === otherPending)
        assertFalse(runCatching {
            controllerB.confirmIncomingInvitation(pending, "Fremd", now + 11)
        }.isSuccess)
        assertTrue(store.trustedPeers().isEmpty())
    }

    private fun controller(pair: KeyPair, label: String) =
        ClimatePairingSessionController(
            localPeerId = peerId(pair),
            trustedPeerStore = store,
            createInvitation = { now -> signedInvitation(pair, now, label) },
            createResponse = { invitation, now -> signedResponse(pair, invitation, now) }
        )

    private fun signedInvitation(pair: KeyPair, now: Long, label: String): ClimatePairingInvitation.Invitation {
        val key = publicKey(pair)
        val id = peerId(pair)
        val unsigned = ClimatePairingInvitation.Invitation(
            sessionId = if (label == "A") "a1".repeat(16) else "b2".repeat(16),
            createdAtMillis = now,
            expiresAtMillis = now + 120_000L,
            peerId = id,
            publicKeyBase64 = key,
            fingerprint = id.chunked(4).joinToString(":"),
            nonceBase64 = Base64.encodeToString(ByteArray(32) { (it + label.length).toByte() }, Base64.NO_WRAP),
            signatureBase64 = ""
        )
        return unsigned.copy(signatureBase64 = sign(pair, unsigned.signingBytes()))
    }

    private fun signedResponse(
        pair: KeyPair,
        invitation: ClimatePairingInvitation.Invitation,
        now: Long
    ): ClimatePairingResponse.Response {
        val id = peerId(pair)
        val unsigned = ClimatePairingResponse.Response(
            sessionId = invitation.sessionId,
            invitationPeerId = invitation.peerId,
            invitationNonceBase64 = invitation.nonceBase64,
            responderPeerId = id,
            responderPublicKeyBase64 = publicKey(pair),
            responderFingerprint = id.chunked(4).joinToString(":"),
            responderNonceBase64 = Base64.encodeToString(ByteArray(32) { (it + 7).toByte() }, Base64.NO_WRAP),
            createdAtMillis = now,
            signatureBase64 = ""
        )
        return unsigned.copy(signatureBase64 = sign(pair, unsigned.signingBytes()))
    }

    private fun sign(pair: KeyPair, bytes: ByteArray): String =
        Base64.encodeToString(Signature.getInstance("SHA256withECDSA").run {
            initSign(pair.private)
            update(bytes)
            sign()
        }, Base64.NO_WRAP)

    private fun newPair(): KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    private fun publicKey(pair: KeyPair): String =
        Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)

    private fun peerId(pair: KeyPair): String =
        MessageDigest.getInstance("SHA-256").digest(pair.public.encoded)
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
