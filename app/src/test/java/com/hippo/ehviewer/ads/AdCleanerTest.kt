package com.hippo.ehviewer.ads

import android.app.Application
import com.hippo.ehviewer.spider.SpiderInfo
import com.hippo.unifile.UniFile
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class AdCleanerTest {
    @get:Rule val temp = TemporaryFolder()
    private val info = SpiderInfo(50, "t", 10)

    private fun gallery(): File = temp.newFolder().also { dir ->
        (1..10).forEach { File(dir, "%08d.jpg".format(it)).writeText("page $it") }
    }

    @Test fun deletesOnlyTailQrAfterDurableMarkAndKeepsPageNumbering() = runBlocking {
        val dir = gallery()
        val uni = UniFile.fromFile(dir)!!
        val root = temp.newFolder()
        val store = AdPageStore(root)
        val scanned = mutableListOf<String>()
        val changed = mutableListOf<Int>()
        val report = AdCleaner.scan(info, store, uni, { file ->
            scanned.add(file.name!!)
            file.name == "00000010.jpg"
        }, { gid, index ->
            // A fresh process can already read the tombstone before the first deletion.
            assertTrue(index in AdPageStore(root).load(gid, "t", 10, uni).ads)
            val deleted = File(dir, "%08d.jpg".format(index + 1)).delete()
            AdCleaner.Deletion(deleted, true)
        }, { _, index -> changed.add(index) })
        assertEquals(7, scanned.size)
        assertFalse(scanned.contains("00000003.jpg"))
        assertEquals(1, report.removed)
        assertEquals(listOf(9), changed)
        assertTrue(File(dir, "00000009.jpg").exists())
        assertFalse(File(dir, "00000010.jpg").exists())
        assertEquals(10, store.get(50).pages)
        val repeat = AdCleaner.scan(info, store, uni, { error("Already checked") }, { _, _ -> AdCleaner.Deletion(false, true) }, { _, _ -> })
        assertEquals(0, repeat.removed)
    }

    @Test fun interruptedDeletionIsRetriedWithoutQrOrRedownload() = runBlocking {
        val dir = UniFile.fromFile(gallery())!!
        val store = AdPageStore(temp.newFolder())
        store.load(50, "t", 10, dir)
        store.change(50, dir) { it.mark(9).copy(manualComplete = true) }
        val first = AdCleaner.scan(info, store, dir, { error("No scan") }, { _, _ -> AdCleaner.Deletion(false, false) }, { _, _ -> })
        assertEquals(1, first.incomplete)
        assertTrue(9 in store.get(50).ads)
        val second = AdCleaner.scan(info, store, dir, { error("No scan") }, { _, i ->
            assertEquals(9, i)
            AdCleaner.Deletion(true, true)
        }, { _, _ -> })
        assertEquals(1, second.removed)
        assertEquals(0, second.incomplete)
    }

    @Test fun restoredPagesAreProtectedFromLaterAutomaticScans() = runBlocking {
        val dir = UniFile.fromFile(gallery())!!
        val store = AdPageStore(temp.newFolder())
        store.load(50, "t", 10, dir)
        store.change(50, dir) { it.mark(9).restore(9) }
        AdCleaner.scan(info, store, dir, { file ->
            assertNotEquals("00000010.jpg", file.name)
            false
        }, { _, _ -> error("No deletion") }, { _, _ -> })
        assertFalse(9 in store.get(50).ads)
        assertTrue(9 in store.get(50).restored)
    }

    @Test fun missingAndUnreadableImagesRemainUncheckedForRetry() = runBlocking {
        val dir = gallery()
        File(dir, "00000010.jpg").delete()
        val store = AdPageStore(temp.newFolder())
        val result = AdCleaner.scan(info, store, UniFile.fromFile(dir)!!, { null }, { _, _ -> error("No deletion") }, { _, _ -> })
        assertEquals(7, result.incomplete)
        assertTrue(store.get(50).checked.isEmpty())
    }

    @Test fun failedRecordWriteNeverDeletesTheImage() = runBlocking {
        val dir = gallery()
        val store = AdPageStore(temp.newFile())
        var deletes = 0
        try {
            AdCleaner.scan(info, store, UniFile.fromFile(dir)!!, { true }, { _, _ ->
                deletes++
                AdCleaner.Deletion(true, true)
            }, { _, _ -> })
            fail("Expected write failure")
        } catch (_: java.io.IOException) {
            assertEquals(0, deletes)
            assertEquals(10, dir.listFiles()!!.size)
        }
    }

    @Test fun failedDirectoryMirrorRetainsImageAndRetryRepairsItBeforeDeletion() = runBlocking {
        val dir = gallery()
        val uni = UniFile.fromFile(dir)!!
        val root = temp.newFolder()
        val store = AdPageStore(root)
        store.load(50, "t", 10, uni)
        val blocked = File(dir, AdPageStore.SIDECAR).apply { mkdirs() }
        var deletes = 0
        try {
            AdCleaner.scan(info, store, uni, { true }, { _, _ ->
                deletes++
                AdCleaner.Deletion(true, true)
            }, { _, _ -> })
            fail("Expected mirror failure")
        } catch (_: IllegalStateException) {
            assertEquals(0, deletes)
            assertTrue(File(dir, "00000004.jpg").exists())
            assertTrue(3 in AdPageStore(root).load(50, "t", 10, uni).ads)
        }
        blocked.delete()
        val fresh = AdPageStore(root)
        val result = AdCleaner.scan(info, fresh, uni, { false }, { _, index ->
            val mirror = AdRecord.decode(File(dir, AdPageStore.SIDECAR).readText())
            assertTrue(index in mirror.ads)
            AdCleaner.Deletion(File(dir, "%08d.jpg".format(index + 1)).delete(), true)
        }, { _, _ -> })
        assertEquals(1, result.removed)
        assertFalse(File(dir, "00000004.jpg").exists())
    }

    @Test fun cancellationPropagatesAndNextRunContinuesCheckedPages() = runBlocking {
        val uni = UniFile.fromFile(gallery())!!
        val store = AdPageStore(temp.newFolder())
        var calls = 0
        try {
            AdCleaner.scan(info, store, uni, {
                if (++calls == 3) throw CancellationException("stop")
                false
            }, { _, _ -> error("No deletion") }, { _, _ -> })
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(2, store.get(50).checked.size)
        }
        calls = 0
        AdCleaner.scan(info, store, uni, {
            calls++
            false
        }, { _, _ -> error("No deletion") }, { _, _ -> })
        assertEquals(5, calls)
    }
}
