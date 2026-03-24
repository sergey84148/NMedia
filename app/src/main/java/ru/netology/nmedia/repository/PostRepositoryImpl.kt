package ru.netology.nmedia.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import ru.netology.nmedia.api.*
import ru.netology.nmedia.dao.PostDao
import ru.netology.nmedia.dto.*
import ru.netology.nmedia.entity.PostEntity
import ru.netology.nmedia.enumeration.AttachmentType
import ru.netology.nmedia.enumeration.SyncState
import ru.netology.nmedia.error.ApiError
import ru.netology.nmedia.error.AppError
import ru.netology.nmedia.error.NetworkError
import java.io.File
import java.io.IOException

class PostRepositoryImpl(private val dao: PostDao) : PostRepository {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.DONE)

    override val data = dao.getAll()
        .map { list -> list.map(PostEntity::toDto) }
        .flowOn(Dispatchers.Default)

    private suspend fun findPostById(id: Long): PostEntity? {
        return dao.getById(id)
    }

    override suspend fun getAllAsync() {
        try {
            val response = PostsApi.service.getAll()
            if (!response.isSuccessful) {
                throw ApiError(response.code(), response.message())
            }

            val body = response.body() ?: throw ApiError(response.code(), response.message())
            dao.insert(body.map(PostEntity::fromDto))
        } catch (e: IOException) {
            throw NetworkError()
        } catch (e: Exception) {
            throw UnknownError()
        }
    }

    override suspend fun syncWithServer() {
        getAllAsync()
    }

    override suspend fun getSyncState(): Flow<SyncState> = _syncState.asStateFlow()

    override suspend fun getPendingPostsCount(): Int {
        return dao.getPendingPostsCount()
    }

    override suspend fun retryFailedSync() {
        _syncState.value = SyncState.SYNCING
        try {
            val pendingPosts = dao.getPendingPosts()
            var hasErrors = false

            pendingPosts.forEach { post ->
                try {
                    when (post.syncState) {
                        SyncState.PENDING -> {
                            val response = PostsApi.service.save(post.toDto())
                            if (response.isSuccessful) {
                                val updatedPost = response.body()
                                if (updatedPost != null) {
                                    dao.insert(PostEntity.fromDto(updatedPost).copy(
                                        syncState = SyncState.SYNCED,
                                        retryCount = 0
                                    ))
                                }
                            } else {
                                hasErrors = true
                                Log.e("PostRepository", "Failed to sync post ${post.id}: ${response.code()}")
                            }
                        }
                        SyncState.PENDING_DELETE -> {
                            post.serverId?.let { serverId ->
                                val response = PostsApi.service.removeById(serverId)
                                if (response.isSuccessful) {
                                    dao.delete(post)
                                } else {
                                    hasErrors = true
                                    Log.e("PostRepository", "Failed to delete post ${post.id}: ${response.code()}")
                                }
                            }
                        }
                        else -> { /* Ничего не делаем */ }
                    }
                } catch (exception: Exception) {
                    hasErrors = true
                    Log.e("PostRepository", "Failed to sync post ${post.id}", exception)
                }
            }

            _syncState.value = if (hasErrors) SyncState.FAILED else SyncState.DONE
        } catch (exception: Exception) {
            Log.e("PostRepository", "Error in retryFailedSync", exception)
            _syncState.value = SyncState.FAILED
        }
    }

    override suspend fun checkForNewPosts(afterId: Long): Int {
        return try {
            val response = PostsApi.service.getNewer(afterId)
            if (response.isSuccessful) {
                val body = response.body() ?: return 0
                dao.insert(body.map(PostEntity::fromDto))
                body.size
            } else {
                0
            }
        } catch (exception: Exception) {
            Log.e("PostRepository", "Error checking new posts", exception)
            0
        }
    }

    override suspend fun getNewPostsCount(): Int {
        return dao.getNewPostsCount()
    }

    override suspend fun showNewPosts() {
        dao.markAllAsVisible()
    }

    override suspend fun dislikeById(id: Long): Post = likeById(id)

    override suspend fun shareById(id: Long): Post {
        val entity = findPostById(id)
            ?: throw IllegalArgumentException("Post with id $id not found")

        val updatedEntity = entity.copy(
            shares = entity.shares + 1,
            syncState = SyncState.PENDING,
            lastModified = System.currentTimeMillis()
        )

        dao.update(updatedEntity)

        return try {
            if (entity.serverId != null) {
                val serverPost = PostsApi.service.shareById(entity.serverId)
                val syncedEntity = updatedEntity.copy(
                    syncState = SyncState.SYNCED,
                    retryCount = 0,
                )
                dao.update(syncedEntity)
                serverPost
            } else {
                Log.w("PostRepository", "Post not on server, updating locally only")
                val syncedEntity = updatedEntity.copy(
                    syncState = SyncState.SYNCED,
                    retryCount = 0
                )
                dao.update(syncedEntity)
                updatedEntity.toDto()
            }
        } catch (exception: Exception) {
            Log.e("PostRepository", "Share error", exception)
            _syncState.value = SyncState.FAILED
            updatedEntity.toDto()
        }
    }

    override val newPostsCount: Flow<Int> = flow {
        while (true) {
            delay(30_000L)
            val count = dao.getNewPostsCount()
            emit(count)
        }
    }.flowOn(Dispatchers.Default)

    override suspend fun save(post: Post): Post {
        try {
            val response = PostsApi.service.save(post)
            if (!response.isSuccessful) {
                throw ApiError(response.code(), response.message())
            }

            val body = response.body() ?: throw ApiError(response.code(), response.message())
            val entity = PostEntity.fromDto(body)
            dao.insert(entity)
            return body
        } catch (e: IOException) {
            throw NetworkError()
        } catch (e: Exception) {
            throw UnknownError()
        }
    }

    override suspend fun removeById(id: Long) {
        val entity = findPostById(id)
            ?: throw IllegalArgumentException("Post with id $id not found")

        Log.d("PostRepository", "Removing post: localId=${entity.id}, serverId=${entity.serverId}")

        if (entity.serverId == null) {
            dao.delete(entity)
            return
        }

        try {
            PostsApi.service.removeById(entity.serverId)
            dao.delete(entity)
            Log.d("PostRepository", "Successfully deleted from server")
        } catch (exception: Exception) {
            Log.e("PostRepository", "Delete API error", exception)
            val updatedEntity = entity.copy(
                syncState = SyncState.PENDING_DELETE,
                lastModified = System.currentTimeMillis(),
                retryCount = entity.retryCount + 1
            )
            dao.insert(updatedEntity)
            _syncState.value = SyncState.FAILED
        }
    }

    override suspend fun likeById(id: Long): Post {
        val entity = findPostById(id)
            ?: throw IllegalArgumentException("Post with id $id not found")

        val postDto = entity.toDto()

        val updatedPostDto = postDto.copy(
            likedByMe = !postDto.likedByMe,
            likes = if (postDto.likedByMe) postDto.likes - 1 else postDto.likes + 1
        )

        val updatedEntity = entity.copy(
            likedByMe = updatedPostDto.likedByMe,
            likes = updatedPostDto.likes,
            syncState = if (entity.serverId != null) SyncState.PENDING else SyncState.SYNCED,
            lastModified = System.currentTimeMillis()
        )

        dao.update(updatedEntity)

        if (entity.serverId == null) {
            return updatedPostDto
        }

        return try {
            val serverPost = if (postDto.likedByMe) {
                PostsApi.service.dislikeById(entity.serverId)
            } else {
                PostsApi.service.likeById(entity.serverId)
            }
            val serverPostBody = serverPost.body() ?: updatedPostDto

            val syncedEntity = updatedEntity.copy(
                syncState = SyncState.SYNCED,
                retryCount = 0,
                likes = serverPostBody.likes,
                likedByMe = serverPostBody.likedByMe
            )
            dao.update(syncedEntity)
            serverPostBody
        } catch (exception: Exception) {
            Log.e("PostRepository", "Like/Dislike API error", exception)
            _syncState.value = SyncState.FAILED
            updatedPostDto
        }
    }

    // 👇 ИСПРАВЛЕННЫЙ МЕТОД saveWithAttachment - убрал description
    override suspend fun saveWithAttachment(post: Post, media: MediaUpload): Post {
        try {
            // Сначала загружаем медиафайл
            val uploadedMedia = upload(media.file)

            // Создаем пост с прикрепленным медиа
            val postWithAttachment = post.copy(
                attachment = Attachment(
                    url = uploadedMedia.id,
                    type = AttachmentType.IMAGE,
                )
            )

            // Сохраняем пост
            return save(postWithAttachment)
        } catch (e: AppError) {
            throw e
        } catch (e: IOException) {
            throw NetworkError()
        } catch (e: Exception) {
            Log.e("PostRepository", "Error saving with attachment", e)
            throw UnknownError()
        }
    }

    override suspend fun upload(file: File): Media {
        try {
            val media = MultipartBody.Part.createFormData(
                "file", file.name, file.asRequestBody()
            )

            val response = PostsApi.service.upload(media)
            if (!response.isSuccessful) {
                throw ApiError(response.code(), response.message())
            }

            return response.body() ?: throw ApiError(response.code(), response.message())
        } catch (e: IOException) {
            throw NetworkError()
        } catch (e: Exception) {
            throw UnknownError()
        }
    }
}