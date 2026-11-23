package de.klimapilot.app.wfrac

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CodecRawFieldsTest {
    private fun blob(vararg segments: Int): String {
        val bytes = ByteArray(21 + 18 + 1) + segments.map { it.toByte() }.toByteArray() + ByteArray(2)
        bytes[21 + 3] = 0x06
        return Base64.getEncoder().encodeToString(bytes)
    }

    @Test
    fun listsStateBytesAndUnknownSegmentsButNotTemperaturesOrEnergy() {
        val fields = Codec.rawFields(blob(0x80, 0x20, 0x40, 0x00, 0x94, 0x10, 0x0a, 0x01, 0x95, 0x30, 0x02, 0x00))
        assertEquals(0x06, fields["K03"])
        assertEquals(0x0002, fields["S9530"])
        assertFalse(fields.containsKey("S8020"))
        assertFalse(fields.containsKey("S9410"))
        assertEquals(21, fields.keys.count { it.startsWith("H") })
    }
}
