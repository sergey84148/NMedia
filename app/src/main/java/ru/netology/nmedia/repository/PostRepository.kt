package ru.netology.nmedia.repository

import kotlinx.coroutines.flow.Flow
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.enumeration.SyncState

interface PostRepository {
    // Данные как Flow (для RecyclerView)
    val data: Flow<List<Post>>

    // CRUD операции
    suspend fun likeById(id: Long): Post
    suspend fun dislikeById(id: Long): Post
    suspend fun save(post: Post): Post
    suspend fun removeById(id: Long)
    suspend fun shareById(id: Long): Post
    suspend fun getAllAsync()

    // Методы для синхронизации
    suspend fun syncWithServer()
    suspend fun getSyncState(): Flow<SyncState>
    suspend fun getPendingPostsCount(): Int
    suspend fun retryFailedSync()

    // 👇 НОВЫЙ МЕТОД для подсчета новых постов (для плашки "Свежие записи")
    // Возвращает количество постов, созданных/измененных после указанного времени
    suspend fun getNewerPostsCount(timestamp: Long): Int
}