package com.hippo.ehviewer.ads

import android.util.AtomicFile
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.spider.SpiderDen
import com.hippo.unifile.UniFile
import com.hippo.unifile.openInputStream
import com.hippo.unifile.openOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex

/** Durable user decisions, deliberately separate from the rebuildable tag cache. IO threads only. */
class AdPageStore(private val root: File) {
    private val records = ConcurrentHashMap<Long, AdRecord>()
    private fun file(gid: Long) = AtomicFile(File(root, "$gid.json"))

    @Synchronized
    fun load(gid: Long, token: String?, pages: Int, dir: UniFile?): AdRecord {
        records[gid]?.takeIf { it.matches(gid, token, pages) }?.let { return it }
        val primary = file(gid)
        val old = if (primary.baseFile.exists() || File(primary.baseFile.path + ".bak").exists()) {
            AdRecord.decode(primary.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        } else {
            dir?.findFile(SIDECAR)?.openInputStream()?.use { AdRecord.decode(it.readBytes().toString(Charsets.UTF_8)) }
        }
        return (old?.takeIf { it.matches(gid, token, pages) } ?: AdRecord(gid, token, pages)).also { records[gid] = it }
    }

    fun get(gid: Long): AdRecord = checkNotNull(records[gid]) { "Ad decisions not loaded" }

    @Synchronized
    fun change(gid: Long, dir: UniFile?, update: (AdRecord) -> AdRecord): AdRecord {
        val next = update(get(gid))
        root.mkdirs()
        val atomic = file(gid)
        val bytes = next.encode().toByteArray()
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
        // Publish the durable decision even if the directory mirror fails. Never delete until both writes succeed.
        records[gid] = next
        if (dir != null) {
            val mirror = checkNotNull(dir.createFile(SIDECAR)) { "Cannot save ad page record" }
            mirror.openOutputStream().use { it.write(bytes) }
        }
        return next
    }

    companion object {
        const val SIDECAR = ".ehviewer-ads.json"
    }
}

object AdPages {
    val store by lazy { AdPageStore(File(EhApplication.application.filesDir, "ad-pages")) }
    private val pageLocks = Array(256) { Mutex() }
    private val scanLocks = Array(64) { Mutex() }
    fun lock(gid: Long, index: Int): Mutex = pageLocks[(31 * gid.hashCode() + index).and(Int.MAX_VALUE) % pageLocks.size]
    fun scanLock(gid: Long): Mutex = scanLocks[gid.hashCode().and(Int.MAX_VALUE) % scanLocks.size]
    fun load(gid: Long, token: String?, pages: Int) = store.load(gid, token, pages, SpiderDen.getGalleryDownloadDir(gid))
    fun isAd(gid: Long, index: Int) = index in store.get(gid).ads
}
