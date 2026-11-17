package de.klimapilot.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClimateProfileStorageConcurrencyTest {
    private lateinit var context: Context

    @Before
    fun resetPreferences() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("klimapilot_climate_profiles", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("klimapilot_profile_sync", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun independentRepositoriesDoNotLoseConcurrentProfilesOrJournalEntries() {
        val repositories = List(4) { ClimateProfileRepository(context) }
        val executor = Executors.newFixedThreadPool(8)
        try {
            val tasks = (0 until 48).map { index ->
                Callable {
                    repositories[index % repositories.size].saveWithPendingSync(
                        profile("profile-$index"), "wfrac:shared-device"
                    )
                }
            }
            executor.invokeAll(tasks).forEach { it.get(10, TimeUnit.SECONDS) }

            assertEquals(48, repositories.first().load().size)
            val store = ClimateProfileSyncStore(context)
            assertEquals(48, repositories.last().flushPendingSyncJournal(store))
            assertEquals(48, store.pendingEvents().size)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun independentStoresKeepAllConcurrentEventsAndUniqueSequences() {
        val stores = List(4) { ClimateProfileSyncStore(context) }
        val executor = Executors.newFixedThreadPool(8)
        try {
            val tasks = (0 until 64).map { index ->
                Callable {
                    stores[index % stores.size].enqueueDelete("profile-$index", "wfrac:shared-device")
                }
            }
            executor.invokeAll(tasks).forEach { assertTrue(it.get(10, TimeUnit.SECONDS)) }

            val events = stores.first().pendingEvents()
            assertEquals(64, events.size)
            assertEquals(64, events.map { it.optLong("sequence") }.toSet().size)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun journalFlushRacingWithSavesRetainsEveryChange() {
        val repositories = List(3) { ClimateProfileRepository(context) }
        val store = ClimateProfileSyncStore(context)
        val executor = Executors.newFixedThreadPool(6)
        try {
            val saves = (0 until 40).map { index ->
                Callable {
                    repositories[index % repositories.size].saveWithPendingSync(
                        profile("race-$index"), "wfrac:shared-device"
                    )
                    Unit
                }
            }
            val flushes = (0 until 12).map {
                Callable {
                    repositories[it % repositories.size].flushPendingSyncJournal(store)
                    Unit
                }
            }
            executor.invokeAll(saves + flushes).forEach { it.get(10, TimeUnit.SECONDS) }
            repositories.first().flushPendingSyncJournal(store)
            assertEquals(40, repositories.last().load().size)
            assertEquals(40, store.pendingEvents().size)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun uploadAcknowledgementDoesNotDeleteEventRekeyedWhileUploadWasInFlight() {
        val store = ClimateProfileSyncStore(context)
        assertTrue(store.enqueueDelete("profile-1", "wfrac:old"))
        val uploadedSnapshot = store.pendingEvents()
        assertTrue(store.rekeyPendingEvents("wfrac:old", "wfrac:new"))

        assertTrue(store.acknowledgeUploaded(uploadedSnapshot))
        val stillPending = store.pendingEvents()
        assertEquals(1, stillPending.size)
        assertEquals("wfrac:new", stillPending.single().optString("deviceKey"))
    }

    @Test
    fun pendingLocalEditWinsAgainstOlderRemoteEventDuringApply() {
        val repository = ClimateProfileRepository(context)
        val store = ClimateProfileSyncStore(context)
        val client = ClimateProfileSyncClient()
        repository.saveWithPendingSync(profile("conflict"), "wfrac:shared-device")

        val remote = org.json.JSONObject()
            .put("eventId", "remote-install:1")
            .put("type", "upsert")
            .put("deviceKey", "wfrac:shared-device")
            .put("profileId", "conflict")
            .put("installationId", "remote-install")
            .put("sequence", 1L)
            .put("logicalClock", 0L)
            .put("profile", org.json.JSONObject()
                .put("name", "Older remote")
                .put("power", false)
                .put("mode", "HEAT")
                .put("targetTemp", 25.0)
                .put("fanSpeed", 1)
                .put("verticalSwing", 2)
                .put("updatedAt", 1L))

        assertTrue(!client.applyRemoteEventIfNewer(remote, "device-1", store, repository))
        assertEquals("conflict", repository.load().single().id)
        assertEquals("conflict", repository.load().single().name)
        assertEquals(1, store.pendingEvents().size)
    }

    @Test
    fun activeProfileSelectionIsStoredAsOutboxEvent() {
        val store = ClimateProfileSyncStore(context)
        assertTrue(store.saveActiveProfileIdWithSync("device-1", "profile-1", "wfrac:shared-device"))
        val event = store.pendingEvents().single()
        assertEquals("selection", event.optString("type"))
        assertEquals("profile-1", event.optJSONObject("profile")?.optString("activeProfileId"))
    }

    @Test
    fun rekeyCarriesAcknowledgedProfileVersionsToNewDeviceKey() {
        val store = ClimateProfileSyncStore(context)
        assertTrue(store.enqueueInitialProfiles(listOf(profile("profile-p")), "wfrac:old"))
        val initialUpload = store.pendingEvents()
        assertEquals(1, initialUpload.size)
        assertTrue(store.acknowledgeUploaded(initialUpload))

        val acknowledgedOldVersion = org.json.JSONObject()
            .put("eventId", "old-install:45")
            .put("type", "upsert")
            .put("deviceKey", "wfrac:old")
            .put("profileId", "profile-p")
            .put("installationId", "old-install")
            .put("sequence", 45L)
            .put("logicalClock", 45L)
        val existingNewVersion = org.json.JSONObject(acknowledgedOldVersion.toString())
            .put("eventId", "new-install:40")
            .put("deviceKey", "wfrac:new")
            .put("installationId", "new-install")
            .put("sequence", 40L)
            .put("logicalClock", 40L)
        assertTrue(store.recordRemoteEvent(acknowledgedOldVersion))
        assertTrue(store.recordRemoteEvent(existingNewVersion))

        assertTrue(store.rekeyPendingEvents("wfrac:old", "wfrac:new"))

        val olderThanOldVersion = org.json.JSONObject(existingNewVersion.toString())
            .put("eventId", "remote-install:42")
            .put("installationId", "remote-install")
            .put("sequence", 42L)
            .put("logicalClock", 42L)
        assertTrue("The acknowledged old-key version must be carried to the new key.",
            !store.isRemoteEventNewer(olderThanOldVersion))

        val newerThanMergedVersion = org.json.JSONObject(existingNewVersion.toString())
            .put("eventId", "remote-install:46")
            .put("installationId", "remote-install")
            .put("sequence", 46L)
            .put("logicalClock", 46L)
        assertTrue(store.isRemoteEventNewer(newerThanMergedVersion))

        assertTrue(store.enqueueInitialProfiles(listOf(profile("profile-p")), "wfrac:new"))
        assertTrue("The migrated initialization marker must still prevent duplicate initial uploads.",
            store.pendingEvents().isEmpty())
    }

    @Test
    fun rekeyPreservesInitializedDeviceMarker() {
        val store = ClimateProfileSyncStore(context)
        assertTrue(store.enqueueInitialProfiles(listOf(profile("initial")), "wfrac:old"))
        val before = store.pendingEvents().size
        assertTrue(store.rekeyPendingEvents("wfrac:old", "wfrac:new"))
        assertTrue(store.enqueueInitialProfiles(listOf(profile("initial")), "wfrac:new"))
        assertEquals(before, store.pendingEvents().size)
    }

    @Test
    fun repeatedJournalFlushIsIdempotent() {
        val repository = ClimateProfileRepository(context)
        val store = ClimateProfileSyncStore(context)
        repository.saveWithPendingSync(profile("replay"), "wfrac:shared-device")

        assertEquals(1, repository.flushPendingSyncJournal(store))
        assertEquals(0, repository.flushPendingSyncJournal(store))
        assertEquals(1, store.pendingEvents().size)
    }

    @Test
    fun remoteEventReplayBeforeVersionMarkerIsIdempotent() {
        val repository = ClimateProfileRepository(context)
        val store = ClimateProfileSyncStore(context)
        val upsert = org.json.JSONObject()
            .put("eventId", "remote-install:1")
            .put("type", "upsert")
            .put("deviceKey", "wfrac:shared-device")
            .put("profileId", "remote-profile")
            .put("installationId", "remote-install")
            .put("sequence", 1L)
            .put("logicalClock", 1L)
            .put("profile", org.json.JSONObject()
                .put("name", "Remote")
                .put("power", true)
                .put("mode", "COOL")
                .put("targetTemp", 23.0)
                .put("fanSpeed", 2)
                .put("verticalSwing", 1)
                .put("updatedAt", 123L))

        // Simulate process death after applying local data but before committing its version marker.
        repository.applyRemoteEvent(upsert, "local-device")
        assertTrue(store.isRemoteEventNewer(upsert))
        repository.applyRemoteEvent(upsert, "local-device")
        assertEquals(1, repository.load().count { it.id == "remote-profile" })
        assertTrue(store.recordRemoteEvent(upsert))
        assertTrue(!store.isRemoteEventNewer(upsert))

        // A committed marker prevents an already completed event from being applied again.
        if (store.isRemoteEventNewer(upsert)) repository.applyRemoteEvent(upsert, "local-device")
        assertEquals(1, repository.load().count { it.id == "remote-profile" })
    }

    @Test
    fun remoteDeleteReplayAndSelectionReplayAreIdempotent() {
        val repository = ClimateProfileRepository(context)
        val store = ClimateProfileSyncStore(context)
        repository.save(profile("to-delete"))
        val delete = org.json.JSONObject()
            .put("eventId", "remote-install:2")
            .put("type", "delete")
            .put("deviceKey", "wfrac:shared-device")
            .put("profileId", "to-delete")
            .put("installationId", "remote-install")
            .put("sequence", 2L)
            .put("logicalClock", 2L)

        repository.applyRemoteEvent(delete, "local-device")
        repository.applyRemoteEvent(delete, "local-device")
        assertTrue(repository.load().none { it.id == "to-delete" })
        assertTrue(store.recordRemoteEvent(delete))
        assertTrue(!store.isRemoteEventNewer(delete))

        // Selection is an overwrite operation; replay must leave the same selected ID.
        store.saveActiveProfileId("local-device", "remote-profile")
        store.saveActiveProfileId("local-device", "remote-profile")
        assertEquals("remote-profile", store.activeProfileId("local-device"))
    }

    private fun profile(id: String) = ClimateProfile(
        id = id,
        deviceId = "device-1",
        name = id,
        power = true,
        mode = "COOL",
        targetTemp = 22.0,
        fanSpeed = 2,
        verticalSwing = 1,
        horizontalSwing = null,
        threeDAuto = null,
        updatedAt = System.currentTimeMillis()
    )
}
