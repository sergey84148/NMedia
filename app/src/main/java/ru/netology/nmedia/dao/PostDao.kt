package ru.netology.nmedia.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ru.netology.nmedia.entity.PostEntity
import ru.netology.nmedia.enumeration.AttachmentType
import ru.netology.nmedia.enumeration.SyncState

@Dao
interface PostDao {

    // Основные запросы
    @Query("SELECT * FROM PostEntity ORDER BY id DESC")
    fun getAll(): Flow<List<PostEntity>>

    @Query("SELECT * FROM PostEntity ORDER BY id DESC")
    suspend fun getAllSync(): List<PostEntity>

    @Query("SELECT * FROM PostEntity WHERE id = :id")
    suspend fun getById(id: Long): PostEntity?

    @Query("SELECT * FROM PostEntity WHERE serverId = :serverId")
    suspend fun getPostByServerId(serverId: Long): PostEntity?

    @Query("SELECT * FROM PostEntity ORDER BY id DESC LIMIT 1")
    suspend fun findLastEditedPost(): PostEntity?

    @Query("SELECT COUNT(*) FROM PostEntity")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) == 0 FROM PostEntity")
    suspend fun isEmpty(): Boolean

    // Запросы для новых постов
    @Query("SELECT COUNT(*) FROM PostEntity WHERE isNew = 1")
    suspend fun getNewPostsCount(): Int

    @Query("SELECT serverId FROM PostEntity WHERE isNew = 1")
    suspend fun getNewPostsIds(): List<Long?>

    @Query("UPDATE PostEntity SET isNew = 0 WHERE isNew = 1")
    suspend fun markAllAsVisible()

    // Запросы для синхронизации
    @Query("SELECT * FROM PostEntity WHERE syncState != 'SYNCED'")
    suspend fun getPendingPosts(): List<PostEntity>

    @Query("SELECT COUNT(*) FROM PostEntity WHERE syncState != 'SYNCED'")
    suspend fun getPendingPostsCount(): Int

    @Query("SELECT * FROM PostEntity WHERE syncState = :state")
    suspend fun getPostsBySyncState(state: SyncState): List<PostEntity>

    @Query("SELECT * FROM PostEntity WHERE syncState IN (:states)")
    suspend fun getPostsBySyncStates(states: List<SyncState>): List<PostEntity>

    @Query("UPDATE PostEntity SET syncState = :newState WHERE id = :id")
    suspend fun updateSyncState(id: Long, newState: SyncState)

    // CRUD операции
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(post: PostEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(posts: List<PostEntity>)

    @Update
    suspend fun update(post: PostEntity)

    @Update
    suspend fun updateAll(posts: List<PostEntity>)

    @Delete
    suspend fun delete(post: PostEntity)

    @Query("DELETE FROM PostEntity WHERE id = :id")
    suspend fun removeById(id: Long)

    @Query("DELETE FROM PostEntity WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: Long)

    // Операции с лайками и репостами
    @Query(
        """
        UPDATE PostEntity SET  
            likes = likes + CASE WHEN likedByMe THEN -1 ELSE 1 END,
            likedByMe = CASE WHEN likedByMe THEN 0 ELSE 1 END
        WHERE id = :id
        """
    )
    suspend fun likeById(id: Long)

    @Query(
        """
        UPDATE PostEntity SET
            shares = shares + 1
        WHERE id = :id
        """
    )
    suspend fun shareById(id: Long)

    @Query("UPDATE PostEntity SET content = :content WHERE id = :id")
    suspend fun updateContentById(id: Long, content: String)
}

class Converters {
    @TypeConverter
    fun toAttachmentType(value: String) = enumValueOf<AttachmentType>(value)

    @TypeConverter
    fun fromAttachmentType(value: AttachmentType) = value.name

    @TypeConverter
    fun toSyncState(value: String) = enumValueOf<SyncState>(value)

    @TypeConverter
    fun fromSyncState(value: SyncState) = value.name
}