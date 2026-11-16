package com.hippo.ehviewer.ads

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.hippo.unifile.UniFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QrPageDetectorTest {
    @get:Rule val temp = TemporaryFolder()
    private fun page(qr: Boolean, inverted: Boolean = false): UniFile {
        val bitmap = Bitmap.createBitmap(900, 1300, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(if (inverted) Color.BLACK else Color.WHITE)
        if (qr) {
            val matrix = QRCodeWriter().encode("https://example.org/qr-test", BarcodeFormat.QR_CODE, 220, 220)
            for (x in 0 until 220) {
                for (y in 0 until 220) {
                    val dark = matrix[x, y] != inverted
                    bitmap.setPixel(x + 600, y + 1000, if (dark) Color.BLACK else Color.WHITE)
                }
            }
        }
        val file = temp.newFile()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return UniFile.fromFile(file)!!
    }

    @Test fun detectsSmallQrOnFullPage() {
        assertEquals(true, QrPageDetector.detect(page(true)))
    }

    @Test fun detectsInvertedQr() {
        assertEquals(true, QrPageDetector.detect(page(true, true)))
    }

    @Test fun blankPageIsNotAnAd() {
        assertEquals(false, QrPageDetector.detect(page(false)))
    }

    @Test fun corruptImageIsUnknown() {
        assertNull(QrPageDetector.detect(UniFile.fromFile(temp.newFile())!!))
    }
}
