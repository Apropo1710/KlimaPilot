package de.klimapilot.app.sync

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory

@RunWith(AndroidJUnit4::class)
class ClimatePeerTcpCompatibilityTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun mutuallyTrustedPeersAuthenticateBeforeApplicationData() {
        val clientIdentityStore = ClimatePeerIdentityStore("klimapilot.test.client.identity")
        val serverIdentityStore = ClimatePeerIdentityStore("klimapilot.test.server.identity")
        val clientIdentity = clientIdentityStore.getOrCreateIdentity()
        val serverIdentity = serverIdentityStore.getOrCreateIdentity()
        assertNotEquals(clientIdentity.peerId, serverIdentity.peerId)

        val clientTrust = freshTrustStore("tcp_success_client")
        val serverTrust = freshTrustStore("tcp_success_server")
        clientTrust.trustPeer(serverIdentity.publicKeyBase64, "Testserver")
        serverTrust.trustPeer(clientIdentity.publicKeyBase64, "Testclient")

        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        try {
            val serverResult = executor.submit<Boolean> {
                server.accept().use { socket ->
                    ClimatePeerTcp.authenticateAsServer(socket, serverIdentityStore, serverTrust)
                    assertEquals(REQUEST_BYTE, socket.getInputStream().read())
                    socket.getOutputStream().write(RESPONSE_BYTE)
                    socket.getOutputStream().flush()
                    true
                }
            }
            Socket(InetAddress.getByName("127.0.0.1"), server.localPort).use { socket ->
                ClimatePeerTcp.authenticateAsClient(socket, clientIdentityStore, clientTrust)
                socket.getOutputStream().write(REQUEST_BYTE)
                socket.getOutputStream().flush()
                assertEquals(RESPONSE_BYTE, socket.getInputStream().read())
            }
            assertTrue(serverResult.get(20, TimeUnit.SECONDS))
        } finally {
            server.close()
            executor.shutdownNow()
            clientTrust.revokePeer(serverIdentity.peerId)
            serverTrust.revokePeer(clientIdentity.peerId)
        }
    }

    @Test
    fun serverRejectsPeerMissingFromItsLocalTrustList() {
        val clientIdentityStore = ClimatePeerIdentityStore("klimapilot.test.reject.client.identity")
        val serverIdentityStore = ClimatePeerIdentityStore("klimapilot.test.reject.server.identity")
        val clientIdentity = clientIdentityStore.getOrCreateIdentity()
        val serverIdentity = serverIdentityStore.getOrCreateIdentity()
        assertNotEquals(clientIdentity.peerId, serverIdentity.peerId)

        val clientTrust = freshTrustStore("tcp_reject_client")
        val emptyServerTrust = freshTrustStore("tcp_reject_server")
        clientTrust.trustPeer(serverIdentity.publicKeyBase64, "Testserver")
        assertFalse(emptyServerTrust.isTrusted(clientIdentity.peerId, clientIdentity.publicKeyBase64))

        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        try {
            val serverResult = executor.submit<Boolean> {
                server.accept().use { socket ->
                    runCatching {
                        ClimatePeerTcp.authenticateAsServer(socket, serverIdentityStore, emptyServerTrust)
                    }.isSuccess
                }
            }
            val clientAccepted = runCatching {
                Socket(InetAddress.getByName("127.0.0.1"), server.localPort).use { socket ->
                    ClimatePeerTcp.authenticateAsClient(socket, clientIdentityStore, clientTrust)
                }
            }.isSuccess

            assertFalse("Client must receive the server rejection", clientAccepted)
            assertFalse("Server must reject an untrusted peer", serverResult.get(20, TimeUnit.SECONDS))
        } finally {
            server.close()
            executor.shutdownNow()
            clientTrust.revokePeer(serverIdentity.peerId)
        }
    }

    @Test
    fun clientRejectsServerMissingFromItsLocalTrustList() {
        val clientIdentityStore = ClimatePeerIdentityStore("klimapilot.test.client.reject.server.identity")
        val serverIdentityStore = ClimatePeerIdentityStore("klimapilot.test.server.reject.client.identity")
        val clientIdentity = clientIdentityStore.getOrCreateIdentity()
        val serverIdentity = serverIdentityStore.getOrCreateIdentity()
        val emptyClientTrust = freshTrustStore("tcp_reject_server_client")
        val serverTrust = freshTrustStore("tcp_reject_server_server")
        serverTrust.trustPeer(clientIdentity.publicKeyBase64, "Testclient")
        assertFalse(emptyClientTrust.isTrusted(serverIdentity.peerId, serverIdentity.publicKeyBase64))

        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        try {
            val serverResult = executor.submit<Boolean> {
                server.accept().use { socket ->
                    runCatching {
                        ClimatePeerTcp.authenticateAsServer(socket, serverIdentityStore, serverTrust)
                    }.isSuccess
                }
            }
            val clientAccepted = runCatching {
                Socket(InetAddress.getByName("127.0.0.1"), server.localPort).use { socket ->
                    ClimatePeerTcp.authenticateAsClient(socket, clientIdentityStore, emptyClientTrust)
                }
            }.isSuccess
            assertFalse("Client must reject an untrusted server", clientAccepted)
            assertFalse("Server must not complete when client closes after rejection", serverResult.get(20, TimeUnit.SECONDS))
        } finally {
            server.close()
            executor.shutdownNow()
            serverTrust.revokePeer(clientIdentity.peerId)
        }
    }

    @Test
    fun boundedSessionManagerAuthenticatesTrustedPeerAndClosesBothSockets() {
        val clientIdentityStore = ClimatePeerIdentityStore("klimapilot.test.manager.client.identity")
        val serverIdentityStore = ClimatePeerIdentityStore("klimapilot.test.manager.server.identity")
        val clientIdentity = clientIdentityStore.getOrCreateIdentity()
        val serverIdentity = serverIdentityStore.getOrCreateIdentity()
        val clientTrust = freshTrustStore("tcp_manager_client")
        val serverTrust = freshTrustStore("tcp_manager_server")
        clientTrust.trustPeer(serverIdentity.publicKeyBase64, "Testserver")
        serverTrust.trustPeer(clientIdentity.publicKeyBase64, "Testclient")

        val clientManager = ClimatePeerTcpConnectionManager(clientIdentityStore, clientTrust)
        val serverManager = ClimatePeerTcpConnectionManager(serverIdentityStore, serverTrust)
        val listener = ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        val clientFinished = CountDownLatch(1)
        val serverFinished = CountDownLatch(1)
        var clientAccepted = false
        try {
            val serverTask = executor.submit {
                val incoming = listener.accept()
                assertTrue(serverManager.acceptIncoming(incoming) { serverFinished.countDown() })
            }
            val peer = UnverifiedClimatePeer(
                instanceName = "KlimaPilot-01234567",
                hostAddress = "127.0.0.1",
                port = listener.localPort,
                protocolMajor = 1,
                discoveryId = "0123456789abcdef0123456789abcdef",
            )
            assertTrue(clientManager.authenticate(peer, javax.net.SocketFactory.getDefault()) { success, _ ->
                clientAccepted = success
                clientFinished.countDown()
            })
            assertTrue("Client handshake callback timed out", clientFinished.await(15, TimeUnit.SECONDS))
            assertTrue("Server handshake callback timed out", serverFinished.await(15, TimeUnit.SECONDS))
            serverTask.get(5, TimeUnit.SECONDS)
            assertTrue("Both paired identities must authenticate", clientAccepted)
        } finally {
            clientManager.close()
            serverManager.close()
            listener.close()
            executor.shutdownNow()
            clientTrust.revokePeer(serverIdentity.peerId)
            serverTrust.revokePeer(clientIdentity.peerId)
        }
    }

    @Test
    fun clientSocketCreatedDuringShutdownIsClosedBeforeConnect() {
        val identityStore = ClimatePeerIdentityStore("klimapilot.test.shutdown.identity")
        val trustStore = freshTrustStore("tcp_shutdown_race")
        val manager = ClimatePeerTcpConnectionManager(identityStore, trustStore)
        val createEntered = CountDownLatch(1)
        val allowCreateToReturn = CountDownLatch(1)
        val createdSocket = AtomicReference<Socket?>()
        val blockingFactory = object : SocketFactory() {
            override fun createSocket(): Socket {
                createEntered.countDown()
                var released = false
                while (!released) {
                    try {
                        allowCreateToReturn.await()
                        released = true
                    } catch (_: InterruptedException) {
                        // Simulate a SocketFactory that finishes socket creation despite shutdown.
                    }
                }
                return Socket().also(createdSocket::set)
            }

            override fun createSocket(host: String, port: Int): Socket = createSocket()
            override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket = createSocket()
            override fun createSocket(host: InetAddress, port: Int): Socket = createSocket()
            override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket = createSocket()
        }
        val peer = UnverifiedClimatePeer(
            instanceName = "KlimaPilot-01234567",
            hostAddress = "127.0.0.1",
            port = 9,
            protocolMajor = 1,
            discoveryId = "0123456789abcdef0123456789abcdef",
        )
        try {
            assertTrue(manager.authenticate(peer, blockingFactory) { _, _ -> })
            assertTrue("SocketFactory was not entered", createEntered.await(5, TimeUnit.SECONDS))
            manager.close()
            allowCreateToReturn.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (createdSocket.get() == null && System.nanoTime() < deadline) Thread.sleep(10)
            val socket = createdSocket.get()
            assertTrue("SocketFactory did not return its socket", socket != null)
            assertTrue("Socket created after shutdown must be closed before connect", socket!!.isClosed)
        } finally {
            allowCreateToReturn.countDown()
            manager.close()
            trustStore.trustedPeers().forEach { trustStore.revokePeer(it.peerId) }
        }
    }

    private fun freshTrustStore(name: String): ClimateTrustedPeerStore {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        return ClimateTrustedPeerStore(context, name)
    }

    private companion object {
        const val REQUEST_BYTE = 0x4b
        const val RESPONSE_BYTE = 0x50
    }
}
