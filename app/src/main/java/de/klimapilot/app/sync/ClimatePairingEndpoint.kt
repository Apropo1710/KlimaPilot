package de.klimapilot.app.sync

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/** A QR-advertised endpoint is untrusted input until the invitation signature is verified. */
internal object ClimatePairingEndpoint {
    fun isAllowed(host: String, port: Int): Boolean {
        if (port !in 1..65535 || host.isBlank() || host.length > 64 ||
            host.any { it.isWhitespace() } || host.contains('/') || host.contains('@')) return false
        val address = runCatching { InetAddress.getByName(host.substringBefore('%')) }.getOrNull()
            ?: return false
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isMulticastAddress) return false
        return when (address) {
            is Inet4Address -> {
                val b = address.address.map { it.toInt() and 0xff }
                b.size == 4 && (
                    b[0] == 10 ||
                    b[0] == 192 && b[1] == 168 ||
                    b[0] == 172 && b[1] in 16..31 ||
                    b[0] == 169 && b[1] == 254
                )
            }
            is Inet6Address -> {
                val b0 = address.address[0].toInt() and 0xff
                (b0 and 0xfe) == 0xfc || address.isLinkLocalAddress
            }
            else -> false
        }
    }
}
