package ru.netology.nmedia.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ru.netology.nmedia.api.PostsApi
import ru.netology.nmedia.dao.PostDao
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.entity.PostEntity
import ru.netology.nmedia.enumeration.SyncState
import ru.netology.nmedia.utils.RetryPolicy
import java.io.IOException

class PostRepositoryImpl(
    private val dao: PostDao,
) : PostRepository {

    private val _syncState = MutableStateFlow(SyncState.SYNCED)
    override suspend fun getSyncState(): Flow<SyncState> = _syncState

    // Только видимые посты (isNew = false)
    override val data: Flow<List<Post>>
        get() = dao.getAllVisible().map { entities ->
            entities.map { it.toDto() }
        }

    override suspend fun getAllAsync() {
        if (_syncState.value == SyncState.SYNCING) return

        try {
            _syncState.value = SyncState.SYNCING
            Log.d("PostRepository", "Loading posts from server")

            val posts = PostsApi.retrofitService.getAll()
            Log.d("PostRepository", "Loaded ${posts.size} posts from server")

            val pendingPosts = dao.getPostsBySyncStates(
                listOf(SyncState.PENDING, SyncState.FAILED, SyncState.PENDING_DELETE)
            )
            val pendingServerIds = pendingPosts.mapNotNull { it.serverId }.toSet()

            // Получаем ID новых постов
            val newPostsIds = dao.getNewPostsIds().toSet()

            val postsToInsert = posts
                .filter { !pendingServerIds.contains(it.id) }
                .map { dto ->
                    // Если пост уже был новым, сохраняем этот статус
                    val isNew = newPostsIds.contains(dto.id)
                    PostEntity.Companion.fromDto(dto, SyncState.SYNCED, isNew)
                }

            if (postsToInsert.isNotEmpty()) {
                dao.insert(postsToInsert)
            }

            _syncState.value = SyncState.SYNCED
        } catch (e: IOException) {
            _syncState.value = SyncState.FAILED
            Log.e("PostRepository", "Network error loading posts", e)
        } catch (e: Exception) {
            _syncState.value = SyncState.FAILED
            Log.e("PostRepository", "Error loading posts", e)
        }
    }

    override suspend fun checkForNewPosts(afterId: Long): Int {
        return try {
            Log.d("PostRepository", "Checking for new posts after ID: $afterId")

            val allPosts = PostsApi.retrofitService.getAll()
            val newPosts = allPosts.filter { it.id > afterId }

            if (newPosts.isNotEmpty()) {
                Log.d("PostRepository", "Found ${newPosts.size} new posts")

                // Сохраняем новые посты с флагом isNew = true
                val newEntities = newPosts.map { dto ->
                    PostEntity.Companion.fromDto(dto, SyncState.SYNCED, isNew = true)
                }
                dao.insert(newEntities)
            }

            newPosts.size
        } catch (e: Exception) {
            Log.e("PostRepository", "Error checking new posts", e)
            0
        }
    }

    override suspend fun getNewPostsCount(): Int {
        return dao.getNewPostsCount()
    }

    override suspend fun showNewPosts() {
        Log.d("PostRepository", "Marking all new posts as visible")
        dao.markAllAsVisible()
    }

    override suspend fun likeById(id: Long): Post {
        val entity = findPostById(id)
            ?: throw IllegalArgumentException("Post with id $id not found")

        val updatedEntity = entity.copy(
            likedByMe = !entity.likedByMe,
            likes = if (entity.likedByMe) entity.likes - 1 else entity.likes + 1,
            syncState = SyncState.PENDING,
            lastModified = System.currentTimeMillis()
        )

        dao.update(updatedEntity)

        return try {
            val serverPost = if (entity.likedByMe) {
                PostsApi.retrofitService.dislikeById(entity.serverId ?: id)
            } else {
                PostsApi.retrofitService.likeById(entity.serverId ?: id)
            }

            val syncedEntity = updatedEntity.copy(
                syncState = SyncState.SYNCED,
                retryCount = 0,
                likes = serverPost.likes,
                likedByMe = serverPost.likedByMe
            )
            dao.update(syncedEntity)

            serverPost
        } catch (e: Exception) {
            Log.e("PostRepository", "Like/Dislike API error", e)
            _syncState.value = SyncState.FAILED
            updatedEntity.toDto()
        }
    }

    override suspend fun dislikeById(id: Long): Post = likeById(id)

    override suspend fun removeById(id: Long) {
        val entity = findPostById(id)
            ?: throw IllegalArgumentException("Post with id $id not found")

        Log.d("PostRepository", "Removing post: localId=${entity.id}, serverId=${entity.serverId}")

        if (entity.serverId == null) {
            // ИСПРАВЛЕНО: используем правильный метод удаления
            dao.delete(entity)
            return
        }

        // ИСПРАВЛЕНО: сначала удаляем локально
        dao.delete(entity)

        try {
            PostsApi.retrofitService.removeById(entity.serverId)
            Log.d("PostRepository", "Successfully deleted from server")
        } catch (e: Exception) {
            Log.e("PostRepository", "Delete API error", e)
            // Восстанавливаем с пометкой на удаление
            dao.insert(
                entity.copy(
                    syncState = SyncState.PENDING_DELETE,
                    lastModified = System.currentTimeMillis(),
                    retryCount = entity.retryCount + 1
                )
            )
            _syncState.value = SyncState.FAILED
        }
    }

    override suspend fun save(post: Post): Post {
        Log.d("PostRepository", "=== SAVE OPERATION START ===")
        Log.d("PostRepository", "1. Received post: $post")

        val entity = PostEntity.Companion.fromDto(post, SyncState.PENDING)
        Log.d("PostRepository", "4. Created entity: serverId=${entity.serverId}")

        val newId: Long = if (post.id == 0L) {
            Log.d("PostRepository", "5a. Inserting new post locally")
            dao.insert(entity.copy(serverId = null))

            val lastPost = dao.findLastEditedPost()
                ?: throw IllegalStateException("Failed to get inserted post ID")
            Log.d("PostRepository", "6a. New local ID: ${lastPost.id}")
            lastPost.id
        } else {
            Log.d("PostRepository", "5b. Updating existing post locally")
            dao.update(entity)
            post.id
        }

        Log.d("PostRepository", "7. newId = $newId")

        return try {
            Log.d("PostRepository", "8. Preparing to send to server")

            val postForServer = if (post.id == 0L) {
                Post(
                    id = 0L,
                    author = post.author,
                    authorAvatar = post.authorAvatar,
                    content = post.content,
                    published = post.published,
                    likedByMe = post.likedByMe,
                    likes = post.likes,
                    shares = post.shares,
                    video = post.video,
                    attachment = post.attachment
                ).also {
                    Log.d("PostRepository", "9a. Created new post for server: $it")
                }
            } else {
                post.also {
                    Log.d("PostRepository", "9b. Using existing post for server: $it")
                }
            }

            Log.d("PostRepository", "11. Calling API with post id: ${postForServer.id}")

            val serverPost = PostsApi.retrofitService.save(postForServer)
            Log.d("PostRepository", "12. Server response: $serverPost")
            Log.d("PostRepository", "13. Server post id: ${serverPost.id}")

            val syncedEntity = PostEntity.Companion.fromDto(serverPost, SyncState.SYNCED)
            Log.d("PostRepository", "14. Created synced entity with serverId=${syncedEntity.serverId}")

            if (post.id == 0L) {
                Log.d("PostRepository", "15a. Deleting temporary post with local ID: $newId")
                val entityToDelete = dao.getById(newId) ?: entity
                // ИСПРАВЛЕНО: используем правильный метод удаления
                dao.delete(entityToDelete)
                dao.insert(syncedEntity)
                Log.d("PostRepository", "16a. Inserted synced post")
            } else {
                Log.d("PostRepository", "15b. Updating existing post")
                dao.update(syncedEntity)
                Log.d("PostRepository", "16b. Updated post")
            }

            Log.d("PostRepository", "=== SAVE OPERATION SUCCESS ===")
            serverPost
        } catch (e: Exception) {
            Log.e("PostRepository", "=== SAVE OPERATION FAILED ===")
            Log.e("PostRepository", "Error type: ${e::class.simpleName}")
            Log.e("PostRepository", "Error message: ${e.message}")
            e.printStackTrace()
            _syncState.value = SyncState.FAILED
            post.copy(id = newId)
        }
    }

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
            val serverPost = PostsApi.retrofitService.shareById(entity.serverId ?: id)

            val syncedEntity = updatedEntity.copy(
                syncState = SyncState.SYNCED,
                retryCount = 0,
                shares = serverPost.shares
            )
            dao.update(syncedEntity)

            serverPost
        } catch (e: Exception) {
            Log.e("PostRepository", "Share API error", e)
            _syncState.value = SyncState.FAILED
            updatedEntity.toDto()
        }
    }

    override suspend fun syncWithServer() {
        if (_syncState.value == SyncState.SYNCING) return

        _syncState.value = SyncState.SYNCING
        try {
            Log.d("PostRepository", "Starting sync with server")
            sendLocalChanges()
            fetchServerChanges()
            _syncState.value = SyncState.SYNCED
            Log.d("PostRepository", "Sync completed successfully")
        } catch (e: Exception) {
            _syncState.value = SyncState.FAILED
            Log.e("PostRepository", "Sync error", e)
        }
    }

    private suspend fun sendLocalChanges() {
        val pendingPosts = dao.getPostsBySyncStates(
            listOf(SyncState.PENDING, SyncState.FAILED, SyncState.PENDING_DELETE)
        )

        Log.d("PostRepository", "Found ${pendingPosts.size} pending posts for sync")

        pendingPosts.forEach { local ->
            try {
                when {
                    local.syncState == SyncState.PENDING_DELETE -> {
                        Log.d("PostRepository", "Processing DELETE for post: ${local.id}")
                        if (local.serverId != null) {
                            PostsApi.retrofitService.removeById(local.serverId)
                            // ИСПРАВЛЕНО: используем правильный метод удаления
                            dao.delete(local)
                            Log.d("PostRepository", "DELETE successful for post: ${local.id}")
                        }
                    }
                    local.serverId == null -> {
                        Log.d("PostRepository", "Processing NEW post: ${local.id}")
                        val dto = local.toDto()
                        val postForServer = Post(
                            id = 0L,
                            author = dto.author,
                            authorAvatar = dto.authorAvatar,
                            content = dto.content,
                            published = dto.published,
                            likedByMe = dto.likedByMe,
                            likes = dto.likes,
                            shares = dto.shares,
                            video = dto.video
                        )
                        Log.d("PostRepository", "Post for server: $postForServer")

                        val created = PostsApi.retrofitService.save(postForServer)
                        Log.d("PostRepository", "Created post with serverId: ${created.id}")

                        // ИСПРАВЛЕНО: используем правильный метод удаления
                        dao.delete(local)
                        dao.insert(PostEntity.Companion.fromDto(created, SyncState.SYNCED))
                        Log.d("PostRepository", "NEW post sync completed")
                    }
                    else -> {
                        Log.d("PostRepository", "Processing UPDATE for post: ${local.id}")
                        val updated = PostsApi.retrofitService.save(local.toDto())
                        dao.update(PostEntity.Companion.fromDto(updated, SyncState.SYNCED))
                        Log.d("PostRepository", "UPDATE sync completed for post: ${local.id}")
                    }
                }
            } catch (e: Exception) {
                Log.e("PostRepository", "Error processing post ${local.id}", e)
                handleSyncError(local)
            }
        }
    }

    private suspend fun fetchServerChanges() {
        try {
            val serverPosts = PostsApi.retrofitService.getAll()
            val newPostsIds = dao.getNewPostsIds().toSet()

            val localPosts = dao.getPostsBySyncStates(
                listOf(SyncState.SYNCED, SyncState.PENDING, SyncState.FAILED)
            )
            val localPostsMap = localPosts.mapNotNull { it.serverId?.let { id -> id to it } }.toMap()

            serverPosts.forEach { serverPost ->
                val localPost = localPostsMap[serverPost.id]
                when {
                    localPost == null -> {
                        // Новый пост с сервера - сохраняем как isNew = true
                        dao.insert(PostEntity.Companion.fromDto(serverPost, SyncState.SYNCED, isNew = true))
                    }
                    localPost.syncState == SyncState.SYNCED -> {
                        // Обновляем существующий пост, сохраняя его статус новизны
                        val isNew = newPostsIds.contains(serverPost.id)
                        dao.update(PostEntity.Companion.fromDto(serverPost, SyncState.SYNCED, isNew))
                    }
                }
            }

            val serverPostIds = serverPosts.map { it.id }.toSet()
            localPostsMap.keys.forEach { serverId ->
                if (!serverPostIds.contains(serverId)) {
                    val localPost = localPostsMap[serverId]
                    if (localPost != null && localPost.syncState != SyncState.PENDING_DELETE) {
                        dao.deleteByServerId(serverId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PostRepository", "Error fetching server changes", e)
            throw e
        }
    }

    private suspend fun handleSyncError(entity: PostEntity) {
        val newRetryCount = entity.retryCount + 1

        if (RetryPolicy.canRetry(newRetryCount)) {
            dao.update(entity.copy(retryCount = newRetryCount))
        } else {
            dao.update(
                entity.copy(
                    syncState = SyncState.FAILED,
                    retryCount = newRetryCount
                )
            )
        }
    }

    override suspend fun getPendingPostsCount(): Int {
        return dao.getPostsBySyncState(SyncState.PENDING).size +
                dao.getPostsBySyncState(SyncState.PENDING_DELETE).size +
                dao.getPostsBySyncState(SyncState.FAILED).size
    }

    override suspend fun retryFailedSync() {
        Log.d("PostRepository", "Retrying failed sync")
        val failedPosts = dao.getPostsBySyncState(SyncState.FAILED)
        failedPosts.forEach {
            dao.update(it.copy(syncState = SyncState.PENDING, retryCount = 0))
        }
        syncWithServer()
    }

    private suspend fun findPostById(id: Long): PostEntity? {
        return dao.getPostByServerId(id) ?: dao.getById(id)
    }
}