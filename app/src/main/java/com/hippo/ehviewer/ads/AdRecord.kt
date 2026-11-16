package com.hippo.ehviewer.ads

import org.json.JSONArray
import org.json.JSONObject

data class AdRecord(
    val gid: Long,
    val token: String?,
    val pages: Int,
    val ads: Set<Int> = emptySet(),
    val restored: Set<Int> = emptySet(),
    val checked: Set<Int> = emptySet(),
    val manualComplete: Boolean = false,
) {
    fun mark(index: Int) = copy(ads = ads + index, restored = restored - index, checked = checked + index)
    fun restore(index: Int) = copy(ads = ads - index, restored = restored + index)
    fun matches(gid: Long, token: String?, pages: Int) = this.gid == gid && this.token == token && this.pages == pages
    fun encode(): String = JSONObject().put("version", 1).put("gid", gid).put("token", token ?: JSONObject.NULL)
        .put("pages", pages).put("ads", JSONArray(ads.sorted())).put("restored", JSONArray(restored.sorted()))
        .put("checked", JSONArray(checked.sorted())).put("manualComplete", manualComplete).toString()

    companion object {
        fun tail(pages: Int): IntRange = (pages - 7).coerceAtLeast(0) until pages
        fun decode(text: String): AdRecord {
            val obj = JSONObject(text)
            require(obj.getInt("version") == 1)
            val pages = obj.getInt("pages")
            require(pages > 0)
            fun indices(key: String): Set<Int> = obj.getJSONArray(key).let { array ->
                (0 until array.length()).map { array.getInt(it).also { i -> require(i in 0 until pages) } }.toSet()
            }
            return AdRecord(
                obj.getLong("gid"),
                obj.optString("token").takeUnless { obj.isNull("token") },
                pages,
                indices("ads"),
                indices("restored"),
                indices("checked"),
                obj.optBoolean("manualComplete"),
            ).also { require(it.ads.intersect(it.restored).isEmpty()) }
        }
    }
}
