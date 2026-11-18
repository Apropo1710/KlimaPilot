package de.klimapilot.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ClimatePeerTcpJvmTest {
    @Test
    fun softwareSignersAuthenticateMutuallyTrustedPeers() {
        val client = SoftwareSigner()
        val server = SoftwareSigner()
        val clientTrust = trust(server)
        val serverTrust = trust(client)
        val listener = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val executor = Executors.newSingleThreadExecutor()
        try {
            val accepted = executor.submit<Boolean> {
                listener.accept().use {
                    ClimatePeerTcp.authenticateAsServer(it, server, serverTrust)
                    true
                }
            }
            Socket(InetAddress.getLoopbackAddress(), listener.localPort).use {
                ClimatePeerTcp.authenticateAsClient(it, client, clientTrust)
            }
            assertTrue(accepted.get(15, TimeUnit.SECONDS))
        } finally {
            listener.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun rejectsTamperedSignature() {
        val client = SoftwareSigner(tamperSignature = true)
        val server = SoftwareSigner()
        val listener = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val executor = Executors.newSingleThreadExecutor()
        try {
            val serverResult = executor.submit<Boolean> {
                listener.accept().use {
                    runCatching { ClimatePeerTcp.authenticateAsServer(it, server, trust(client)) }.isSuccess
                }
            }
            val clientAccepted = runCatching {
                Socket(InetAddress.getLoopbackAddress(), listener.localPort).use {
                    ClimatePeerTcp.authenticateAsClient(it, client, trust(server))
                }
            }.isSuccess
            assertFalse(clientAccepted)
            assertFalse(serverResult.get(15, TimeUnit.SECONDS))
        } finally {
            listener.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun rejectsPeerIdThatDoesNotMatchPublicKeyEvenIfTrustStoreAcceptsIt() {
        val server = SoftwareSigner()
        val fakePeerId = "0".repeat(64)
        assertFalse(peerIdMatchesPublicKey(fakePeerId, server.identity.publicKeyBase64))
        assertTrue(peerIdMatchesPublicKey(server.identity.peerId, server.identity.publicKeyBase64))
        val listener = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val executor = Executors.newSingleThreadExecutor()
        try {
            val rejected = executor.submit<Boolean> {
                listener.accept().use {
                    runCatching {
                        ClimatePeerTcp.authenticateAsServer(it, SoftwareSigner(),
                            ClimatePeerTrust { _, _ -> true })
                    }.isFailure
                }
            }
            Socket(InetAddress.getLoopbackAddress(), listener.localPort).use { socket ->
                val data = java.io.DataOutputStream(socket.getOutputStream())
                val input = java.io.DataInputStream(socket.getInputStream())
                data.writeInt(0x4B505431)
                data.write(ByteArray(32) { 7 })
                data.flush()
                input.readInt()
                input.readFully(ByteArray(32))
                data.writeInt(fakePeerId.length)
                data.write(fakePeerId.toByteArray(Charsets.US_ASCII))
                val key = server.identity.publicKeyBase64.toByteArray(Charsets.US_ASCII)
                data.writeInt(key.size)
                data.write(key)
                data.flush()
            }
            assertTrue(rejected.get(5, TimeUnit.SECONDS))
        } finally {
            listener.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun rejectsOversizedHandshakeField() {
        val server = SoftwareSigner()
        val listener = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val executor = Executors.newSingleThreadExecutor()
        try {
            val rejected = executor.submit<Boolean> {
                listener.accept().use {
                    runCatching { ClimatePeerTcp.authenticateAsServer(it, server, trust(SoftwareSigner())) }.isFailure
                }
            }
            Socket(InetAddress.getLoopbackAddress(), listener.localPort).use {
                val out = it.getOutputStream()
                val data = java.io.DataOutputStream(out)
                data.writeInt(0x4B505431)
                data.write(ByteArray(32))
                data.flush()
                // Server responds with its nonce, identity, then reads this length.
                val input = java.io.DataInputStream(it.getInputStream())
                input.readInt()
                input.readFully(ByteArray(32))
                input.readInt() // server peer-id field length
                input.readFully(ByteArray(64))
                val keyLength = input.readInt() // server public-key field length
                input.readFully(ByteArray(keyLength))
                data.writeInt(4097)
                data.flush()
            }
            assertTrue(rejected.get(15, TimeUnit.SECONDS))
        } finally {
            listener.close()
            executor.shutdownNow()
        }
    }


    @Test
    fun rejectsTruncatedClientHello() {
        val server = SoftwareSigner()
        val listener = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val executor = Executors.newSingleThreadExecutor()
        try {
            val rejected = executor.submit<Boolean> {
                listener.accept().use {
                    runCatching { ClimatePeerTcp.authenticateAsServer(it, server, trust(SoftwareSigner())) }.isFailure
                }
            }
            Socket(InetAddress.getLoopbackAddress(), listener.localPort).use {
                val data = java.io.DataOutputStream(it.getOutputStream())
                data.writeInt(0x4B505431)
                data.write(byteArrayOf(1, 2, 3, 4))
                data.flush()
            }
            assertTrue(rejected.get(5, TimeUnit.SECONDS))
        } finally {
            listener.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun enforcesOverallHandshakeDeadlineWhenPeerStalls() {
        val client = SoftwareSigner()
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val executor = Executors.newSingleThreadExecutor()
        try {
            val accepted = executor.submit<Boolean> {
                server.accept().use {
                    Thread.sleep(12_000)
                    true
                }
            }
            val started = System.nanoTime()
            val failed = runCatching {
                Socket(InetAddress.getLoopbackAddress(), server.localPort).use {
                    ClimatePeerTcp.authenticateAsClient(it, client, ClimatePeerTrust { _, _ -> false })
                }
            }.isFailure
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            assertTrue("Stalled peer must be rejected", failed)
            assertTrue("Handshake must time out close to its 10s deadline: ${elapsedMs}ms", elapsedMs in 9_000..11_500)
            accepted.get(13, TimeUnit.SECONDS)
        } finally {
            server.close()
            executor.shutdownNow()
        }
    }


    @Test
    fun restoresClientSocketTimeoutAfterSuccessfulHandshake() {
        val client = SoftwareSigner()
        val server = SoftwareSigner()
        val listener = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val executor = Executors.newSingleThreadExecutor()
        try {
            val accepted = executor.submit<Boolean> {
                listener.accept().use {
                    it.soTimeout = 3210
                    ClimatePeerTcp.authenticateAsServer(it, server, trust(client))
                    it.soTimeout == 3210
                }
            }
            Socket(InetAddress.getLoopbackAddress(), listener.localPort).use {
                it.soTimeout = 2345
                ClimatePeerTcp.authenticateAsClient(it, client, trust(server))
                assertTrue("Client timeout must be restored", it.soTimeout == 2345)
            }
            assertTrue("Server timeout must be restored", accepted.get(15, TimeUnit.SECONDS))
        } finally {
            listener.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun totalHandshakeDeadlineStopsSlowDripPeer() {
        val client = SoftwareSigner()
        val listener = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val executor = Executors.newSingleThreadExecutor()
        try {
            val drip = executor.submit<Boolean> {
                listener.accept().use { socket ->
                    val input = java.io.DataInputStream(socket.getInputStream())
                    input.readInt()
                    input.readFully(ByteArray(32))
                    val output = socket.getOutputStream()
                    // Each byte arrives well inside a fresh 10s read timeout,
                    // but the complete 4-byte field exceeds the 10s handshake deadline.
                    repeat(4) {
                        Thread.sleep(3_500)
                        output.write(if (it == 0) 0x4B else if (it == 1) 0x50 else if (it == 2) 0x54 else 0x31)
                        output.flush()
                    }
                    true
                }
            }
            val started = System.nanoTime()
            val failed = runCatching {
                Socket(InetAddress.getLoopbackAddress(), listener.localPort).use {
                    ClimatePeerTcp.authenticateAsClient(it, client, ClimatePeerTrust { _, _ -> false })
                }
            }.isFailure
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            assertTrue("Slow-drip peer must be rejected", failed)
            assertTrue("Total deadline should stop the slow drip around 10s: ${elapsed}ms", elapsed in 9_000..12_500)
            // The server is interrupted in finally after the client enforces its deadline.
        } finally {
            listener.close()
            executor.shutdownNow()
        }
    }

    private fun trust(signer: SoftwareSigner) = ClimatePeerTrust { id, key ->
        id == signer.identity.peerId && key == signer.identity.publicKeyBase64
    }

    private class SoftwareSigner(private val tamperSignature: Boolean = false) : ClimatePeerSigner {
        private val pair: KeyPair = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }
        val identity: ClimatePeerIdentity = run {
            val encoded = pair.public.encoded
            val id = MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") {
                "%02x".format(it.toInt() and 0xff)
            }
            ClimatePeerIdentity(id, Base64.getEncoder().encodeToString(encoded), id.chunked(4).joinToString(":"))
        }

        override fun getOrCreateIdentity() = identity

        override fun verify(publicKeyBase64: String, challenge: ByteArray, signatureBytes: ByteArray): Boolean = runCatching {
        val key = java.security.KeyFactory.getInstance("EC").generatePublic(
            java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64))
        )
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(challenge)
            verify(signatureBytes)
        }
    }.getOrDefault(false)

    override fun sign(challenge: ByteArray): ByteArray {
            val signature = Signature.getInstance("SHA256withECDSA").run {
                initSign(pair.private)
                update(challenge)
                sign()
            }
            if (!tamperSignature) return signature
            return signature.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        }
    }
}
