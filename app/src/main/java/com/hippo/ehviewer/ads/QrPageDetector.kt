package com.hippo.ehviewer.ads

import android.graphics.BitmapFactory
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.hippo.unifile.UniFile
import com.hippo.unifile.openInputStream

object QrPageDetector {
    /** null means unreadable, never a negative detection. Bound decoded memory before allocating. */
    fun detect(file: UniFile): Boolean? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        file.openInputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val bitmap = file.openInputStream().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            val reader = MultiFormatReader().apply {
                setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
            }
            for (luminance in listOf(source, source.invert())) {
                try {
                    reader.decodeWithState(BinaryBitmap(HybridBinarizer(luminance)))
                    return true
                } catch (_: NotFoundException) {
                    // Try inverted codes too.
                } finally {
                    reader.reset()
                }
            }
            return false
        } finally {
            bitmap.recycle()
        }
    }
}
