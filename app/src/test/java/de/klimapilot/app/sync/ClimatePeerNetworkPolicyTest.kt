package de.klimapilot.app.sync

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimatePeerNetworkPolicyTest {
    private val selectedAddress = InetAddress.getByName("192.168.1.25")

    @Test
    fun keepsDiscoveryWhenSelectedWifiRemainsTheDefault() {
        assertFalse(
            ClimatePeerNetworkPolicy.shouldAbortForDefaultNetwork(
                isCurrentRun = true,
                isSelectedNetwork = true,
                hasWifiTransport = true,
            )
        )
    }

    @Test
    fun keepsDiscoveryWhenMobileDataBecomesTheDefault() {
        assertFalse(
            ClimatePeerNetworkPolicy.shouldAbortForDefaultNetwork(
                isCurrentRun = true,
                isSelectedNetwork = false,
                hasWifiTransport = false,
            )
        )
    }

    @Test
    fun abortsWhenAnotherWifiBecomesTheDefault() {
        assertTrue(
            ClimatePeerNetworkPolicy.shouldAbortForDefaultNetwork(
                isCurrentRun = true,
                isSelectedNetwork = false,
                hasWifiTransport = true,
            )
        )
    }

    @Test
    fun ignoresCallbacksFromAnOldDiscoveryRun() {
        assertFalse(
            ClimatePeerNetworkPolicy.shouldAbortForDefaultNetwork(
                isCurrentRun = false,
                isSelectedNetwork = false,
                hasWifiTransport = true,
            )
        )
    }

    @Test
    fun acceptsUnchangedWifiSnapshotWhenMobileIsDefault() {
        assertTrue(
            ClimatePeerNetworkPolicy.isSelectedWifiSnapshotValid(
                selectedNetworkIsWifi = true,
                currentInterfaceName = "wlan0",
                currentAddress = selectedAddress,
                expectedInterfaceName = "wlan0",
                expectedAddress = selectedAddress,
                defaultNetworkIsDifferentWifi = false,
            )
        )
    }

    @Test
    fun rejectsSnapshotWhenSelectedNetworkIsNoLongerWifi() {
        assertFalse(
            ClimatePeerNetworkPolicy.isSelectedWifiSnapshotValid(
                selectedNetworkIsWifi = false,
                currentInterfaceName = "wlan0",
                currentAddress = selectedAddress,
                expectedInterfaceName = "wlan0",
                expectedAddress = selectedAddress,
                defaultNetworkIsDifferentWifi = false,
            )
        )
    }

    @Test
    fun rejectsSnapshotWhenAnotherWifiIsAlreadyDefault() {
        assertFalse(
            ClimatePeerNetworkPolicy.isSelectedWifiSnapshotValid(
                selectedNetworkIsWifi = true,
                currentInterfaceName = "wlan0",
                currentAddress = selectedAddress,
                expectedInterfaceName = "wlan0",
                expectedAddress = selectedAddress,
                defaultNetworkIsDifferentWifi = true,
            )
        )
    }

    @Test
    fun rejectsSnapshotWhenWifiInterfaceChanged() {
        assertFalse(
            ClimatePeerNetworkPolicy.isSelectedWifiSnapshotValid(
                selectedNetworkIsWifi = true,
                currentInterfaceName = "wlan1",
                currentAddress = selectedAddress,
                expectedInterfaceName = "wlan0",
                expectedAddress = selectedAddress,
                defaultNetworkIsDifferentWifi = false,
            )
        )
    }

    @Test
    fun rejectsSnapshotWhenWifiAddressChanged() {
        assertFalse(
            ClimatePeerNetworkPolicy.isSelectedWifiSnapshotValid(
                selectedNetworkIsWifi = true,
                currentInterfaceName = "wlan0",
                currentAddress = InetAddress.getByName("192.168.1.26"),
                expectedInterfaceName = "wlan0",
                expectedAddress = selectedAddress,
                defaultNetworkIsDifferentWifi = false,
            )
        )
    }
}
