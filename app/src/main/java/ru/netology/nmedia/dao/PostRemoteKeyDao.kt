package ru.netology.nmedia.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.netology.nmedia.entity.PostRemoteKeyEntity

@Dao
interface PostRemoteKeyDao {
    @Query("SELECT COUNT(*) == 0 FROM post_remote_keys")
    suspend fun isEmpty(): Boolean

    @Query("SELECT `key` FROM post_remote_keys WHERE type = 'TOP'")
    suspend fun getTopKey(): Long?

    @Query("SELECT `key` FROM post_remote_keys WHERE type = 'BOTTOM'")
    suspend fun getBottomKey(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(key: PostRemoteKeyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(keys: List<PostRemoteKeyEntity>)

    @Query("DELETE FROM post_remote_keys")
    suspend fun removeAll()
}