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
import java.util.concurrent.TimeUnit

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

    private fun freshTrustStore(name: String): ClimateTrustedPeerStore {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        return ClimateTrustedPeerStore(context, name)
    }

    private companion object {
        const val REQUEST_BYTE = 0x4b
        const val RESPONSE_BYTE = 0x50
    }
}
