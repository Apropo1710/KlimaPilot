package de.klimapilot.app.sync

import java.net.InetAddress
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
}
