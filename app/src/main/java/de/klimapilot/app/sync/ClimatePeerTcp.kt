package de.klimapilot.app.sync

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.security.SecureRandom

/**
 * Unencrypted local-WLAN handshake. Both peers prove possession of their paired
 * Android Keystore identity and independently trust the other peer.
 * No application data is permitted until both functions return successfully.
 */
internal object ClimatePeerTcp {
    private const val MAGIC = 0x4B505431 // "KPT1"
    private const val NONCE_BYTES = 32
    private const val MAX_FIELD_BYTES = 4096
    private const val MAX_PUBLIC_KEY_BASE64_CHARS = 2048
    private const val CLIENT_ROLE = 1
    private const val SERVER_ROLE = 2
    private const val SOCKET_TIMEOUT_MS = 10_000

    fun authenticateAsClient(socket: Socket, identityStore: ClimatePeerIdentityStore, trustedPeers: ClimateTrustedPeerStore) {
        socket.soTimeout = SOCKET_TIMEOUT_MS
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        output.writeInt(MAGIC)
        val clientNonce = randomNonce()
        output.write(clientNonce)
        output.flush()
        require(input.readInt() == MAGIC) { "Ungültige KlimaPilot-Gegenstelle." }
        val serverNonce = ByteArray(NONCE_BYTES).also(input::readFully)
        exchangeAndVerify(input, output, identityStore, trustedPeers, CLIENT_ROLE, clientNonce, serverNonce)
        require(input.readUnsignedByte() == 1) { "Die Gegenstelle hat dieses Gerät nicht akzeptiert." }
    }

    fun authenticateAsServer(socket: Socket, identityStore: ClimatePeerIdentityStore, trustedPeers: ClimateTrustedPeerStore) {
        socket.soTimeout = SOCKET_TIMEOUT_MS
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        require(input.readInt() == MAGIC) { "Ungültige KlimaPilot-Gegenstelle." }
        val clientNonce = ByteArray(NONCE_BYTES).also(input::readFully)
        val serverNonce = randomNonce()
        output.writeInt(MAGIC)
        output.write(serverNonce)
        output.flush()
        try {
            exchangeAndVerify(input, output, identityStore, trustedPeers, SERVER_ROLE, serverNonce, clientNonce)
            output.writeByte(1)
            output.flush()
        } catch (error: Exception) {
            runCatching { output.writeByte(0); output.flush() }
            throw error
        }
    }

    private fun exchangeAndVerify(
        input: DataInputStream, output: DataOutputStream,
        identityStore: ClimatePeerIdentityStore, trustedPeers: ClimateTrustedPeerStore,
        localRole: Int, localFirstNonce: ByteArray, localSecondNonce: ByteArray
    ) {
        val identity = identityStore.getOrCreateIdentity()
        val signature = identityStore.sign(transcript(localRole, localFirstNonce, localSecondNonce))
        writeField(output, identity.peerId.toByteArray(Charsets.US_ASCII))
        writeField(output, identity.publicKeyBase64.toByteArray(Charsets.US_ASCII))
        writeField(output, signature)
        output.flush()

        val peerId = readField(input).toString(Charsets.US_ASCII)
        val publicKey = readField(input).toString(Charsets.US_ASCII)
        val peerSignature = readField(input)
        require(peerId.matches(Regex("[a-f0-9]{64}"))) { "Ungültige Peer-ID." }
        require(publicKey.length in 1..MAX_PUBLIC_KEY_BASE64_CHARS) { "Ungültiger Peer-Schlüssel." }
        require(trustedPeers.isTrusted(peerId, publicKey)) { "Gerät ist auf diesem Smartphone nicht gekoppelt." }
        val remoteRole = if (localRole == CLIENT_ROLE) SERVER_ROLE else CLIENT_ROLE
        require(ClimatePeerIdentityStore.verify(
            publicKey, transcript(remoteRole, localSecondNonce, localFirstNonce), peerSignature
        )) { "Die Geräteauthentifizierung ist fehlgeschlagen." }
    }

    private fun randomNonce(): ByteArray = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
    private fun transcript(role: Int, first: ByteArray, second: ByteArray): ByteArray =
        ByteBuffer.allocate(1 + first.size + second.size).put(role.toByte()).put(first).put(second).array()

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
}
