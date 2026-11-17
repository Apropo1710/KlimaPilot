package de.klimapilot.app.sync

import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClimateTrustedPeerStoreTest {
    private lateinit var context: Context
    private lateinit var store: ClimateTrustedPeerStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("klimapilot_trusted_peers_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = ClimateTrustedPeerStore(context)
    }

    @Test
    fun trustedPeerPersistsAndIsRecognizedByItsKey() {
        val publicKey = newPublicKey()
        val peer = store.trustPeer(publicKey, "Wohnzimmer")
        val reloaded = ClimateTrustedPeerStore(context)

        assertEquals(peer.peerId, reloaded.trustedPeers().single().peerId)
        assertTrue(reloaded.isTrusted(peer.peerId, publicKey))
        assertFalse(reloaded.isTrusted(peer.peerId, newPublicKey()))
    }

    @Test
    fun revocationRemovesPeerAndUnknownPeerCannotBeRevoked() {
        val peer = store.trustPeer(newPublicKey(), "Tablet")
        assertTrue(store.revokePeer(peer.peerId))
        assertTrue(store.trustedPeers().isEmpty())
        assertFalse(store.isTrusted(peer.peerId, peer.publicKeyBase64))
        assertFalse(store.revokePeer(peer.peerId))
    }

    @Test
    fun invalidKeyAndNameAreRejected() {
        assertFalse(runCatching { store.trustPeer("invalid", "Phone") }.isSuccess)
        assertFalse(runCatching { store.trustPeer(newPublicKey(), " ".repeat(3)) }.isSuccess)
        assertFalse(runCatching { store.trustPeer(newPublicKey(), "x".repeat(49)) }.isSuccess)
    }

    private fun newPublicKey(): String {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return Base64.encodeToString(generator.generateKeyPair().public.encoded, Base64.NO_WRAP)
    }
}
