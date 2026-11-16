package com.hippo.ehviewer.ads

import android.app.Application
import com.hippo.ehviewer.client.data.BaseGalleryInfo
import com.hippo.ehviewer.spider.SpiderDen
import com.hippo.ehviewer.spider.SpiderQueen
import com.hippo.unifile.UniFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class AdDownloadGuardTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun tombstonesCountAsPresentInReadAndBulkDownloadModesWithoutImageFiles() {
        val info = BaseGalleryInfo().apply {
            gid = 7
            token = "t"
        }
        val den = SpiderDen(info) { it == 9 }
        assertTrue(9 in den)
        den.downloadDir = UniFile.fromFile(temp.newFolder())!!
        den.mode = SpiderQueen.MODE_DOWNLOAD
        assertTrue(9 in den)
    }

    @Test fun markedPageCannotBeDecodedOrExportedEvenIfAFileWasReintroduced() {
        val dir = temp.newFolder()
        java.io.File(dir, "00000010.jpg").writeText("reintroduced ad")
        val den = SpiderDen(
            BaseGalleryInfo().apply {
                gid = 7
                token = "t"
            },
        ) { it == 9 }
        den.downloadDir = UniFile.fromFile(dir)
        den.mode = SpiderQueen.MODE_DOWNLOAD
        assertNull(den.getImageSource(9))
        assertFalse(den.saveToUniFile(9, UniFile.fromFile(temp.newFile())!!))
    }
}
