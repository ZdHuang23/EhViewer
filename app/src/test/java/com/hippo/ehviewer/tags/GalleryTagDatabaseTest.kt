package com.hippo.ehviewer.tags

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = android.app.Application::class)
class GalleryTagDatabaseTest {
    @Test fun refreshPreservesSnapshotUntilSuccessAndReplacesRemovedTags() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), GalleryTagDatabase::class.java).build()
        try {
            val dao = db.tags()
            dao.seed(1, "token")
            dao.save(dao.get(1)!!, listOf(GalleryTag(1, "other", "extraneous ads")))
            dao.seed(1, "token", refresh = true)
            assertTrue(dao.get(1)!!.complete)
            assertEquals(listOf(1L), dao.matching("other", "extraneous ads"))
            dao.put(dao.get(1)!!.copy(status = "unavailable"))
            assertEquals(listOf(1L), dao.matching("other", "extraneous ads"))
            dao.seed(1, "token", retry = true)
            assertEquals("pending", dao.get(1)!!.status)
            dao.save(dao.get(1)!!, emptyList())
            assertTrue(dao.matching("other", "extraneous ads").isEmpty())
            assertEquals(listOf(1L), dao.known())
        } finally {
            db.close()
        }
    }

    @Test fun unavailableIsSkippedAndMissingTokenCanBeRepaired() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), GalleryTagDatabase::class.java).build()
        try {
            val dao = db.tags()
            dao.seed(1, null)
            assertTrue(dao.pending().isEmpty())
            dao.seed(1, "token")
            assertEquals(1, dao.pending().size)
            dao.put(dao.get(1)!!.copy(status = "unavailable"))
            dao.seed(1, "token")
            assertTrue(dao.pending().isEmpty())
            assertFalse(dao.get(1)!!.complete)
        } finally {
            db.close()
        }
    }
}
