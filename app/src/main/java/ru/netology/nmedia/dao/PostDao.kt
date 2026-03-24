package ru.netology.nmedia.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ru.netology.nmedia.entity.PostEntity
import ru.netology.nmedia.enumeration.AttachmentType

@Dao
interface PostDao {
    @Query("SELECT * FROM PostEntity ORDER BY id DESC")
    fun getAll(): Flow<List<PostEntity>>

    @Query("SELECT * FROM PostEntity WHERE syncState != 'SYNCED'")
    suspend fun getPendingPosts(): List<PostEntity>

    @Query("SELECT COUNT(*) FROM PostEntity WHERE syncState != 'SYNCED'")
    suspend fun getPendingPostsCount(): Int

    @Query("SELECT COUNT(*) == 0 FROM PostEntity")
    suspend fun isEmpty(): Boolean

    @Query("SELECT * FROM PostEntity WHERE id = :id")
    suspend fun getById(id: Long): PostEntity?

    @Query("SELECT COUNT(*) FROM PostEntity")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(post: PostEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(posts: List<PostEntity>)

    @Query("DELETE FROM PostEntity WHERE id = :id")
    suspend fun removeById(id: Long)

    @Delete
    suspend fun delete(post: PostEntity)

    @Update
    suspend fun update(post: PostEntity)
    @Query("SELECT COUNT(*) FROM PostEntity WHERE isNew = 1")
    suspend fun getNewPostsCount(): Int

    @Query("UPDATE PostEntity SET isNew = 0 WHERE isNew = 1")
    suspend fun markAllAsVisible()

}

class Converters {
    @TypeConverter
    fun toAttachmentType(value: String) = enumValueOf<AttachmentType>(value)
    @TypeConverter
    fun fromAttachmentType(value: AttachmentType) = value.name
}