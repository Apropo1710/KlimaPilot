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
        val client = SoftwareSigner()
        val server = SoftwareSigner(tamperSignature = true)
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
    fun rejectsPeerIdThatDoesNotMatchPublicKey() {
        val client = SoftwareSigner()
        val server = SoftwareSigner()
        val wrongIdTrust = ClimatePeerTrust { peerId, publicKey ->
            peerId == "0".repeat(64) && peerId == server.identity.peerId &&
                publicKey == server.identity.publicKeyBase64
        }
        assertFalse(wrongIdTrust.isTrusted(server.identity.peerId, server.identity.publicKeyBase64))
        assertTrue(trust(server).isTrusted(server.identity.peerId, server.identity.publicKeyBase64))
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
                input.readInt() // server public-key field length
                val keyLength = input.readInt()
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
