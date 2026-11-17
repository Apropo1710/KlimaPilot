package de.klimapilot.app.sync

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Test

class ClimatePairingQrCodeTest {
    @Test
    fun encodedPairingPayloadCanBeReadBackExactly() {
        val payload = """{"v":1,"type":"klimapilot-pairing-test","text":"Grüße aus dem lokalen WLAN"}"""
        val matrix = ClimatePairingQrCode.encode(payload, 512)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            val x = index % matrix.width
            val y = index / matrix.width
            if (matrix[x, y]) 0xff000000.toInt() else 0xffffffff.toInt()
        }
        val source = RGBLuminanceSource(matrix.width, matrix.height, pixels)
        val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)))
        assertEquals(payload, decoded.text)
    }
}
