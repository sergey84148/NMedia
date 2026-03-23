package ru.netology.nmedia.repository

import kotlinx.coroutines.flow.Flow
import ru.netology.nmedia.dto.Media
import ru.netology.nmedia.dto.MediaUpload
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.enumeration.SyncState
import java.io.File

interface PostRepository {
    // Данные как Flow (только видимые посты)
    val data: Flow<List<Post>>

    // Flow для отслеживания количества новых постов (для плашки)
    val newPostsCount: Flow<Int>

    // CRUD операции
    suspend fun likeById(id: Long): Post
    suspend fun dislikeById(id: Long): Post
    suspend fun save(post: Post): Post
    suspend fun saveWithAttachment(post: Post, media: MediaUpload): Post
    suspend fun removeById(id: Long)
    suspend fun shareById(id: Long): Post
    suspend fun getAllAsync()

    // Методы для синхронизации
    suspend fun syncWithServer()
    suspend fun getSyncState(): Flow<SyncState>
    suspend fun getPendingPostsCount(): Int
    suspend fun retryFailedSync()

    // Методы для работы с новыми постами
    suspend fun checkForNewPosts(afterId: Long): Int
    suspend fun getNewPostsCount(): Int
    suspend fun showNewPosts()

    // Метод для загрузки медиа
    suspend fun upload(file: File): Media
}
