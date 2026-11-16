package com.hippo.ehviewer.ads

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.spider.SpiderDen
import com.hippo.ehviewer.spider.SpiderQueen
import com.hippo.ehviewer.spider.readFromUniFile
import com.hippo.ehviewer.tags.GalleryTagDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdCleanupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val state = AdCleanupState(prefs)
        val bulk = state.bulkPending
        if (!bulk && !prefs.getBoolean("ad_auto", false)) return@withContext Result.success()
        val dao = GalleryTagDatabase.get(applicationContext).tags()
        val candidates = dao.matching("other", "extraneous ads").toSet()
        val pending = state.pending
        val all = EhDB.allDownloadInfo.filter { it.state == DownloadInfo.STATE_FINISH && (bulk && it.gid in candidates || it.gid.toString() in pending) }.sortedBy { it.gid }
        val rows = all.filter { !bulk || it.gid > state.cursor }
        if (!bulk && rows.isEmpty()) return@withContext Result.success()
        var done = if (bulk) prefs.getInt("ad_done", 0) else 0
        var removed = if (bulk) prefs.getInt("ad_removed", 0) else 0
        var errors = if (bulk) prefs.getInt("ad_errors", 0) else 0
        var incomplete = if (bulk) prefs.getInt("ad_incomplete", 0) else 0
        var waiting = 0
        val total = if (bulk) maxOf(prefs.getInt("ad_total", 0), done + rows.size) else rows.size
        val started = android.os.SystemClock.elapsedRealtime()
        for (row in rows) {
            if (prefs.getBoolean("ad_paused", false)) return@withContext Result.success()
            if (!bulk && !prefs.getBoolean("ad_auto", false)) return@withContext Result.success()
            if (android.os.SystemClock.elapsedRealtime() - started > 7 * 60 * 1000) return@withContext Result.retry()
            var completed = true
            try {
                // Check identity against metadata: never apply a stale tag snapshot to a different token.
                val tags = dao.get(row.gid)
                if (tags?.complete != true) {
                    if (tags?.status == "unavailable" || tags?.status == "missing") state.forget(row.gid) else waiting++
                    continue
                }
                val info = SpiderDen.getGalleryDownloadDir(row.gid)?.findFile(SpiderQueen.SPIDER_INFO_FILENAME)?.let { readFromUniFile(it) }
                if (info == null || info.gid != row.gid || row.token != null && info.token != row.token || tags.token != (row.token ?: info.token)) {
                    incomplete++
                    continue
                }
                if (row.gid !in candidates) {
                    state.forget(row.gid)
                    continue
                }
                val report = AdCleaner.scan(info)
                removed += report.removed
                incomplete += report.incomplete
                if (report.incomplete == 0) state.forget(row.gid)
            } catch (e: CancellationException) {
                completed = false
                throw e
            } catch (e: Exception) {
                android.util.Log.w("AdCleanupWorker", "Failed gallery ${row.gid}", e)
                errors++
            } finally {
                if (completed) {
                    done++
                    if (bulk) state.checkpoint(row.gid)
                    val progress = workDataOf("done" to done, "total" to total, "removed" to removed, "errors" to errors, "incomplete" to incomplete, "waiting" to waiting)
                    setProgress(progress)
                    prefs.edit().putInt("ad_done", done).putInt("ad_total", total).putInt("ad_removed", removed)
                        .putInt("ad_errors", errors).putInt("ad_incomplete", incomplete).putInt("ad_waiting", waiting).apply()
                }
            }
        }
        prefs.edit().putInt("ad_done", done).putInt("ad_total", total).putInt("ad_removed", removed)
            .putInt("ad_errors", errors).putInt("ad_incomplete", incomplete).putInt("ad_waiting", waiting).apply()
        if (bulk) state.finishBulk()
        Result.success(workDataOf("done" to done, "total" to total, "removed" to removed, "errors" to errors, "incomplete" to incomplete, "waiting" to waiting))
    }

    companion object {
        const val WORK = "gallery-ad-cleanup"

        fun afterDownload(context: Context, gid: Long) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            if (AdCleanupState(prefs).acceptDownload(gid)) {
                com.hippo.ehviewer.tags.TagSyncWorker.schedule(context, seed = true)
                schedule(context)
            }
        }

        fun schedule(context: Context, bulk: Boolean = false) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            if (bulk) AdCleanupState(prefs).beginBulk()
            val pending = prefs.getStringSet("ad_pending", emptySet()).orEmpty()
            if (!bulk && !prefs.getBoolean("ad_bulk_pending", false) && (!prefs.getBoolean("ad_auto", false) || pending.isEmpty())) return
            if (prefs.getBoolean("ad_paused", false)) return
            val request = OneTimeWorkRequestBuilder<AdCleanupWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
