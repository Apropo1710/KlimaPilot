package de.klimapilot.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClimatePeerDataSyncTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        listOf("klimapilot_accounts", "klimapilot_peer_event_log", "klimapilot_profile_sync", "klimapilot_climate_profiles", "klimapilot_settings")
            .forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    private fun registry(revision: Long, vararg names: String) = KlimaPilotAccountRegistry(
        revision = revision,
        accounts = names.map { KlimaPilotAccount.create(it, it, "sicheresPasswort1".toCharArray(), KlimaPilotAccount.ADMIN) }
    )

    private fun event(profileId: String, clock: Long, installation: String = "a", sequence: Long = clock) = JSONObject()
        .put("eventId", "$installation:$sequence").put("installationId", installation).put("sequence", sequence)
        .put("logicalClock", clock).put("type", "delete").put("deviceKey", "wfrac:1").put("profileId", profileId)

    @Test
    fun accountRegistryHigherRevisionWinsAndLowerIsIgnored() {
        val repository = KlimaPilotAccountRepository(context)
        assertTrue(repository.mergeFromPeer(registry(3, "apropo")))
        assertTrue(repository.mergeFromPeer(registry(5, "apropo", "partner")))
        assertFalse(repository.mergeFromPeer(registry(4, "andere")))
        val loaded = repository.load() as KlimaPilotAccountLoadResult.Loaded
        assertEquals(listOf("apropo", "partner"), loaded.registry.accounts.map { it.username })
    }

    @Test
    fun equalRevisionsConvergeDeterministically() {
        val left = registry(7, "eins")
        val right = registry(7, "zwei")
        val phoneA = KlimaPilotAccountRepository(context)
        assertTrue(phoneA.saveInitial(left))
        phoneA.mergeFromPeer(right)
        val resultA = (phoneA.load() as KlimaPilotAccountLoadResult.Loaded).registry

        clear()
        val phoneB = KlimaPilotAccountRepository(context)
        assertTrue(phoneB.saveInitial(right))
        phoneB.mergeFromPeer(left)
        val resultB = (phoneB.load() as KlimaPilotAccountLoadResult.Loaded).registry
        assertEquals(resultA.toJson().toString(), resultB.toJson().toString())
    }

    @Test
    fun eventLogKeepsNewestVersionPerProfile() {
        val log = ClimatePeerEventLog(context)
        assertTrue(log.remember(listOf(event("p1", 2), event("p1", 5), event("p2", 1))))
        assertTrue(log.remember(listOf(event("p1", 3))))
        val byProfile = log.all().associateBy { it.getString("profileId") }
        assertEquals(5L, byProfile.getValue("p1").getLong("logicalClock"))
        assertEquals(2, byProfile.size)
        assertEquals(1, ClimatePeerEventLog.newestPerKey(listOf(event("p3", 1), event("p3", 9), JSONObject())).size)
    }

    @Test
    fun snapshotRoundTripTransfersAccountsAndRemembersEvents() {
        val sender = JSONObject().put("v", 1)
            .put("accounts", registry(2, "apropo").toJson())
            .put("events", JSONArray().put(event("p1", 4, installation = "fremd")))
        val result = ClimatePeerDataSync(context).applySnapshot(sender)
        assertTrue(result.accountsChanged)
        val loaded = KlimaPilotAccountRepository(context).load() as KlimaPilotAccountLoadResult.Loaded
        assertEquals("apropo", loaded.registry.accounts.single().username)
        assertEquals(1, ClimatePeerEventLog(context).all().size)
        // Own snapshot forwards what was learned, including the account registry.
        val own = ClimatePeerDataSync(context).buildSnapshot()
        assertEquals(1, own.getJSONArray("events").length())
        assertTrue(own.has("accounts"))
    }
}
