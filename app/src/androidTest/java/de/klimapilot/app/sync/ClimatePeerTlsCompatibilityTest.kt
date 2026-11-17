package de.klimapilot.app.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Device/emulator test for the existing non-exportable installation key.
 *
 * This is deliberately a local loopback compatibility test, not a production transport.
 * Both TLS endpoints use the same test installation identity; a later two-device test
 * must validate the independent peer identities and their separate trust stores.
 */
@RunWith(AndroidJUnit4::class)
class ClimatePeerTlsCompatibilityTest {
    @Test
    fun androidKeystoreIdentityCompletesMutualTls12Handshake() {
        val identity = ClimatePeerIdentityStore().getOrCreateIdentity()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val certificate = keyStore.getCertificate(KEY_ALIAS) as? X509Certificate
        assertNotNull("Android Keystore identity certificate missing", certificate)
        val peerCertificate = requireNotNull(certificate)

        val actualPeerId = MessageDigest.getInstance("SHA-256")
            .digest(peerCertificate.publicKey.encoded)
            .joinToString("") { "%02x".format(it) }
        assertEquals(identity.peerId, actualPeerId)
        assertNotNull("Keystore private key missing", keyStore.getKey(KEY_ALIAS, null))

        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, null)

        val pinnedTrustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
                checkPinnedCertificate(chain, peerCertificate)
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                checkPinnedCertificate(chain, peerCertificate)
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf(peerCertificate)
        }

        val sslContext = SSLContext.getInstance("TLSv1.2").apply {
            init(keyManagerFactory.keyManagers, arrayOf(pinnedTrustManager), SecureRandom())
        }

        val server = sslContext.serverSocketFactory
            .createServerSocket(0, 1, InetAddress.getByName("127.0.0.1")) as SSLServerSocket
        server.needClientAuth = true
        server.enabledProtocols = arrayOf("TLSv1.2")

        val executor = Executors.newSingleThreadExecutor()
        try {
            val serverResult = executor.submit<String> {
                try {
                    server.accept().use { accepted ->
                        val socket = accepted as SSLSocket
                        socket.enabledProtocols = arrayOf("TLSv1.2")
                        socket.startHandshake()
                        val received = socket.inputStream.read()
                        check(received == REQUEST_BYTE) {
                            "Unexpected client test payload: $received"
                        }
                        socket.outputStream.write(RESPONSE_BYTE)
                        "server verified client certificate and completed TLS 1.2"
                    }
                } catch (failure: Throwable) {
                    throw AssertionError(
                        "TLS server side failed: ${failure.javaClass.name}: ${failure.message}",
                        failure
                    )
                }
            }

            val clientFailure = runCatching {
                val client = sslContext.socketFactory
                    .createSocket(InetAddress.getByName("127.0.0.1"), server.localPort) as SSLSocket
                client.use { socket ->
                    socket.enabledProtocols = arrayOf("TLSv1.2")
                    socket.soTimeout = SOCKET_TIMEOUT_MS
                    socket.startHandshake()
                    socket.outputStream.write(REQUEST_BYTE)
                    assertEquals(RESPONSE_BYTE, socket.inputStream.read())
                    assertEquals("TLSv1.2", socket.session.protocol)
                }
            }.exceptionOrNull()

            val serverOutcome = runCatching { serverResult.get(20, TimeUnit.SECONDS) }
            if (clientFailure != null) {
                serverOutcome.exceptionOrNull()?.let(clientFailure::addSuppressed)
                throw AssertionError(
                    "TLS client side failed; any server-side cause is attached as suppressed exception",
                    clientFailure
                )
            }
            assertEquals(
                "server verified client certificate and completed TLS 1.2",
                serverOutcome.getOrThrow()
            )
        } finally {
            server.close()
            executor.shutdownNow()
        }
    }

    private fun checkPinnedCertificate(
        chain: Array<out X509Certificate>,
        expected: X509Certificate
    ) {
        check(chain.isNotEmpty()) { "Peer did not provide a certificate" }
        val presented = chain.first()
        presented.checkValidity()
        val actual = MessageDigest.getInstance("SHA-256").digest(presented.publicKey.encoded)
        val pinned = MessageDigest.getInstance("SHA-256").digest(expected.publicKey.encoded)
        check(MessageDigest.isEqual(actual, pinned)) { "Peer public key does not match the pinned key" }
        check(ClimatePeerIdentityStore.isSupportedPublicKey(presented.publicKey)) {
            "Peer public key is not a supported secp256r1 key"
        }
    }

    private companion object {
        const val KEY_ALIAS = "de.klimapilot.peer.identity.v1"
        const val REQUEST_BYTE = 0x4b
        const val RESPONSE_BYTE = 0x50
        const val SOCKET_TIMEOUT_MS = 15_000
    }
}
