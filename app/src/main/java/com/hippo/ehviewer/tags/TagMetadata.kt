package com.hippo.ehviewer.tags

import org.json.JSONObject

sealed interface TagMetadata {
    data class Available(val tags: List<GalleryTag>) : TagMetadata
    data object Unavailable : TagMetadata
    data object Failed : TagMetadata
}

fun parseTagMetadata(body: String, requested: Set<Long>): Map<Long, TagMetadata> {
    val items = JSONObject(body).getJSONArray("gmetadata")
    val result = mutableMapOf<Long, TagMetadata>()
    for (i in 0 until items.length()) {
        val item = items.optJSONObject(i) ?: continue
        val gid = item.optLong("gid", -1)
        if (gid !in requested) continue
        result[gid] = runCatching {
            when {
                item.has("error") -> {
                    // Only explicit gallery absence is permanent; authentication and other errors remain retryable.
                    val error = item.optString("error").lowercase()
                    if (error.contains("key missing") || error.contains("file not found") || error.contains("gallery not found")) TagMetadata.Unavailable else TagMetadata.Failed
                }
                item.optJSONArray("tags") != null -> {
                    val tags = item.getJSONArray("tags")
                    TagMetadata.Available(
                        (0 until tags.length()).map { index ->
                            val raw = tags.getString(index)
                            val split = raw.indexOf(':')
                            GalleryTag(gid, if (split < 0) "" else raw.substring(0, split), if (split < 0) raw else raw.substring(split + 1))
                        }.distinct(),
                    )
                }
                else -> TagMetadata.Failed
            }
        }.getOrDefault(TagMetadata.Failed)
    }
    return requested.associateWith { result[it] ?: TagMetadata.Failed }
}
