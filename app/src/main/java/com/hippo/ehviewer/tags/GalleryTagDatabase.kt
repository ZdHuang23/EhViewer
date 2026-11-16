package com.hippo.ehviewer.tags

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(tableName = "gallery_tag_sync")
data class TagSync(
    @PrimaryKey val gid: Long,
    val token: String?,
    val complete: Boolean = false,
    val status: String = "pending",
    val updatedAt: Long = 0,
    val attempts: Int = 0,
)

@Entity(tableName = "gallery_tags", primaryKeys = ["gid", "namespace", "name"], indices = [Index(value = ["namespace", "name", "gid"])])
data class GalleryTag(val gid: Long, val namespace: String, val name: String)

@Dao
abstract class GalleryTagDao {
    @Query("SELECT * FROM gallery_tag_sync WHERE gid = :gid")
    abstract suspend fun get(gid: Long): TagSync?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun put(sync: TagSync)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTags(tags: List<GalleryTag>)

    @Query("DELETE FROM gallery_tags WHERE gid = :gid")
    abstract suspend fun deleteTags(gid: Long)

    @Query("SELECT * FROM gallery_tag_sync WHERE status = 'pending' LIMIT 25")
    abstract suspend fun pending(): List<TagSync>

    @Query("SELECT gid FROM gallery_tags WHERE namespace = :namespace AND name = :name")
    abstract suspend fun matching(namespace: String, name: String): List<Long>

    @Query("SELECT gid FROM gallery_tag_sync WHERE complete = 1")
    abstract suspend fun known(): List<Long>

    @Query("SELECT * FROM gallery_tag_sync")
    abstract suspend fun all(): List<TagSync>

    @Transaction
    open suspend fun save(sync: TagSync, tags: List<GalleryTag>) {
        deleteTags(sync.gid)
        insertTags(tags)
        put(sync.copy(complete = true, status = "ready", updatedAt = System.currentTimeMillis(), attempts = 0))
    }

    @Transaction
    open suspend fun seed(gid: Long, token: String?, refresh: Boolean = false, retry: Boolean = false) {
        val old = get(gid)
        if (old == null || refresh || old.token != token || retry && old.status in listOf("failed", "unavailable", "missing")) {
            put((old ?: TagSync(gid, token)).copy(token = token, status = if (token.isNullOrBlank()) "missing" else "pending", attempts = 0))
        }
    }
}

@Database(entities = [TagSync::class, GalleryTag::class], version = 1, exportSchema = true)
abstract class GalleryTagDatabase : RoomDatabase() {
    abstract fun tags(): GalleryTagDao

    companion object {
        @Volatile private var instance: GalleryTagDatabase? = null
        fun get(context: Context): GalleryTagDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, GalleryTagDatabase::class.java, "eh_tags.db").build().also { instance = it }
        }
    }
}
