package de.klimapilot.app.sync

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Creates QR matrices for the already signed, short-lived pairing payloads. */
internal object ClimatePairingQrCode {
    fun encode(payload: String, size: Int = DEFAULT_SIZE) =
        QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2
            )
        )

    private const val DEFAULT_SIZE = 640
}
