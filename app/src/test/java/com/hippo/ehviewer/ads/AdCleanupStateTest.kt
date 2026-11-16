package com.hippo.ehviewer.ads

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class AdCleanupStateTest {
    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences("ad-state-test", 0)

    @Before fun clear() {
        prefs.edit().clear().commit()
    }

    @Test fun autoOnlyRecordsDownloadsCompletedWhileEnabled() {
        val state = AdCleanupState(prefs)
        assertFalse(state.acceptDownload(1))
        prefs.edit().putBoolean("ad_auto", true).commit()
        assertTrue(state.acceptDownload(2))
        assertEquals(setOf("2"), AdCleanupState(prefs).pending)
        prefs.edit().putBoolean("ad_auto", false).commit()
        assertFalse(state.acceptDownload(3))
        assertEquals(setOf("2"), state.pending)
    }

    @Test fun pendingMetadataSurvivesRestartAndSuccessfulDecisionRemovesOnlyThatGallery() {
        prefs.edit().putBoolean("ad_auto", true).commit()
        val state = AdCleanupState(prefs)
        state.acceptDownload(2)
        state.acceptDownload(3)
        AdCleanupState(prefs).forget(2)
        assertEquals(setOf("3"), AdCleanupState(prefs).pending)
    }

    @Test fun continueKeepsCursorAndNewBulkRunResetsIt() {
        val state = AdCleanupState(prefs)
        state.beginBulk()
        state.checkpoint(20)
        state.checkpoint(10)
        assertEquals(20L, state.cursor)
        AdCleanupState(prefs).beginBulk()
        assertEquals(20L, state.cursor)
        assertTrue(state.bulkPending)
        state.finishBulk()
        assertFalse(state.bulkPending)
        state.beginBulk()
        assertEquals(-1L, state.cursor)
    }
}
