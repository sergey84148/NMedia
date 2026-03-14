package ru.netology.nmedia.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.netology.nmedia.entity.PostEntity
import ru.netology.nmedia.enumeration.SyncState

@Dao
interface PostDao {

    // Получаем только "видимые" посты (не новые) для RecyclerView
    @Query("SELECT * FROM PostEntity WHERE isNew = 0 ORDER BY id DESC")
    fun getAllVisible(): Flow<List<PostEntity>>

    // Получаем все посты (для синхронизации)
    @Query("SELECT * FROM PostEntity ORDER BY id DESC")
    suspend fun getAll(): List<PostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(post: PostEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
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

    @Query("SELECT * FROM PostEntity WHERE id = :id")
    suspend fun getById(id: Long): PostEntity?

    // 👇 МЕТОДЫ ДЛЯ НОВЫХ ПОСТОВ
    @Query("SELECT COUNT(*) FROM PostEntity WHERE isNew = 1")
    suspend fun getNewPostsCount(): Int

    @Query("SELECT serverId FROM PostEntity WHERE isNew = 1")
    suspend fun getNewPostsIds(): List<Long>

    @Query("UPDATE PostEntity SET isNew = 0 WHERE isNew = 1")
    suspend fun markAllAsVisible()

    // 👇 МЕТОДЫ ДЛЯ СИНХРОНИЗАЦИИ
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

    // 👇 ДОБАВЬТЕ ЭТОТ МЕТОД - удаление по объекту
    @Delete
    suspend fun delete(post: PostEntity)
}