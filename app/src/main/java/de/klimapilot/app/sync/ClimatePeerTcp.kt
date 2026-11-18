package de.klimapilot.app.sync

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
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
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val HANDSHAKE_TIMEOUT_MS = 10_000
    private val TRANSCRIPT_LABEL = "KlimaPilot.LocalPeerAuth.v1\u0000".toByteArray(Charsets.US_ASCII)

    /**
     * Opens a bounded connection and authenticates it. The returned socket is
     * authenticated and may then be used by the caller for application data.
     */
    fun connectAndAuthenticate(
        host: String,
        port: Int,
        identityStore: ClimatePeerSigner,
        trustedPeers: ClimatePeerTrust
    ): Socket {
        require(port in 1..65535) { "Ungültiger KlimaPilot-Port." }
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            authenticateAsClient(socket, identityStore, trustedPeers)
            return socket
        } catch (error: Exception) {
            runCatching { socket.close() }
            throw error
        }
    }

    fun authenticateAsClient(socket: Socket, identityStore: ClimatePeerIdentityStore, trustedPeers: ClimateTrustedPeerStore) {
        val deadline = deadlineAfter(HANDSHAKE_TIMEOUT_MS)
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        output.writeInt(MAGIC)
        val clientNonce = randomNonce()
        output.write(clientNonce)
        output.flush()
        require(readInt(input, socket, deadline) == MAGIC) { "Ungültige KlimaPilot-Gegenstelle." }
        val serverNonce = readBytes(input, socket, deadline, NONCE_BYTES)
        exchangeAndVerify(input, output, socket, deadline, identityStore, trustedPeers,
            CLIENT_ROLE, clientNonce, serverNonce)
        require(readByte(input, socket, deadline) == 1) { "Die Gegenstelle hat dieses Gerät nicht akzeptiert." }
    }

    fun authenticateAsServer(socket: Socket, identityStore: ClimatePeerIdentityStore, trustedPeers: ClimateTrustedPeerStore) {
        val deadline = deadlineAfter(HANDSHAKE_TIMEOUT_MS)
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        require(readInt(input, socket, deadline) == MAGIC) { "Ungültige KlimaPilot-Gegenstelle." }
        val clientNonce = readBytes(input, socket, deadline, NONCE_BYTES)
        val serverNonce = randomNonce()
        output.writeInt(MAGIC)
        output.write(serverNonce)
        output.flush()
        try {
            exchangeAndVerify(input, output, socket, deadline, identityStore, trustedPeers,
                SERVER_ROLE, serverNonce, clientNonce)
            output.writeByte(1)
            output.flush()
        } catch (error: Exception) {
            runCatching { output.writeByte(0); output.flush() }
            throw error
        }
    }

    private fun exchangeAndVerify(
        input: DataInputStream,
        output: DataOutputStream,
        socket: Socket,
        deadline: Long,
        identityStore: ClimatePeerIdentityStore,
        trustedPeers: ClimateTrustedPeerStore,
        localRole: Int,
        localFirstNonce: ByteArray,
        localSecondNonce: ByteArray
    ) {
        val identity = identityStore.getOrCreateIdentity()
        writeField(output, identity.peerId.toByteArray(Charsets.US_ASCII))
        writeField(output, identity.publicKeyBase64.toByteArray(Charsets.US_ASCII))
        output.flush()

        val peerId = readField(input, socket, deadline).toString(Charsets.US_ASCII)
        val publicKey = readField(input, socket, deadline).toString(Charsets.US_ASCII)
        require(peerId.matches(Regex("[a-f0-9]{64}"))) { "Ungültige Peer-ID." }
        require(publicKey.length in 1..MAX_PUBLIC_KEY_BASE64_CHARS) { "Ungültiger Peer-Schlüssel." }
        require(trustedPeers.isTrusted(peerId, publicKey)) { "Gerät ist auf diesem Smartphone nicht gekoppelt." }

        val clientId = if (localRole == CLIENT_ROLE) identity.peerId else peerId
        val serverId = if (localRole == SERVER_ROLE) identity.peerId else peerId
        val signedTranscript = transcript(
            localRole, localFirstNonce, localSecondNonce, clientId, serverId
        )
        val signature = identityStore.sign(signedTranscript)
        writeField(output, signature)
        output.flush()

        val remoteRole = if (localRole == CLIENT_ROLE) SERVER_ROLE else CLIENT_ROLE
        val remoteTranscript = transcript(
            remoteRole, localSecondNonce, localFirstNonce, clientId, serverId
        )
        val peerSignature = readField(input, socket, deadline)
        require(ClimatePeerIdentityStore.verify(publicKey, remoteTranscript, peerSignature)) {
            "Die Geräteauthentifizierung ist fehlgeschlagen."
        }
    }

    private fun randomNonce(): ByteArray = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)

    private fun transcript(
        role: Int,
        clientNonce: ByteArray,
        serverNonce: ByteArray,
        clientPeerId: String,
        serverPeerId: String
    ): ByteArray {
        require(clientPeerId.matches(Regex("[a-f0-9]{64}")) && serverPeerId.matches(Regex("[a-f0-9]{64}")))
        return ByteBuffer.allocate(
            TRANSCRIPT_LABEL.size + 1 + clientNonce.size + serverNonce.size + 64 + 64
        ).put(TRANSCRIPT_LABEL)
            .put(role.toByte())
            .put(clientNonce)
            .put(serverNonce)
            .put(clientPeerId.toByteArray(Charsets.US_ASCII))
            .put(serverPeerId.toByteArray(Charsets.US_ASCII))
            .array()
    }

    private fun deadlineAfter(timeoutMs: Int): Long = System.nanoTime() + timeoutMs * 1_000_000L

    private fun remainingTimeout(socket: Socket, deadline: Long) {
        val remainingNanos = deadline - System.nanoTime()
        require(remainingNanos > 0) { "Zeitlimit für die Geräteauthentifizierung überschritten." }
        val millis = ((remainingNanos + 999_999L) / 1_000_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        socket.soTimeout = millis.coerceAtLeast(1)
    }

    private fun readByte(input: DataInputStream, socket: Socket, deadline: Long): Int {
        remainingTimeout(socket, deadline)
        return input.readUnsignedByte()
    }

    private fun readInt(input: DataInputStream, socket: Socket, deadline: Long): Int {
        val bytes = readBytes(input, socket, deadline, 4)
        return ByteBuffer.wrap(bytes).int
    }

    private fun readBytes(input: DataInputStream, socket: Socket, deadline: Long, length: Int): ByteArray {
        require(length in 1..MAX_FIELD_BYTES) { "Ungültige Handshake-Feldlänge." }
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            remainingTimeout(socket, deadline)
            val count = input.read(bytes, offset, length - offset)
            if (count < 0) throw java.io.EOFException("Verbindung während der Geräteauthentifizierung beendet.")
            offset += count
        }
        return bytes
    }

    private fun writeField(output: DataOutputStream, value: ByteArray) {
        require(value.isNotEmpty() && value.size <= MAX_FIELD_BYTES) { "Ungültige Handshake-Feldlänge." }
        output.writeInt(value.size)
        output.write(value)
    }

    private fun readField(input: DataInputStream, socket: Socket, deadline: Long): ByteArray {
        val length = readInt(input, socket, deadline)
        require(length in 1..MAX_FIELD_BYTES) { "Ungültige Handshake-Feldlänge." }
        return readBytes(input, socket, deadline, length)
    }
}
