package com.hippo.ehviewer.ads

import com.hippo.unifile.UniFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object DownloadStorage {
    data class Usage(val bytes: Long, val unknown: Int)
    suspend fun measure(root: UniFile): Usage {
        var bytes = 0L
        var unknown = 0
        val pending = ArrayDeque<UniFile>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val file = pending.removeLast()
            try {
                if (file.isDirectory) {
                    val files = file.listFiles()
                    if (files == null) unknown++ else pending.addAll(files)
                } else {
                    val size = file.length()
                    if (size < 0) unknown++ else bytes += size
                }
            } catch (_: SecurityException) {
                unknown++
            }
        }
        return Usage(bytes, unknown)
    }
}
