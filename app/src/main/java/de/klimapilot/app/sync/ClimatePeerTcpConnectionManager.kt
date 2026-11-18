package de.klimapilot.app.sync

import android.os.Handler
import android.os.Looper
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.SocketFactory

/**
 * Bounded handshake-only sessions for the local discovery listener.
 * All sockets are closed immediately after authentication; this class has no payload API.
 */
internal class ClimatePeerTcpConnectionManager(
    private val identityStore: ClimatePeerSigner,
    private val trustedPeers: ClimatePeerTrust,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val socketLock = Any()
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()

    /**
     * Registers a socket atomically against close(). If shutdown already started,
     * the newly created socket is closed here and must not be connected or used.
     */
    private fun trackSocket(socket: Socket): Boolean = synchronized(socketLock) {
        if (closed.get()) {
            runCatching { socket.close() }
            false
        } else {
            activeSockets.add(socket)
            true
        }
    }
    private val serverExecutor = ThreadPoolExecutor(
        1, 2, 30L, TimeUnit.SECONDS, ArrayBlockingQueue(4),
        { task -> Thread(task, "KlimaPilot-Peer-Auth-Server").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )
    private val clientExecutor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(4),
        { task -> Thread(task, "KlimaPilot-Peer-Auth-Client").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )

    fun acceptIncoming(socket: Socket, onAuthenticated: () -> Unit): Boolean {
        if (!trackSocket(socket)) return false
        return try {
            serverExecutor.execute {
                var authenticated = false
                try {
                    if (!closed.get()) {
                        ClimatePeerTcp.authenticateAsServer(socket, identityStore, trustedPeers)
                        authenticated = true
                    }
                } catch (_: Exception) {
                    // Unknown, untrusted, or malformed peers are rejected without UI noise.
                } finally {
                    activeSockets.remove(socket)
                    runCatching { socket.close() }
                }
                if (authenticated) mainHandler.post { if (!closed.get()) onAuthenticated() }
            }
            true
        } catch (_: RejectedExecutionException) {
            activeSockets.remove(socket)
            runCatching { socket.close() }
            false
        }
    }

    fun authenticate(
        peer: UnverifiedClimatePeer,
        socketFactory: SocketFactory,
        onResult: (Boolean, String) -> Unit,
    ): Boolean {
        if (closed.get()) return false
        return try {
            clientExecutor.execute {
                var socket: Socket? = null
                var success = false
                var message = "Authentifizierung fehlgeschlagen."
                try {
                    val connectedSocket = socketFactory.createSocket()
                    socket = connectedSocket
                    if (!trackSocket(connectedSocket)) return@execute
                    connectedSocket.connect(InetSocketAddress(peer.hostAddress, peer.port), CONNECT_TIMEOUT_MS)
                    ClimatePeerTcp.authenticateAsClient(connectedSocket, identityStore, trustedPeers)
                    success = true
                    message = "Gekoppeltes Gerät erfolgreich authentifiziert. Verbindung geschlossen; keine Nutzdaten übertragen."
                } catch (error: Exception) {
                    message = error.message ?: message
                } finally {
                    socket?.let {
                        activeSockets.remove(it)
                        runCatching { it.close() }
                    }
                }
                val result = success
                val resultMessage = message
                mainHandler.post { if (!closed.get()) onResult(result, resultMessage) }
            }
            true
        } catch (_: RejectedExecutionException) {
            false
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val socketsToClose = synchronized(socketLock) {
            activeSockets.toList().also { activeSockets.clear() }
        }
        socketsToClose.forEach { runCatching { it.close() } }
        serverExecutor.shutdownNow()
        clientExecutor.shutdownNow()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
    }
}
