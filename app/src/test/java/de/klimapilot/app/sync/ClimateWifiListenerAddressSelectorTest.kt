package de.klimapilot.app.sync

import java.net.InetAddress
import java.net.Inet6Address
import java.net.NetworkInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClimateWifiListenerAddressSelectorTest {
    @Test
    fun prefersPrivateIpv4WhenBothAddressFamiliesAreAvailable() {
        val selected = ClimateWifiListenerAddressSelector.select(
            "wlan0",
            listOf(InetAddress.getByName("fd12:3456::5"), InetAddress.getByName("192.168.1.25"))
        )
        assertEquals("192.168.1.25", selected?.hostAddress)
    }

    @Test
    fun fallsBackToIpv6WhenNoIpv4AddressExists() {
        val selected = ClimateWifiListenerAddressSelector.select(
            "wlan0", listOf(InetAddress.getByName("fd12:3456::5"))
        )
        assertEquals("fd12:3456:0:0:0:0:0:5", selected?.hostAddress)
    }

    @Test
    fun rejectsPublicAddressesInsteadOfBindingOutsideTheLocalLan() {
        assertNull(
            ClimateWifiListenerAddressSelector.select(
                "wlan0",
                listOf(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("2001:4860:4860::8888"))
            )
        )
    }

    @Test
    fun acceptsIpv4LinkLocalAsLocalFallback() {
        val selected = ClimateWifiListenerAddressSelector.select(
            "wlan0", listOf(InetAddress.getByName("169.254.10.20"))
        )
        assertEquals("169.254.10.20", selected?.hostAddress)
    }

    @Test
    fun rejectsMissingInterfaceAndNonUsableAddresses() {
        assertNull(ClimateWifiListenerAddressSelector.select(
            null, listOf(InetAddress.getByName("192.168.1.25"))
        ))
        assertNull(ClimateWifiListenerAddressSelector.select(
            "wlan0",
            listOf(
                InetAddress.getByName("0.0.0.0"),
                InetAddress.getByName("127.0.0.1"),
                InetAddress.getByName("224.0.0.1"),
                InetAddress.getByName("::")
            )
        ))
    }
    @Test
    fun scopesIpv6LinkLocalAddressToTheSelectedInterface() {
        val loopback = NetworkInterface.getByName("lo")
        org.junit.Assert.assertNotNull("Linux test runner must expose loopback", loopback)
        val address = InetAddress.getByName("fe80::1234") as Inet6Address

        val scoped = ClimateWifiListenerAddressSelector.select("lo", listOf(address)) as? Inet6Address

        org.junit.Assert.assertNotNull(scoped)
        assertEquals("lo", scoped?.scopedInterface?.name)
        org.junit.Assert.assertTrue((scoped?.scopeId ?: 0) > 0)
    }

    @Test
    fun doesNotScopeNonLinkLocalIpv6Addresses() {
        val address = InetAddress.getByName("fd12:3456::5") as Inet6Address
        assertNull(ClimateWifiListenerAddressSelector.scopeLinkLocalAddress(address, "lo"))
    }

}
