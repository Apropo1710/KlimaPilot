package de.klimapilot.app.sync

import android.util.Base64
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Unencrypted local-WLAN transport handshake.
 *
 * Both peers prove possession of their paired Android Keystore identity and must
 * independently trust the other peer. No application payload may be exchanged before
 * authenticateClient/authenticateServer has returned successfully.
 */
internal object ClimatePeerTcp {
    private const val MAGIC = 0x4B505431 // "KPT1"
    private const val NONCE_BYTES = 32
    private const val MAX_FIELD_BYTES = 4096
    private const val MAX_PUBLIC_KEY_BASE64_CHARS = 2048
    private const val CLIENT_ROLE = 1
    private const val SERVER_ROLE = 2

    fun authenticateAsClient(
        socket: Socket,
        identityStore: ClimatePeerIdentityStore,
        trustedPeers: ClimateTrustedPeerStore
    ) {
        socket.soTimeout = SOCKET_TIMEOUT_MS
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        output.writeInt(MAGIC)
        val clientNonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        output.write(clientNonce)
        output.flush()

        require(input.readInt() == MAGIC) { "Ungültige KlimaPilot-Gegenstelle." }
        val serverNonce = ByteArray(NONCE_BYTES)
        input.readFully(serverNonce)
        exchangeAndVerify(
            input, output, identityStore, trustedPeers,
            sign(identityStore, CLIENT_ROLE, clientNonce, serverNonce),
            CLIENT_ROLE, serverNonce, clientNonce
        )
    }

    fun authenticateAsServer(
        socket: Socket,
        identityStore: ClimatePeerIdentityStore,
        trustedPeers: ClimateTrustedPeerStore
    ) {
        socket.soTimeout = SOCKET_TIMEOUT_MS
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        require(input.readInt() == MAGIC) { "Ungültige KlimaPilot-Gegenstelle." }
        val clientNonce = ByteArray(NONCE_BYTES)
        input.readFully(clientNonce)
        val serverNonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        output.writeInt(MAGIC)
        output.write(serverNonce)
        output.flush()
        exchangeAndVerify(
            input, output, identityStore, trustedPeers,
            sign(identityStore, SERVER_ROLE, serverNonce, clientNonce),
            SERVER_ROLE, clientNonce, serverNonce
        )
    }

    private fun exchangeAndVerify(
        input: DataInputStream,
        output: DataOutputStream,
        identityStore: ClimatePeerIdentityStore,
        trustedPeers: ClimateTrustedPeerStore,
        localProof: Proof,
        localRole: Int,
        remoteFirstNonce: ByteArray,
        remoteSecondNonce: ByteArray
    ) {
        writeField(output, localProof.peerId.toByteArray(Charsets.US_ASCII))
        writeField(output, localProof.publicKeyBase64.toByteArray(Charsets.US_ASCII))
        writeField(output, localProof.signature)
        output.flush()

        val peerId = readField(input).toString(Charsets.US_ASCII)
        val publicKey = readField(input).toString(Charsets.US_ASCII)
        val signature = readField(input)
        require(peerId.matches(Regex("[a-f0-9]{64}"))) { "Ungültige Peer-ID." }
        require(publicKey.length in 1..MAX_PUBLIC_KEY_BASE64_CHARS) { "Ungültiger Peer-Schlüssel." }
        require(trustedPeers.isTrusted(peerId, publicKey)) { "Gerät ist auf diesem Smartphone nicht gekoppelt." }

        val remoteRole = if (localRole == CLIENT_ROLE) SERVER_ROLE else CLIENT_ROLE
        val transcript = transcript(remoteRole, remoteFirstNonce, remoteSecondNonce)
        require(ClimatePeerIdentityStore.verify(publicKey, transcript, signature)) {
            "Die Geräteauthentifizierung ist fehlgeschlagen."
        }
    }

    private data class Proof(val peerId: String, val publicKeyBase64: String, val signature: ByteArray)

    private fun sign(
        identityStore: ClimatePeerIdentityStore,
        role: Int,
        firstNonce: ByteArray,
        secondNonce: ByteArray
    ): Proof {
        val identity = identityStore.getOrCreateIdentity()
        return Proof(identity.peerId, identity.publicKeyBase64,
            identityStore.sign(transcript(role, firstNonce, secondNonce)))
    }

    private fun transcript(role: Int, firstNonce: ByteArray, secondNonce: ByteArray): ByteArray =
        ByteBuffer.allocate(1 + firstNonce.size + secondNonce.size)
            .put(role.toByte()).put(firstNonce).put(secondNonce).array()

    private fun writeField(output: DataOutputStream, value: ByteArray) {
        require(value.isNotEmpty() && value.size <= MAX_FIELD_BYTES) { "Ungültige Handshake-Feldlänge." }
        output.writeInt(value.size)
        output.write(value)
    }

    private fun readField(input: DataInputStream): ByteArray {
        val length = input.readInt()
        require(length in 1..MAX_FIELD_BYTES) { "Ungültige Handshake-Feldlänge." }
        return ByteArray(length).also(input::readFully)
    }

    private const val SOCKET_TIMEOUT_MS = 10_000
}
