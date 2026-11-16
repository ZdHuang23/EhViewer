package com.hippo.ehviewer.tags

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.client.EhEngine
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class TagSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val dao = GalleryTagDatabase.get(applicationContext).tags()
        if (inputData.getBoolean("seed", false) || prefs.getBoolean("tag_auto_sync", true)) {
            EhDB.allDownloadInfo.forEach {
                val token = it.token ?: com.hippo.ehviewer.spider.SpiderDen.getGalleryDownloadDir(it.gid)
                    ?.findFile(com.hippo.ehviewer.spider.SpiderQueen.SPIDER_INFO_FILENAME)
                    ?.let { file -> com.hippo.ehviewer.spider.readFromUniFile(file)?.token }
                dao.seed(it.gid, token, inputData.getBoolean("refresh", false) && runAttemptCount == 0, inputData.getBoolean("retry", false) && runAttemptCount == 0)
            }
        }
        var batches = 0
        while (!prefs.getBoolean("tag_sync_paused", false)) {
            val batch = dao.pending()
            if (batch.isEmpty()) return Result.success()
            try {
                val response = EhEngine.getGalleryTags(batch)
                for (item in batch) {
                    when (val metadata = response[item.gid]) {
                        is TagMetadata.Available -> {
                            dao.save(item, metadata.tags)
                        }
                        TagMetadata.Unavailable -> dao.put(item.copy(status = "unavailable"))
                        else -> dao.put(item.copy(attempts = item.attempts + 1, status = if (item.attempts >= 2) "failed" else "pending"))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Preserve snapshots and pending entries across network/authentication failures.
                return if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
            com.hippo.ehviewer.ads.AdCleanupWorker.schedule(applicationContext)
            if (++batches >= 60) return Result.retry()
            delay(5000)
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context, seed: Boolean = false, refresh: Boolean = false, retry: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<TagSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workDataOf("seed" to seed, "refresh" to refresh, "retry" to retry))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("gallery-tag-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
