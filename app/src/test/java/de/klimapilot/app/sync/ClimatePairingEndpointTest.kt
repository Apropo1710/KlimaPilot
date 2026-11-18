package de.klimapilot.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimatePairingEndpointTest {
    @Test fun acceptsPrivateIpv4AndLocalIpv6() {
        assertTrue(ClimatePairingEndpoint.isAllowed("192.168.1.25", 43210))
        assertTrue(ClimatePairingEndpoint.isAllowed("10.0.0.2", 43210))
        assertTrue(ClimatePairingEndpoint.isAllowed("172.20.1.4", 43210))
        assertTrue(ClimatePairingEndpoint.isAllowed("169.254.1.4", 43210))
        assertTrue(ClimatePairingEndpoint.isAllowed("fd12:3456::1", 43210))
        assertTrue(ClimatePairingEndpoint.isAllowed("fe80::1", 43210))
    }

    @Test fun rejectsPublicAndSpecialAddresses() {
        assertFalse(ClimatePairingEndpoint.isAllowed("8.8.8.8", 43210))
        assertFalse(ClimatePairingEndpoint.isAllowed("127.0.0.1", 43210))
        assertFalse(ClimatePairingEndpoint.isAllowed("0.0.0.0", 43210))
        assertFalse(ClimatePairingEndpoint.isAllowed("224.0.0.1", 43210))
        assertFalse(ClimatePairingEndpoint.isAllowed("::1", 43210))
        assertFalse(ClimatePairingEndpoint.isAllowed("ff02::1", 43210))
    }

    @Test fun rejectsDnsNamesMalformedAddressesAndPorts() {
        assertFalse(ClimatePairingEndpoint.isAllowed("example.com", 43210))
        assertFalse(ClimatePairingEndpoint.isAllowed("192.168.1.999", 43210))
        assertFalse(ClimatePairingEndpoint.isAllowed("192.168.001.2", 43210))
        assertFalse(ClimatePairingEndpoint.isAllowed("192.168.1.2", 0))
        assertFalse(ClimatePairingEndpoint.isAllowed("192.168.1.2", 65536))
    }
}
