package com.hippo.ehviewer.ads

import android.app.Application
import com.hippo.unifile.UniFile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class AdPageStoreTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun survivesRestartAndDirectoryMirrorRebuildsLostInternalRecord() {
        val root = temp.newFolder("records")
        val dir = UniFile.fromFile(temp.newFolder("gallery"))!!
        val store = AdPageStore(root)
        store.load(5, "t", 10, dir)
        store.change(5, dir) { it.mark(9).restore(8) }
        assertEquals(setOf(9), AdPageStore(root).load(5, "t", 10, dir).ads)
        File(root, "5.json").delete()
        val rebuilt = AdPageStore(root).load(5, "t", 10, dir)
        assertEquals(setOf(9), rebuilt.ads)
        assertEquals(setOf(8), rebuilt.restored)
    }

    @Test fun onlineDecisionsCanBeMirroredWhenGalleryIsLaterDownloaded() {
        val store = AdPageStore(temp.newFolder())
        store.load(5, "t", 10, null)
        store.change(5, null) { it.mark(9) }
        val dir = temp.newFolder()
        store.change(5, UniFile.fromFile(dir)) { it }
        assertEquals(setOf(9), AdRecord.decode(File(dir, AdPageStore.SIDECAR).readText()).ads)
    }

    @Test fun identityChangeNeverAppliesOldPageDecisions() {
        val store = AdPageStore(temp.newFolder())
        store.load(5, "t", 10, null)
        store.change(5, null) { it.mark(9) }
        assertTrue(store.load(5, "new", 10, null).ads.isEmpty())
        assertTrue(store.load(5, "t", 20, null).ads.isEmpty())
    }

    @Test fun corruptedRecordFailsClosedRatherThanSilentlyLosingTombstones() {
        val root = temp.newFolder()
        File(root, "5.json").writeText("corrupt")
        assertTrue(runCatching { AdPageStore(root).load(5, "t", 10, null) }.isFailure)
    }

    @Test fun failedPrimaryWriteDoesNotPublishDecision() {
        val blocked = temp.newFile()
        val store = AdPageStore(blocked)
        store.load(5, "t", 10, null)
        assertTrue(runCatching { store.change(5, null) { it.mark(9) } }.isFailure)
        assertTrue(store.get(5).ads.isEmpty())
    }
}
