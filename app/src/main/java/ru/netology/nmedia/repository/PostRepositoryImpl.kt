package ru.netology.nmedia.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import ru.netology.nmedia.api.PostsApi
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
    private val _syncState = MutableStateFlow(SyncState.DONE)

    override val data: Flow<List<Post>> = dao.getAll()
        .map { entities -> entities.map(PostEntity::toDto) }
        .flowOn(Dispatchers.Default)

    override val newPostsCount: Flow<Int> = flow {
        while (true) {
            delay(30_000L)
            emit(dao.getNewPostsCount())
        }
    }.flowOn(Dispatchers.Default)

    override suspend fun getAll(): List<Post> {
        return dao.getAllSync().map(PostEntity::toDto)
    }

    override suspend fun getSyncState(): Flow<SyncState> = _syncState.asStateFlow()

    override suspend fun getPendingPostsCount(): Int = dao.getPendingPostsCount()

    override suspend fun getNewPostsCount(): Int = dao.getNewPostsCount()

    override suspend fun showNewPosts() = dao.markAllAsVisible()

    override suspend fun dislikeById(id: Long): Post = likeById(id)

    override suspend fun likeById(id: Long): Post {
        val entity = dao.getById(id) ?: throw IllegalArgumentException("Post not found")
        val postDto = entity.toDto()

        val updatedDto = postDto.copy(
            likedByMe = !postDto.likedByMe,
            likes = if (postDto.likedByMe) postDto.likes - 1 else postDto.likes + 1
        )

        val updatedEntity = entity.copy(
            likedByMe = updatedDto.likedByMe,
            likes = updatedDto.likes,
            syncState = if (entity.id > 0) SyncState.PENDING else SyncState.SYNCED,
            lastModified = System.currentTimeMillis()
        )

        dao.update(updatedEntity)

        if (entity.id <= 0) {
            return updatedDto
        }

        return try {
            val serverResponse = if (postDto.likedByMe) {
                PostsApi.service.dislikeById(entity.id)
            } else {
                PostsApi.service.likeById(entity.id)
            }

            if (serverResponse.isSuccessful) {
                val serverBody = serverResponse.body() ?: updatedDto
                val syncedEntity = updatedEntity.copy(
                    syncState = SyncState.SYNCED,
                    retryCount = 0,
                    likes = serverBody.likes,
                    likedByMe = serverBody.likedByMe
                )
                dao.update(syncedEntity)
                serverBody
            } else {
                Log.e("PostRepository", "Like/Dislike failed: ${serverResponse.code()}")
                _syncState.value = SyncState.FAILED
                updatedDto
            }
        } catch (e: Exception) {
            Log.e("PostRepository", "Like/Dislike error", e)
            _syncState.value = SyncState.FAILED
            updatedDto
        }
    }

    override suspend fun shareById(id: Long): Post {
        val entity = dao.getById(id) ?: throw IllegalArgumentException("Post with id $id not found")

        val updatedEntity = entity.copy(
            shares = entity.shares + 1,
            syncState = if (entity.id > 0) SyncState.PENDING else SyncState.SYNCED,
            lastModified = System.currentTimeMillis()
        )

        dao.update(updatedEntity)

        return if (entity.id > 0) {
            try {
                val response = PostsApi.service.shareById(entity.id)
                if (response.isSuccessful) {
                    val serverPost = response.body() ?: updatedEntity.toDto()
                    val syncedEntity = updatedEntity.copy(
                        syncState = SyncState.SYNCED,
                        retryCount = 0,
                        shares = serverPost.shares
                    )
                    dao.update(syncedEntity)
                    serverPost
                } else {
                    Log.e("PostRepository", "Share failed: ${response.code()}")
                    _syncState.value = SyncState.FAILED
                    updatedEntity.toDto()
                }
            } catch (e: Exception) {
                Log.e("PostRepository", "Share error", e)
                _syncState.value = SyncState.FAILED
                updatedEntity.toDto()
            }
        } else {
            updatedEntity.toDto()
        }
    }

    override suspend fun save(post: Post): Post {
        // Для новых постов используем отрицательный ID
        val entity = if (post.id <= 0) {
            PostEntity.fromDto(
                dto = post.copy(id = -System.currentTimeMillis()),
                syncState = SyncState.PENDING
            )
        } else {
            val existing = dao.getById(post.id)
            if (existing != null) {
                PostEntity.updateFromDto(
                    existingEntity = existing,
                    dto = post,
                    syncState = SyncState.PENDING
                )
            } else {
                PostEntity.fromDto(post, SyncState.PENDING)
            }
        }

        val savedId = if (entity.id <= 0) {
            dao.insert(entity)
            entity.id
        } else {
            dao.update(entity)
            entity.id
        }

        return try {
            // Отправляем на сервер с id = 0 для новых постов
            val postForServer = if (post.id <= 0) {
                post.copy(id = 0)
            } else {
                post
            }

            val response = PostsApi.service.save(postForServer)
            if (!response.isSuccessful) {
                throw ApiError(response.code(), response.message())
            }

            val serverPost = response.body() ?: throw ApiError(response.code(), "Empty body")

            // Удаляем временный пост
            dao.delete(entity)

            // Проверяем, не появился ли пост при параллельной синхронизации
            val existing = dao.getById(serverPost.id)
            if (existing == null) {
                dao.insert(PostEntity.fromDto(serverPost, SyncState.SYNCED))
            } else {
                dao.update(
                    PostEntity.updateFromDto(
                        existingEntity = existing,
                        dto = serverPost,
                        syncState = SyncState.SYNCED
                    )
                )
            }

            serverPost
        } catch (e: Exception) {
            Log.e("PostRepository", "Save error", e)
            _syncState.value = SyncState.FAILED
            post.copy(id = savedId)
        }
    }

    override suspend fun saveWithAttachment(post: Post, media: MediaUpload): Post {
        try {
            val uploadedMedia = upload(media.file)
            val postWithAttachment = post.copy(
                attachment = Attachment(
                    url = uploadedMedia.id,
                    type = AttachmentType.IMAGE,
                )
            )
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

    override suspend fun removeById(id: Long) {
        val entity = dao.getById(id) ?: return

        // Если id отрицательный (несинхронизированный пост), просто удаляем
        if (entity.id <= 0) {
            dao.delete(entity)
            return
        }

        try {
            val response = PostsApi.service.removeById(entity.id)
            if (response.isSuccessful) {
                dao.delete(entity)
            } else {
                val updated = entity.copy(
                    syncState = SyncState.PENDING_DELETE,
                    retryCount = entity.retryCount + 1
                )
                dao.update(updated)
                _syncState.value = SyncState.FAILED
            }
        } catch (e: Exception) {
            Log.e("PostRepository", "Delete error", e)
            val updated = entity.copy(
                syncState = SyncState.PENDING_DELETE,
                retryCount = entity.retryCount + 1
            )
            dao.update(updated)
            _syncState.value = SyncState.FAILED
        }
    }

    override suspend fun syncWithServer() {
        try {
            getAllAsync()
        } catch (e: Exception) {
            Log.e("PostRepository", "Sync error", e)
            _syncState.value = SyncState.FAILED
        }
    }

    override suspend fun retryFailedSync() {
        _syncState.value = SyncState.SYNCING
        var hasErrors = false

        val pendingPosts = dao.getPendingPosts()
        pendingPosts.forEach { post ->
            try {
                when (post.syncState) {
                    SyncState.PENDING -> {
                        // Для несинхронизированных постов id отрицательный
                        val postToSend = post.toDto().copy(id = 0) // id 0 для создания на сервере
                        val response = PostsApi.service.save(postToSend)
                        if (response.isSuccessful) {
                            val serverPost = response.body()
                            if (serverPost != null) {
                                // Удаляем временный пост с отрицательным id
                                dao.delete(post)

                                // Вставляем синхронизированный пост с положительным id
                                val existing = dao.getById(serverPost.id)
                                if (existing == null) {
                                    dao.insert(PostEntity.fromDto(serverPost, SyncState.SYNCED))
                                } else {
                                    dao.update(
                                        PostEntity.updateFromDto(
                                            existingEntity = existing,
                                            dto = serverPost.copy(
                                                shares = post.shares,
                                                video = post.video
                                            ),
                                            syncState = SyncState.SYNCED
                                        )
                                    )
                                }
                            }
                        } else {
                            hasErrors = true
                            Log.e("PostRepository", "Failed to sync post ${post.id}: ${response.code()}")
                        }
                    }
                    SyncState.PENDING_DELETE -> {
                        // Для удаления используем положительный id
                        if (post.id > 0) {
                            val response = PostsApi.service.removeById(post.id)
                            if (response.isSuccessful) {
                                dao.delete(post)
                            } else {
                                hasErrors = true
                                Log.e("PostRepository", "Failed to delete post ${post.id}: ${response.code()}")
                            }
                        } else {
                            // Если id отрицательный, просто удаляем локально
                            dao.delete(post)
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
    }

    override suspend fun checkForNewPosts(afterId: Long): Int {
        return try {
            val response = PostsApi.service.getNewer(afterId)
            if (response.isSuccessful) {
                val newPosts = response.body() ?: return 0

                // Получаем существующие id (только положительные, синхронизированные)
                val existingIds = dao.getAllSync()
                    .filter { it.id > 0 && it.syncState == SyncState.SYNCED }
                    .map { it.id }
                    .toSet()

                val postsToInsert = newPosts
                    .filter { !existingIds.contains(it.id) }
                    .map { PostEntity.fromDto(it, SyncState.SYNCED, isNew = true) }

                if (postsToInsert.isNotEmpty()) {
                    postsToInsert.forEach { dao.insert(it) }
                }

                postsToInsert.size
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e("PostRepository", "Error checking new posts", e)
            0
        }
    }

    override suspend fun upload(file: File): Media {
        try {
            val mediaPart = MultipartBody.Part.createFormData(
                "file", file.name, file.asRequestBody()
            )
            val response = PostsApi.service.upload(mediaPart)
            if (!response.isSuccessful) {
                throw ApiError(response.code(), response.message())
            }
            return response.body() ?: throw ApiError(response.code(), "Empty body")
        } catch (e: IOException) {
            throw NetworkError()
        } catch (e: Exception) {
            throw UnknownError()
        }
    }

    override suspend fun getAllAsync() {
        try {
            val response = PostsApi.service.getAll()
            if (!response.isSuccessful) {
                throw ApiError(response.code(), response.message())
            }
            val serverPosts = response.body() ?: throw ApiError(response.code(), response.message())

            // Получаем все локальные посты
            val localPosts = dao.getAllSync()

            // Для синхронизированных постов id положительный
            val syncedLocalPosts = localPosts
                .filter { it.id > 0 && it.syncState == SyncState.SYNCED }
                .associateBy { it.id }

            val postsToUpsert = mutableListOf<PostEntity>()
            val serverIds = serverPosts.map { it.id }.toSet()

            // Обрабатываем посты с сервера
            serverPosts.forEach { serverPost ->
                val existingLocal = syncedLocalPosts[serverPost.id]

                if (existingLocal == null) {
                    // Новый пост с сервера
                    postsToUpsert.add(
                        PostEntity.fromDto(serverPost, SyncState.SYNCED, isNew = true)
                    )
                } else {
                    // Обновляем существующий пост, сохраняя локальные данные
                    postsToUpsert.add(
                        PostEntity.updateFromDto(
                            existingEntity = existingLocal,
                            dto = serverPost.copy(
                                shares = existingLocal.shares,
                                video = existingLocal.video,
                                likedByMe = existingLocal.likedByMe,
                                likes = existingLocal.likes
                            ),
                            syncState = SyncState.SYNCED,
                            isNew = existingLocal.isNew
                        )
                    )
                }
            }

            // Удаляем локальные посты, которых нет на сервере (только синхронизированные)
            val postsToDelete = syncedLocalPosts.values
                .filter { !serverIds.contains(it.id) }

            // Обновляем или вставляем посты
            if (postsToUpsert.isNotEmpty()) {
                postsToUpsert.forEach { post ->
                    val existing = dao.getById(post.id)
                    if (existing == null) {
                        dao.insert(post)
                    } else {
                        dao.update(post)
                    }
                }
            }

            // Удаляем посты
            if (postsToDelete.isNotEmpty()) {
                postsToDelete.forEach { dao.delete(it) }
            }

        } catch (e: IOException) {
            throw NetworkError()
        } catch (e: Exception) {
            Log.e("PostRepository", "Error loading posts", e)
            throw UnknownError()
        }
    }
}