package com.hippo.ehviewer.ads

import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.client.getImageKey
import com.hippo.ehviewer.spider.SpiderDen
import com.hippo.ehviewer.spider.SpiderInfo
import com.hippo.ehviewer.spider.SpiderQueen
import com.hippo.unifile.UniFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock

object AdCleaner {
    data class Report(val removed: Int, val incomplete: Int, val manualComplete: Boolean)

    suspend fun scan(
        info: SpiderInfo,
        store: AdPageStore = AdPages.store,
        dir: UniFile = checkNotNull(SpiderDen.getGalleryDownloadDir(info.gid)),
        detect: (UniFile) -> Boolean? = QrPageDetector::detect,
        delete: (Long, Int) -> Deletion = ::deleteImages,
        changed: (Long, Int) -> Unit = SpiderQueen::adChanged,
    ): Report = AdPages.scanLock(info.gid).withLock {
        store.load(info.gid, info.token, info.pages, dir)
        var removed = 0
        var incomplete = 0
        // Always retry pending deletions, including interrupted runs, before considering scanned pages.
        for (index in store.get(info.gid).ads) {
            currentCoroutineContext().ensureActive()
            AdPages.lock(info.gid, index).withLock {
                if (index !in store.get(info.gid).ads) return@withLock
                store.change(info.gid, dir) { it }
                val deletion = delete(info.gid, index)
                if (deletion.removed) removed++
                if (!deletion.complete) incomplete++
            }
        }
        val manualComplete = store.get(info.gid).manualComplete
        if (!manualComplete) {
            for (index in AdRecord.tail(info.pages)) {
                currentCoroutineContext().ensureActive()
                AdPages.lock(info.gid, index).withLock {
                    val record = store.get(info.gid)
                    if (index in record.ads || index in record.restored || index in record.checked) return@withLock
                    val file = SpiderDen.findImageFile(dir, index)
                    val qr = file?.let { detect(it) }
                    currentCoroutineContext().ensureActive()
                    if (qr == null) {
                        incomplete++
                    } else if (qr) {
                        // Persist before removal. A failed write leaves the image intact.
                        try {
                            store.change(info.gid, dir) { it.mark(index) }
                            val deletion = delete(info.gid, index)
                            if (deletion.removed) removed++
                            if (!deletion.complete) incomplete++
                        } finally {
                            if (index in store.get(info.gid).ads) changed(info.gid, index)
                        }
                    } else {
                        store.change(info.gid, dir) { it.copy(checked = it.checked + index) }
                    }
                }
            }
        }
        Report(removed, incomplete, manualComplete)
    }

    /** All supported variants, not just the first matching extension. */
    data class Deletion(val removed: Boolean, val complete: Boolean)

    fun deleteImages(gid: Long, index: Int): Deletion {
        val key = getImageKey(gid, index)
        val cached = EhApplication.imageCache.openSnapshot(key)?.use { true } == true
        val cacheRemoved = EhApplication.imageCache.remove(key)
        val dir = SpiderDen.getGalleryDownloadDir(gid) ?: return Deletion(cacheRemoved, !cached || cacheRemoved)
        val prefix = SpiderDen.perFilename(index, null)
        val files = checkNotNull(dir.listFiles()) { "Cannot list download directory" }
            .filter { it.isFile && it.name?.substringBeforeLast('.') == prefix }
        val deleted = files.count { it.delete() }
        return Deletion(deleted > 0 || cacheRemoved, deleted == files.size && (!cached || cacheRemoved))
    }
}
