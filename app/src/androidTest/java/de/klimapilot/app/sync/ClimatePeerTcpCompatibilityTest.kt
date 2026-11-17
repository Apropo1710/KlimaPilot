package de.klimapilot.app.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ClimatePeerTcpCompatibilityTest {
    @Test
    fun mutuallyTrustedPeersAuthenticateBeforeApplicationData() {
        val identityStore = ClimatePeerIdentityStore()
        val identity = identityStore.getOrCreateIdentity()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val trusted = ClimateTrustedPeerStore(context)
        trusted.trustPeer(identity.publicKeyBase64, "TCP compatibility test")
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        try {
            val serverResult = executor.submit<Boolean> {
                server.accept().use { socket ->
                    ClimatePeerTcp.authenticateAsServer(socket, identityStore, trusted)
                    assertEquals(REQUEST_BYTE, socket.getInputStream().read())
                    socket.getOutputStream().write(RESPONSE_BYTE)
                    socket.getOutputStream().flush()
                    true
                }
            }
            Socket(InetAddress.getByName("127.0.0.1"), server.localPort).use { socket ->
                ClimatePeerTcp.authenticateAsClient(socket, identityStore, trusted)
                socket.getOutputStream().write(REQUEST_BYTE)
                socket.getOutputStream().flush()
                assertEquals(RESPONSE_BYTE, socket.getInputStream().read())
            }
            assertTrue(serverResult.get(20, TimeUnit.SECONDS))
        } finally {
            server.close()
            executor.shutdownNow()
            trusted.revokePeer(identity.peerId)
        }
    }

    @Test
    fun serverRejectsPeerMissingFromItsLocalTrustList() {
        val identityStore = ClimatePeerIdentityStore()
        val identity = identityStore.getOrCreateIdentity()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clientTrust = ClimateTrustedPeerStore(context)
        clientTrust.trustPeer(identity.publicKeyBase64, "TCP rejection test")
        val emptyServerTrust = ClimateTrustedPeerStore(
            context.createDeviceProtectedStorageContext()
        )
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        try {
            val serverResult = executor.submit<Boolean> {
                server.accept().use { socket ->
                    runCatching { ClimatePeerTcp.authenticateAsServer(socket, identityStore, emptyServerTrust) }.isSuccess
                }
            }
            val clientAccepted = runCatching {
                Socket(InetAddress.getByName("127.0.0.1"), server.localPort).use { socket ->
                    ClimatePeerTcp.authenticateAsClient(socket, identityStore, clientTrust)
                }
            }.isSuccess
            assertTrue("Client must receive the server rejection", !clientAccepted)
            assertTrue("Server must reject an untrusted peer", !serverResult.get(20, TimeUnit.SECONDS))
        } finally {
            server.close()
            executor.shutdownNow()
            clientTrust.revokePeer(identity.peerId)
        }
    }

    private companion object {
        const val REQUEST_BYTE = 0x4b
        const val RESPONSE_BYTE = 0x50
    }
}
