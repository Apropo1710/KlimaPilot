package de.klimapilot.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClimatePeerAnnouncementTest {
    private val validAttributes = mapOf(
        "v" to "1".toByteArray(),
        "id" to "0123456789abcdef0123456789abcdef".toByteArray(),
    )

    @Test
    fun acceptsOnlyMinimalValidAnnouncement() {
        val peer = parse()
        assertEquals(1, peer?.protocolMajor)
        assertEquals("192.168.1.20", peer?.hostAddress)
        assertEquals(49152, peer?.port)
    }

    @Test
    fun rejectsOwnInstanceAndOwnDiscoveryId() {
        assertNull(parse(instance = "KlimaPilot-own1234"))
        assertNull(parse(attributes = validAttributes + ("id" to "ffffffffffffffffffffffffffffffff".toByteArray())))
    }

    @Test
    fun rejectsUnknownProtocolVersion() {
        assertNull(parse(attributes = validAttributes + ("v" to "2".toByteArray())))
    }

    @Test
    fun rejectsMissingOrMalformedDiscoveryId() {
        assertNull(parse(attributes = mapOf("v" to "1".toByteArray())))
        assertNull(parse(attributes = validAttributes + ("id" to "invalid".toByteArray())))
    }

    @Test
    fun rejectsUnexpectedTxtFields() {
        assertNull(parse(attributes = validAttributes + ("name" to "private".toByteArray())))
    }

    @Test
    fun rejectsInvalidAddressAndPort() {
        assertNull(parse(host = null))
        assertNull(parse(host = "127.0.0.1"))
        assertNull(parse(host = "8.8.8.8"))
        assertNull(parse(host = "192.168.1.999"))
        assertNull(parse(host = "printer.local"))
        assertNull(parse(port = 0))
        assertNull(parse(port = 65536))
    }

    @Test
    fun acceptsPrivateIpv4AndLocalIpv6Addresses() {
        assertEquals("10.0.0.5", parse(host = "10.0.0.5")?.hostAddress)
        assertEquals("fe80::1", parse(host = "fe80::1")?.hostAddress)
        assertEquals("fd12::5", parse(host = "fd12::5")?.hostAddress)
    }

    @Test
    fun rejectsUnexpectedInstanceNames() {
        assertNull(parse(instance = "KlimaPilot-peer1234\\n"))
        assertNull(parse(instance = "OtherDevice"))
        assertNull(parse(instance = "KlimaPilot-ABCDEF12"))
    }

    private fun parse(
        instance: String = "KlimaPilot-peer1234",
        host: String? = "192.168.1.20",
        port: Int = 49152,
        attributes: Map<String, ByteArray> = validAttributes,
    ) = ClimatePeerAnnouncement.parse(
        instanceName = instance,
        hostAddress = host,
        port = port,
        attributes = attributes,
        ownInstanceNames = setOf("KlimaPilot-own1234"),
        ownDiscoveryId = "ffffffffffffffffffffffffffffffff",
    )
}
