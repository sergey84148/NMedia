package ru.netology.nmedia.repository

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import ru.netology.nmedia.api.ApiService
import ru.netology.nmedia.dto.FeedItem
import ru.netology.nmedia.dto.Media
import ru.netology.nmedia.dto.MediaUpload
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.enumeration.SyncState
import java.io.File

interface PostRepository {
    val data: Flow<PagingData<FeedItem>>
    suspend fun getAll(): List<Post>
    val newPostsCount: Flow<Int>
    suspend fun likeById(id: Long): Post
    suspend fun dislikeById(id: Long): Post
    suspend fun save(post: Post): Post
    suspend fun saveWithAttachment(post: Post, media: MediaUpload): Post
    suspend fun removeById(id: Long)
    suspend fun shareById(id: Long): Post
    suspend fun getAllAsync()
    suspend fun syncWithServer()
    suspend fun getSyncState(): Flow<SyncState>
    suspend fun getPendingPostsCount(): Int
    suspend fun retryFailedSync()
    suspend fun checkForNewPosts(afterId: Long): Int
    suspend fun getNewPostsCount(): Int
    suspend fun showNewPosts()
    suspend fun upload(file: File): Media
    val apiService: ApiService
}