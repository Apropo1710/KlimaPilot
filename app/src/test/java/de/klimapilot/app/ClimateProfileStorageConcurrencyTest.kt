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
import org.json.JSONObject
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
                }
            }
            val flushes = (0 until 12).map {
                Callable { repositories[it % repositories.size].flushPendingSyncJournal(store) }
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
    fun repeatedJournalFlushIsIdempotent() {
        val repository = ClimateProfileRepository(context)
        val store = ClimateProfileSyncStore(context)
        repository.saveWithPendingSync(profile("replay"), "wfrac:shared-device")

        assertEquals(1, repository.flushPendingSyncJournal(store))
        assertEquals(0, repository.flushPendingSyncJournal(store))
        assertEquals(1, store.pendingEvents().size)
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
