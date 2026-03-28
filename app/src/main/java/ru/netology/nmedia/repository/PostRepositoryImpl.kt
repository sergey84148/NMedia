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
    private val _syncState = MutableStateFlow<SyncState>(SyncState.DONE)

    override val data: Flow<List<Post>> = dao.getAll()
        .map { entities -> entities.map(PostEntity::toDto) }
        .flowOn(Dispatchers.Default)

    // 👇 ДОБАВЛЯЕМ НЕДОСТАЮЩИЙ МЕТОД
    override suspend fun getAll(): List<Post> {
        return dao.getAllSync().map { it.toDto() }
    }

    private suspend fun findPostEntityById(id: Long): PostEntity? {
        return dao.getById(id)
    }

    override suspend fun getAllAsync() {
        try {
            val response = PostsApi.service.getAll()
            if (!response.isSuccessful) {
                throw ApiError(response.code(), response.message())
            }
            val body = response.body() ?: throw ApiError(response.code(), response.message())

            val existingPosts = mutableMapOf<Long, PostEntity>()
            dao.getAllSync().forEach { post ->
                post.serverId?.let { existingPosts[it] = post }
            }

            val newPostsIds = dao.getNewPostsIds().mapNotNull { it }.toSet()

            val postsToInsert = mutableListOf<PostEntity>()
            val postsToUpdate = mutableListOf<PostEntity>()

            body.forEach { serverPost ->
                val existing = existingPosts[serverPost.id]
                if (existing == null) {
                    val isNew = newPostsIds.contains(serverPost.id)
                    postsToInsert.add(PostEntity.fromDto(serverPost, SyncState.SYNCED, isNew))
                } else {
                    postsToUpdate.add(
                        PostEntity.updateFromDto(
                            existingEntity = existing,
                            dto = serverPost.copy(
                                shares = existing.shares,
                                video = existing.video
                            ),
                            syncState = SyncState.SYNCED,
                            isNew = existing.isNew
                        )
                    )
                }
            }

            if (postsToInsert.isNotEmpty()) {
                dao.insert(postsToInsert)
            }
            if (postsToUpdate.isNotEmpty()) {
                dao.updateAll(postsToUpdate)
            }

            val serverIds = body.map { it.id }.toSet()
            existingPosts.values.forEach { localPost ->
                if (localPost.serverId != null && !serverIds.contains(localPost.serverId)) {
                    dao.delete(localPost)
                }
            }

        } catch (e: IOException) {
            throw NetworkError()
        } catch (e: Exception) {
            Log.e("PostRepository", "Error loading posts", e)
            throw UnknownError()
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

    override suspend fun getSyncState(): Flow<SyncState> = _syncState.asStateFlow()

    override suspend fun getPendingPostsCount(): Int {
        return dao.getPendingPostsCount()
    }

    override suspend fun retryFailedSync() {
        _syncState.value = SyncState.SYNCING
        var hasErrors = false

        val pendingPosts = dao.getPendingPosts()
        pendingPosts.forEach { post ->
            try {
                when (post.syncState) {
                    SyncState.PENDING -> {
                        val response = PostsApi.service.save(post.toDto())
                        if (response.isSuccessful) {
                            val updatedPost = response.body()
                            if (updatedPost != null) {
                                val existing = dao.getPostByServerId(updatedPost.id)
                                if (existing != null) {
                                    dao.update(
                                        PostEntity.updateFromDto(
                                            existingEntity = existing,
                                            dto = updatedPost.copy(
                                                shares = post.shares,
                                                video = post.video
                                            ),
                                            syncState = SyncState.SYNCED
                                        )
                                    )
                                } else {
                                    dao.insert(PostEntity.fromDto(updatedPost, SyncState.SYNCED))
                                }
                                dao.delete(post)
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
    }

    override suspend fun checkForNewPosts(afterId: Long): Int {
        return try {
            val response = PostsApi.service.getNewer(afterId)
            if (response.isSuccessful) {
                val body = response.body() ?: return 0

                val existingPosts = mutableMapOf<Long, PostEntity>()
                dao.getAllSync().forEach { post ->
                    post.serverId?.let { existingPosts[it] = post }
                }

                val postsToInsert = mutableListOf<PostEntity>()

                body.forEach { serverPost ->
                    if (existingPosts[serverPost.id] == null) {
                        postsToInsert.add(PostEntity.fromDto(serverPost, SyncState.SYNCED, isNew = true))
                    }
                }

                if (postsToInsert.isNotEmpty()) {
                    dao.insert(postsToInsert)
                }

                body.size
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e("PostRepository", "Error checking new posts", e)
            0
        }
    }

    override suspend fun getNewPostsCount(): Int = dao.getNewPostsCount()

    override suspend fun showNewPosts() = dao.markAllAsVisible()

    override suspend fun dislikeById(id: Long): Post = likeById(id)

    override suspend fun shareById(id: Long): Post {
        val entity = findPostEntityById(id)
            ?: throw IllegalArgumentException("Post with id $id not found")

        val updatedEntity = entity.copy(
            shares = entity.shares + 1,
            syncState = SyncState.PENDING,
            lastModified = System.currentTimeMillis()
        )

        dao.update(updatedEntity)

        return if (entity.serverId != null) {
            try {
                val serverPost = PostsApi.service.shareById(entity.serverId)
                val syncedEntity = updatedEntity.copy(
                    syncState = SyncState.SYNCED,
                    retryCount = 0
                )
                dao.update(syncedEntity)
                serverPost
            } catch (e: Exception) {
                Log.e("PostRepository", "Share error", e)
                _syncState.value = SyncState.FAILED
                updatedEntity.toDto()
            }
        } else {
            updatedEntity.toDto()
        }
    }

    override val newPostsCount: Flow<Int> = flow {
        while (true) {
            delay(30_000L)
            emit(dao.getNewPostsCount())
        }
    }.flowOn(Dispatchers.Default)

    override suspend fun save(post: Post): Post {
        val existingEntity = if (post.id != 0L) {
            dao.getPostByServerId(post.id)
        } else {
            null
        }

        val entity = if (existingEntity != null) {
            PostEntity.updateFromDto(
                existingEntity = existingEntity,
                dto = post,
                syncState = SyncState.PENDING
            )
        } else {
            PostEntity.fromDto(post, SyncState.PENDING)
        }

        val localId = if (existingEntity == null) {
            dao.insert(entity)
            dao.findLastEditedPost()?.id ?: 0L
        } else {
            dao.update(entity)
            existingEntity.id
        }

        return try {
            // 👇 ИСПРАВЛЕНО: добавляем authorId при создании Post для сервера
            val postForServer = if (post.id == 0L) {
                Post(
                    id = 0L,
                    author = post.author.ifBlank { "Пользователь" },
                    authorId = post.authorId,  // 👈 добавляем
                    authorAvatar = post.authorAvatar,
                    content = post.content,
                    published = post.published,
                    likedByMe = post.likedByMe,
                    likes = post.likes,
                    attachment = post.attachment
                )
            } else {
                Post(
                    id = post.id,
                    author = post.author,
                    authorId = post.authorId,  // 👈 добавляем
                    authorAvatar = post.authorAvatar,
                    content = post.content,
                    published = post.published,
                    likedByMe = post.likedByMe,
                    likes = post.likes,
                    attachment = post.attachment
                )
            }

            val response = PostsApi.service.save(postForServer)
            if (!response.isSuccessful) {
                throw ApiError(response.code(), response.message())
            }

            val serverPost = response.body() ?: throw ApiError(response.code(), "Empty body")

            val syncedEntity = if (existingEntity != null) {
                PostEntity.updateFromDto(
                    existingEntity = existingEntity,
                    dto = serverPost.copy(
                        shares = post.shares,
                        video = post.video
                    ),
                    syncState = SyncState.SYNCED
                )
            } else {
                PostEntity.fromDto(
                    dto = serverPost.copy(
                        shares = post.shares,
                        video = post.video
                    ),
                    syncState = SyncState.SYNCED
                )
            }

            if (existingEntity == null) {
                dao.delete(entity)
                dao.insert(syncedEntity)
            } else {
                dao.update(syncedEntity)
            }

            serverPost
        } catch (e: Exception) {
            Log.e("PostRepository", "Save error", e)
            _syncState.value = SyncState.FAILED
            post.copy(id = localId)
        }
    }

    override suspend fun removeById(id: Long) {
        val entity = findPostEntityById(id) ?: return

        if (entity.serverId == null) {
            dao.delete(entity)
            return
        }

        try {
            val response = PostsApi.service.removeById(entity.serverId)
            if (response.isSuccessful) {
                dao.delete(entity)
            } else {
                val updated = entity.copy(
                    syncState = SyncState.PENDING_DELETE,
                    retryCount = entity.retryCount + 1
                )
                dao.insert(updated)
                _syncState.value = SyncState.FAILED
            }
        } catch (e: Exception) {
            Log.e("PostRepository", "Delete error", e)
            val updated = entity.copy(
                syncState = SyncState.PENDING_DELETE,
                retryCount = entity.retryCount + 1
            )
            dao.insert(updated)
            _syncState.value = SyncState.FAILED
        }
    }

    override suspend fun likeById(id: Long): Post {
        val entity = findPostEntityById(id) ?: throw IllegalArgumentException("Post not found")
        val postDto = entity.toDto()

        val updatedDto = postDto.copy(
            likedByMe = !postDto.likedByMe,
            likes = if (postDto.likedByMe) postDto.likes - 1 else postDto.likes + 1
        )

        val updatedEntity = entity.copy(
            likedByMe = updatedDto.likedByMe,
            likes = updatedDto.likes,
            syncState = if (entity.serverId != null) SyncState.PENDING else SyncState.SYNCED,
            lastModified = System.currentTimeMillis()
        )

        dao.update(updatedEntity)

        if (entity.serverId == null) {
            return updatedDto
        }

        return try {
            val serverResponse = if (postDto.likedByMe) {
                PostsApi.service.dislikeById(entity.serverId)
            } else {
                PostsApi.service.likeById(entity.serverId)
            }

            val serverBody = serverResponse.body() ?: updatedDto

            val syncedEntity = updatedEntity.copy(
                syncState = SyncState.SYNCED,
                retryCount = 0,
                likes = serverBody.likes,
                likedByMe = serverBody.likedByMe
            )
            dao.update(syncedEntity)

            serverBody
        } catch (e: Exception) {
            Log.e("PostRepository", "Like/Dislike error", e)
            _syncState.value = SyncState.FAILED
            updatedDto
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
}