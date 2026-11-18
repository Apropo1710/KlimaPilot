package de.klimapilot.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IoBrokerAutoSetupTest {
    @Test
    fun normalizesTypicalUserInput() {
        assertEquals("ws://192.168.178.54:8084", IoBrokerAddress.normalize("192.168.178.54"))
        assertEquals("ws://192.168.178.54:8085", IoBrokerAddress.normalize(" 192.168.178.54:8085 "))
        assertEquals("ws://192.168.178.54:8084", IoBrokerAddress.normalize("ws://192.168.178.54:8084/"))
        assertEquals("wss://iobroker.local:8084", IoBrokerAddress.normalize("wss://iobroker.local"))
        assertEquals("ws://[fd00::5]:8084", IoBrokerAddress.normalize("[fd00::5]"))
        assertNull(IoBrokerAddress.normalize(""))
        assertNull(IoBrokerAddress.normalize("http://192.168.178.54"))
        assertNull(IoBrokerAddress.normalize("192.168.178.54:99999"))
        assertNull(IoBrokerAddress.normalize("192.168 .1.2"))
    }

    @Test
    fun savedAddressIsReadFromAllLegacyFormats() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("autosetup-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        assertNull(IoBrokerAddress.saved(prefs))

        prefs.edit().putString("klimapilot_iobroker_address", "ws://10.0.0.7:8084").commit()
        assertEquals("ws://10.0.0.7:8084", IoBrokerAddress.saved(prefs))

        prefs.edit().clear().putString("klimapilot_iobroker_address", "10.0.0.8")
            .putString("klimapilot_iobroker_port", "9000").commit()
        assertEquals("ws://10.0.0.8:9000", IoBrokerAddress.saved(prefs))

        IoBrokerAddress.store(prefs, "10.0.0.9")
        assertEquals("ws://10.0.0.9:8084", prefs.getString("iobroker_server", null))
        assertEquals("10.0.0.9", prefs.getString("klimapilot_iobroker_address", null))
        assertEquals("8084", prefs.getString("klimapilot_iobroker_port", null))
        assertEquals("ws://10.0.0.9:8084", IoBrokerAddress.saved(prefs))
    }

    @Test
    fun subnetScanCoversOwnSlash24WithoutOwnAddress() {
        val hosts = IoBrokerAutoSetup.subnetHosts(byteArrayOf(192.toByte(), 168.toByte(), 178.toByte(), 20), 24)
        assertEquals(253, hosts.size)
        assertEquals("192.168.178.1", hosts.first().hostAddress)
        assertEquals("192.168.178.254", hosts.last().hostAddress)
        assertTrue(hosts.none { it.hostAddress == "192.168.178.20" })
        // Größere Netze werden auf das eigene /24 begrenzt.
        assertEquals(253, IoBrokerAutoSetup.subnetHosts(byteArrayOf(10, 1, 2, 3), 16).size)
    }

    private fun registry(): KlimaPilotAccountRegistry {
        val admin = KlimaPilotAccount.create("admin", "Admin", "sicheresPasswort1".toCharArray(), KlimaPilotAccount.ADMIN)
        val user = KlimaPilotAccount.create("partner", "Partner", "sicheresPasswort2".toCharArray(), KlimaPilotAccount.USER)
        return KlimaPilotAccountRegistry(revision = 3, accounts = listOf(admin, user))
    }

    @Test
    fun setupQrRoundTripKeepsAccountsAndAddress() {
        val now = 1_800_000_000_000L
        val raw = KlimaPilotSetupTransfer.create(registry(), "192.168.178.54", now)
        assertTrue(raw.length < 2_000)
        val parsed = KlimaPilotSetupTransfer.parse(raw, now + 60_000)
        assertNotNull(parsed)
        assertEquals(listOf("admin", "partner"), parsed!!.registry.accounts.map { it.username })
        assertEquals("ws://192.168.178.54:8084", parsed.ioBrokerUrl)
        assertTrue(KlimaPilotAccount.verify(parsed.registry.accounts[0], "sicheresPasswort1".toCharArray()))
    }

    @Test
    fun setupQrRejectsExpiredForeignAndEmptyCodes() {
        val now = 1_800_000_000_000L
        val raw = KlimaPilotSetupTransfer.create(registry(), null, now)
        assertNull(KlimaPilotSetupTransfer.parse(raw, now + KlimaPilotSetupTransfer.VALIDITY_MS + 1))
        assertNull(KlimaPilotSetupTransfer.parse("https://example.com", now))
        assertNull(KlimaPilotSetupTransfer.parse(KlimaPilotSetupTransfer.PREFIX + "{kaputt", now))
        assertNull(KlimaPilotSetupTransfer.parse(KlimaPilotSetupTransfer.create(KlimaPilotAccountRegistry(), null, now), now))
        assertNull(KlimaPilotSetupTransfer.parse(raw, now)!!.ioBrokerUrl)
    }
}
