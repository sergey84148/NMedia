package ru.netology.nmedia.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.netology.nmedia.entity.PostEntity
import ru.netology.nmedia.enumeration.SyncState

@Dao
interface PostDao {

    // Исправлено: имя таблицы должно быть таким же как в Entity (по умолчанию - имя класса)
    @Query("SELECT * FROM PostEntity ORDER BY id DESC")
    fun getAll(): Flow<List<PostEntity>>

    @Insert(onConflict = REPLACE)
    suspend fun insert(post: PostEntity)

    @Insert(onConflict = REPLACE)
    suspend fun insert(posts: List<PostEntity>)

    @Query("UPDATE PostEntity SET content = :content WHERE id = :id")
    suspend fun updateContentById(id: Long, content: String)

    suspend fun save(post: PostEntity) = if (post.id == 0L) insert(post) else updateContentById(post.id, post.content)

    @Query(
        """
        UPDATE PostEntity SET  
            likes = likes + CASE WHEN likedByMe THEN -1 ELSE 1 END,
            likedByMe = CASE WHEN likedByMe THEN 0 ELSE 1 END
        WHERE id = :id
        """
    )
    suspend fun likeById(id: Long)

    @Query("DELETE FROM PostEntity WHERE id = :id")
    suspend fun removeById(id: Long)

    @Query(
        """
        UPDATE PostEntity SET
            shares = shares + 1
        WHERE id = :id
        """
    )
    suspend fun shareById(id: Long)

    @Query("SELECT * FROM PostEntity ORDER BY id DESC LIMIT 1")
    suspend fun findLastEditedPost(): PostEntity?

    // ========== МЕТОДЫ ДЛЯ ПОИСКА ==========

    @Query("SELECT * FROM PostEntity WHERE id = :id")
    suspend fun getById(id: Long): PostEntity?

    // ========== МЕТОДЫ ДЛЯ ПЛАШКИ "СВЕЖИЕ ЗАПИСИ" ==========

    // 👇 НОВЫЙ МЕТОД - подсчет постов, созданных после указанного времени
    @Query("SELECT COUNT(*) FROM PostEntity WHERE lastModified > :timestamp")
    suspend fun getNewerPostsCount(timestamp: Long): Int

    // ========== МЕТОДЫ ДЛЯ СИНХРОНИЗАЦИИ ==========

    @Query("SELECT * FROM PostEntity WHERE syncState = :state")
    suspend fun getPostsBySyncState(state: SyncState): List<PostEntity>

    @Query("SELECT * FROM PostEntity WHERE serverId = :serverId")
    suspend fun getPostByServerId(serverId: Long): PostEntity?

    @Query("SELECT * FROM PostEntity WHERE syncState IN (:states)")
    suspend fun getPostsBySyncStates(states: List<SyncState>): List<PostEntity>

    @Query("SELECT MAX(lastModified) FROM PostEntity")
    suspend fun getLastSyncTime(): Long?

    @Update
    suspend fun update(post: PostEntity)

    @Update
    suspend fun updateAll(posts: List<PostEntity>)

    @Query("DELETE FROM PostEntity WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: Long)

    @Query("UPDATE PostEntity SET syncState = :newState WHERE id = :id")
    suspend fun updateSyncState(id: Long, newState: SyncState)

    @Delete
    suspend fun delete(post: PostEntity)
}