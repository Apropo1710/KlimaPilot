package de.klimapilot.app.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/**
 * Compatibility test for pinned server-authenticated TLS plus a signed client challenge.
 * TLS does not request a client certificate. The client pins the server's paired identity
 * key and the server authenticates the client against its trusted-peer store.
 */
@RunWith(AndroidJUnit4::class)
class ClimatePeerTlsCompatibilityTest {
    @Test
    fun pinnedTlsAndApplicationLevelPeerAuthenticationComplete() {
        val identityStore = ClimatePeerIdentityStore()
        val identity = identityStore.getOrCreateIdentity()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val trustedPeers = ClimateTrustedPeerStore(context)
        trustedPeers.trustPeer(identity.publicKeyBase64, "TLS compatibility test")

        val serverContext = ClimatePeerTls.createServerContext()
        val clientContext = ClimatePeerTls.createClientContext(identity.publicKeyBase64)
        val server = serverContext.serverSocketFactory
            .createServerSocket(0, 1, InetAddress.getByName("127.0.0.1")) as SSLServerSocket
        server.needClientAuth = false
        server.enabledProtocols = arrayOf("TLSv1.2")

        val executor = Executors.newSingleThreadExecutor()
        try {
            val serverResult = executor.submit<Boolean> {
                server.accept().use { accepted ->
                    val socket = accepted as SSLSocket
                    socket.enabledProtocols = arrayOf("TLSv1.2")
                    socket.soTimeout = SOCKET_TIMEOUT_MS
                    socket.startHandshake()
                    val authenticated = ClimatePeerTls.authenticateClient(socket, trustedPeers)
                    if (authenticated) {
                        assertEquals(REQUEST_BYTE, socket.inputStream.read())
                        socket.outputStream.write(RESPONSE_BYTE)
                        socket.outputStream.flush()
                    }
                    authenticated
                }
            }

            val client = clientContext.socketFactory
                .createSocket(InetAddress.getByName("127.0.0.1"), server.localPort) as SSLSocket
            client.use { socket ->
                socket.enabledProtocols = arrayOf("TLSv1.2")
                socket.soTimeout = SOCKET_TIMEOUT_MS
                socket.startHandshake()
                assertEquals("TLSv1.2", socket.session.protocol)
                ClimatePeerTls.authenticateToServer(socket, identityStore)
                socket.outputStream.write(REQUEST_BYTE)
                socket.outputStream.flush()
                assertEquals(RESPONSE_BYTE, socket.inputStream.read())
            }

            assertTrue("Server rejected the paired client identity", serverResult.get(20, TimeUnit.SECONDS))
        } finally {
            server.close()
            executor.shutdownNow()
            trustedPeers.revokePeer(identity.peerId)
        }
    }

    @Test
    fun clientTrustManagerRejectsDifferentPeerIdentity() {
        val identityStore = ClimatePeerIdentityStore()
        val identity = identityStore.getOrCreateIdentity()
        val other = java.security.KeyPairGenerator.getInstance("EC").run {
            initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }
        val otherBase64 = android.util.Base64.encodeToString(other.public.encoded, android.util.Base64.NO_WRAP)
        val context = ClimatePeerTls.createClientContext(otherBase64)
        val server = ClimatePeerTls.createServerContext().serverSocketFactory
            .createServerSocket(0, 1, InetAddress.getByName("127.0.0.1")) as SSLServerSocket
        val executor = Executors.newSingleThreadExecutor()
        try {
            val serverFuture = executor.submit {
                runCatching {
                    server.accept().use { socket ->
                        (socket as SSLSocket).apply {
                            enabledProtocols = arrayOf("TLSv1.2")
                            startHandshake()
                        }
                    }
                }
            }
            val failure = runCatching {
                (context.socketFactory.createSocket(InetAddress.getByName("127.0.0.1"), server.localPort) as SSLSocket)
                    .use { it.apply { enabledProtocols = arrayOf("TLSv1.2"); startHandshake() } }
            }.exceptionOrNull()
            assertTrue("TLS connection unexpectedly accepted an unpaired server key", failure != null)
            serverFuture.get(20, TimeUnit.SECONDS)
        } finally {
            server.close()
            executor.shutdownNow()
        }
        assertTrue(identity.peerId.isNotBlank())
    }

    private companion object {
        const val REQUEST_BYTE = 0x4b
        const val RESPONSE_BYTE = 0x50
        const val SOCKET_TIMEOUT_MS = 15_000
    }
}
