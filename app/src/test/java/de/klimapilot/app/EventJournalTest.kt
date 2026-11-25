package de.klimapilot.app

import androidx.test.core.app.ApplicationProvider
import de.klimapilot.app.wfrac.AirconState
import de.klimapilot.app.wfrac.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EventJournalTest {
    private val base = AirconState(
        power = true, mode = Mode.HEAT, presetTemp = 20.5, airFlow = 0, windUD = 2, windLR = 2, entrust = false,
        coolHotJudge = false, modelNr = 0, modelNrRaw = 0, vacant = false, selfClean = false,
    )

    @Test
    fun diffNamesOnlyTheChangedValues() {
        assertEquals("", EventJournal.diff(base, base))
        assertEquals("Soll 20,5 → 21,0 °C, Lüfter Auto → Hoch", EventJournal.diff(base, base.copy(presetTemp = 21.0, airFlow = 4)))
        assertEquals("ausgeschaltet", EventJournal.diff(base, base.copy(power = false)))
        assertEquals("Modus Heizen → Kühlen", EventJournal.diff(base, base.copy(mode = Mode.COOL)))
    }

    @Test
    fun entriesAreKeptNewestFirst() {
        EventJournal.init(ApplicationProvider.getApplicationContext())
        EventJournal.clear()
        EventJournal.log("App", "", "geöffnet")
        EventJournal.log("Befehl", "Büro", "Bedienung: eingeschaltet – übernommen")
        val entries = EventJournal.entries()
        assertEquals(2, entries.size)
        assertEquals("Befehl", entries.first().category)
        assertTrue(entries.first().line().contains("Befehl · Büro: Bedienung: eingeschaltet – übernommen"))
    }
}
