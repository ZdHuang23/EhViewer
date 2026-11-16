package com.hippo.ehviewer.ads

import android.app.Application
import com.hippo.unifile.UniFile
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class DownloadStorageTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun countsActualFilesRecursivelyAndShrinksAfterDeletion() = runBlocking {
        val root = temp.newFolder()
        val gallery = File(root, "gallery").apply { mkdirs() }
        val ad = File(gallery, "00000002.jpg").apply { writeBytes(ByteArray(3000)) }
        File(gallery, "00000001.jpg").writeBytes(ByteArray(1000))
        File(root, ".nomedia").writeBytes(ByteArray(2))
        val uni = UniFile.fromFile(root)!!
        assertEquals(4002L, DownloadStorage.measure(uni).bytes)
        ad.delete()
        assertEquals(1002L, DownloadStorage.measure(uni).bytes)
        assertEquals(0, DownloadStorage.measure(uni).unknown)
    }
}
