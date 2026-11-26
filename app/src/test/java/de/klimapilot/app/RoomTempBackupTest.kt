package de.klimapilot.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomTempBackupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun deletedReadingLeavesTheCopy() {
        val units = JSONObject().put("a", JSONArray().put(JSONObject().put("id", "1")).put(JSONObject().put("id", "2")))
        java.io.File(context.filesDir, "roomtemp_samples_backup.json").writeText(units.toString())
        assertEquals(2, RoomTempBackup.count(context))
        RoomTempBackup.forget(context, "a", "1")
        assertEquals(1, RoomTempBackup.count(context))
        RoomTempBackup.forget(context, "b", "x")
        assertEquals(1, RoomTempBackup.count(context))
    }

    @Test
    fun shortMessagesForTheCards() {
        EventJournal.init(context)
        assertNull(IoBrokerMessages.authError(arrayOf(true)))
        assertEquals("ioBroker hat die Anmeldung abgelehnt.", IoBrokerMessages.authError(arrayOf(false)))
        assertEquals("ioBroker hat nicht geantwortet.", IoBrokerMessages.authError(arrayOf("timeout: ioBroker antwortet auf authenticate nicht innerhalb von 12 Sekunden")))
        assertEquals("ioBroker hat nicht geantwortet.", IoBrokerMessages.authError(emptyArray()))
        assertEquals("ioBroker nicht erreichbar", IoBrokerMessages.connectError(arrayOf("failed to connect to /192.168.178.54 (port 8084)")))
    }
}
