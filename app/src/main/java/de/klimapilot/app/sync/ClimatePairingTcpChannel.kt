package de.klimapilot.app.sync

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.net.SocketFactory

/**
 * One-shot, length-prefixed local WLAN return channel for signed pairing messages.
 * The channel transports opaque signed payloads only; it does not establish trust.
 */
internal object ClimatePairingTcpChannel {
    private const val MAX_FRAME_BYTES = 4096
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val LISTENER_TIMEOUT_MS = 120_000

    class Listener internal constructor(
        private val server: ServerSocket,
        val host: String,
        val port: Int,
    ) : AutoCloseable {
        @Volatile private var consumed = false

        /** Accepts exactly one response and returns its signed acknowledgement. */
        @Synchronized
        fun acceptOnce(createAcknowledgement: (String) -> String): String {
            check(!consumed) { "Der Pairing-Rückkanal wurde bereits verwendet." }
            consumed = true
            server.soTimeout = LISTENER_TIMEOUT_MS
            server.use { listener ->
                listener.accept().use { socket ->
                    socket.soTimeout = READ_TIMEOUT_MS
                    val response = readFrame(socket)
                    val acknowledgement = createAcknowledgement(response)
                    writeFrame(socket, acknowledgement)
                    return acknowledgement
                }
            }
        }

        override fun close() {
            runCatching { server.close() }
        }
    }

    fun openListener(address: InetAddress): Listener {
        require(ClimatePairingEndpoint.isAllowed(address.hostAddress.substringBefore('%'), 1)) {
            "Das WLAN besitzt keine zulässige lokale Pairing-Adresse."
        }
        val server = ServerSocket()
        try {
            server.reuseAddress = false
            server.bind(InetSocketAddress(address, 0), 1)
            server.soTimeout = LISTENER_TIMEOUT_MS
            return Listener(server, address.hostAddress, server.localPort)
        } catch (error: Exception) {
            runCatching { server.close() }
            throw error
        }
    }

    /** Sends one signed response over the selected Wi-Fi network and waits for its ACK. */
    fun exchange(
        socketFactory: SocketFactory,
        host: String,
        port: Int,
        responsePayload: String,
    ): String {
        require(ClimatePairingEndpoint.isAllowed(host, port)) {
            "Der Pairing-Endpunkt ist keine zulässige lokale IP-Adresse."
        }
        val socket = socketFactory.createSocket()
        socket.use {
            it.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            it.soTimeout = READ_TIMEOUT_MS
            writeFrame(it, responsePayload)
            return readFrame(it)
        }
    }

    private fun readFrame(socket: Socket): String {
        val input = DataInputStream(socket.getInputStream())
        val length = input.readInt()
        if (length !in 1..MAX_FRAME_BYTES) throw IOException("Ungültige Länge der Pairing-Nachricht.")
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return bytes.toString(Charsets.UTF_8).also {
            if (it.isBlank()) throw IOException("Leere Pairing-Nachricht empfangen.")
        }
    }

    private fun writeFrame(socket: Socket, payload: String) {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > MAX_FRAME_BYTES) {
            throw IOException("Pairing-Nachricht überschreitet die zulässige Größe.")
        }
        val output = DataOutputStream(socket.getOutputStream())
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }
}
