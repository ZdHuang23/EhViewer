package com.hippo.ehviewer.ads

import android.content.SharedPreferences

/** Persistent scheduling state; ad decisions themselves live in AdPageStore. */
class AdCleanupState(private val prefs: SharedPreferences) {
    val pending: Set<String> get() = synchronized(lock) { prefs.getStringSet("ad_pending", emptySet()).orEmpty().toSet() }
    val bulkPending get() = prefs.getBoolean("ad_bulk_pending", false)
    val cursor get() = prefs.getLong("ad_bulk_cursor", -1)

    fun acceptDownload(gid: Long): Boolean = synchronized(lock) {
        if (!prefs.getBoolean("ad_auto", false)) return false
        prefs.edit().putStringSet("ad_pending", pending + gid.toString()).apply()
        true
    }

    fun forget(gid: Long) = synchronized(lock) {
        prefs.edit().putStringSet("ad_pending", pending - gid.toString()).apply()
    }

    fun beginBulk() = synchronized(lock) {
        val editor = prefs.edit().putBoolean("ad_bulk_pending", true)
        if (!bulkPending) {
            editor.putLong("ad_bulk_cursor", -1).putInt("ad_done", 0).putInt("ad_total", 0)
                .putInt("ad_removed", 0).putInt("ad_errors", 0).putInt("ad_incomplete", 0).putInt("ad_waiting", 0)
        }
        editor.apply()
    }

    fun checkpoint(gid: Long) = synchronized(lock) {
        prefs.edit().putLong("ad_bulk_cursor", maxOf(cursor, gid)).apply()
    }

    fun finishBulk() = synchronized(lock) { prefs.edit().putBoolean("ad_bulk_pending", false).apply() }

    companion object {
        private val lock = Any()
    }
}
