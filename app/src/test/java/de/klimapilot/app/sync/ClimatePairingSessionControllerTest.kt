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
    private lateinit var storeA: ClimateTrustedPeerStore
    private lateinit var storeB: ClimateTrustedPeerStore
    private lateinit var pairA: KeyPair
    private lateinit var pairB: KeyPair
    private lateinit var controllerA: ClimatePairingSessionController
    private lateinit var controllerB: ClimatePairingSessionController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("klimapilot_test_peers_a", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("klimapilot_test_peers_b", Context.MODE_PRIVATE)
            .edit().clear().commit()
        storeA = ClimateTrustedPeerStore(context, "klimapilot_test_peers_a")
        storeB = ClimateTrustedPeerStore(context, "klimapilot_test_peers_b")
        pairA = newPair()
        pairB = newPair()
        controllerA = controller(pairA, "A", storeA)
        controllerB = controller(pairB, "B", storeB)
    }

    @Test
    fun peersAreStoredOnlyAfterTheirLocalConfirmationAndSessionCannotBeReused() {
        val now = 1_800_000_000_000L
        val invitationPayload = controllerA.createOutgoingInvitation(now)

        val reviewA = controllerB.prepareIncomingInvitation(invitationPayload, now)
        assertNotNull(reviewA)
        assertTrue(storeA.trustedPeers().isEmpty())
        assertTrue(storeB.trustedPeers().isEmpty())

        val responsePayload = controllerB.confirmIncomingInvitation(
            reviewA!!, "Telefon A", now + 1
        )
        assertEquals(peerId(pairA), storeB.trustedPeers().single().peerId)
        assertTrue(storeA.trustedPeers().isEmpty())

        val reviewB = controllerA.prepareOutgoingResponse(responsePayload, now + 2)
        assertNotNull(reviewB)
        assertTrue(storeA.trustedPeers().isEmpty())

        val trustedB = controllerA.confirmOutgoingResponse(reviewB!!, "Telefon B", now + 3)
        assertEquals(peerId(pairB), trustedB.peerId)
        assertEquals(setOf(peerId(pairA)), storeB.trustedPeers().map { it.peerId }.toSet())
        assertEquals(setOf(peerId(pairB)), storeA.trustedPeers().map { it.peerId }.toSet())

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
        assertTrue(storeA.trustedPeers().isEmpty() && storeB.trustedPeers().isEmpty())
        assertNull(controllerB.prepareIncomingInvitation(invitationPayload, now + 2))

        controllerA.cancel()
        val expiringInvitation = controllerA.createOutgoingInvitation(now + 10)
        assertNull(controllerB.prepareIncomingInvitation(expiringInvitation, now + 130_011))
        assertFalse(storeB.isTrusted(peerId(pairA), publicKey(pairA)))
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
        assertTrue(storeA.trustedPeers().isEmpty() && storeB.trustedPeers().isEmpty())
    }

    private fun controller(pair: KeyPair, label: String, store: ClimateTrustedPeerStore) =
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
            sessionId = now.toString().padStart(32, '0'),
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
