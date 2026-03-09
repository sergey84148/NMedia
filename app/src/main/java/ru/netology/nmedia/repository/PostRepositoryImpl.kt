package ru.netology.nmedia.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.netology.nmedia.api.PostsApi
import ru.netology.nmedia.dao.PostDao
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.entity.PostEntity
import ru.netology.nmedia.enumeration.SyncState
import ru.netology.nmedia.utils.RetryPolicy

class PostRepositoryImpl(
    private val dao: PostDao,
) : PostRepository {

    private val _syncState = MutableStateFlow(SyncState.SYNCED)
    override suspend fun getSyncState(): Flow<SyncState> = _syncState

    override val data: Flow<List<Post>>
        get() = throw UnsupportedOperationException("Use dataLive instead")

    override val dataLive: LiveData<List<Post>> = dao.getAll()
        .map { entities -> entities.map { it.toDto() } }

    override suspend fun getAllAsync() {
        try {
            _syncState.value = SyncState.SYNCING
            Log.d("PostRepository", "Loading posts from server")
            val posts = PostsApi.retrofitService.getAll()
            Log.d("PostRepository", "Loaded ${posts.size} posts from server")

            // Получаем текущие PENDING посты, чтобы не затереть их
            val pendingPosts = dao.getPostsBySyncStates(
                listOf(SyncState.PENDING, SyncState.FAILED, SyncState.PENDING_DELETE)
            )
            val pendingServerIds = pendingPosts.mapNotNull { it.serverId }.toSet()
            Log.d("PostRepository", "Pending server IDs: $pendingServerIds")

            // Фильтруем посты с сервера, исключая те, что есть в PENDING
            val postsToInsert = posts
                .filter { !pendingServerIds.contains(it.id) }
                .map { PostEntity.fromDto(it, SyncState.SYNCED) }

            if (postsToInsert.isNotEmpty()) {
                dao.insert(postsToInsert)
                Log.d("PostRepository", "Inserted ${postsToInsert.size} posts from server")
            }

            _syncState.value = SyncState.SYNCED
        } catch (e: Exception) {
            _syncState.value = SyncState.FAILED
            Log.e("PostRepository", "Error loading posts", e)
        }
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

        try {
            val serverPost = if (entity.likedByMe) {
                PostsApi.retrofitService.dislikeById(entity.serverId ?: id)
            } else {
                PostsApi.retrofitService.likeById(entity.serverId ?: id)
            }

            dao.update(
                updatedEntity.copy(
                    syncState = SyncState.SYNCED,
                    retryCount = 0
                )
            )
            return serverPost
        } catch (e: Exception) {
            Log.e("PostRepository", "Like/Dislike API error", e)
            _syncState.value = SyncState.FAILED
            return updatedEntity.toDto()
        }
    }

    override suspend fun dislikeById(id: Long): Post = likeById(id)

    override suspend fun removeById(id: Long) {
        val entity = findPostById(id)
            ?: throw IllegalArgumentException("Post with id $id not found")

        Log.d("PostRepository", "Removing post: localId=${entity.id}, serverId=${entity.serverId}")

        val originalEntity = entity.copy()

        if (entity.serverId == null) {
            dao.delete(entity)
            return
        }

        dao.delete(entity)

        try {
            PostsApi.retrofitService.removeById(entity.serverId)
            Log.d("PostRepository", "Successfully deleted from server")
        } catch (e: Exception) {
            Log.e("PostRepository", "Delete API error", e)
            dao.insert(
                originalEntity.copy(
                    syncState = SyncState.PENDING_DELETE,
                    lastModified = System.currentTimeMillis(),
                    retryCount = originalEntity.retryCount + 1
                )
            )
            _syncState.value = SyncState.FAILED
        }
    }

    override suspend fun save(post: Post): Post {
        Log.d("PostRepository", "=== SAVE OPERATION START ===")
        Log.d("PostRepository", "Post to save: $post")
        Log.d("PostRepository", "Post ID: ${post.id}, isNew: ${post.id == 0L}")

        // 1. СНАЧАЛА сохраняем локально
        val entity = PostEntity.fromDto(post, SyncState.PENDING)

        val newId: Long = (if (post.id == 0L) {
            Log.d("PostRepository", "Inserting new post locally")
            val id = dao.insert(entity.copy(serverId = null))
            Log.d("PostRepository", "New local ID: $id")
            id
        } else {
            Log.d("PostRepository", "Updating existing post locally")
            dao.update(entity)
            Log.d("PostRepository", "Updated post with ID: ${post.id}")
            post.id
        }) as Long

        // Для нового поста отправляем id = 0, для существующего - реальный id
        val postForServer = post.copy(id = post.id)

        Log.d("PostRepository", "Sending to server: $postForServer")

        // 2. ЗАТЕМ отправляем на сервер
        return try {
            Log.d("PostRepository", "Sending to server: $postForServer")
            val serverPost = PostsApi.retrofitService.save(postForServer)
            Log.d("PostRepository", "Server response SUCCESS: $serverPost")
            Log.d("PostRepository", "Server post ID: ${serverPost.id}")

            // Создаем синхронизированную сущность
            val syncedEntity = PostEntity.fromDto(serverPost, SyncState.SYNCED)

            // Для существующего поста: обновляем существующую запись
            Log.d("PostRepository", "Processing UPDATE - updating existing post")
            dao.update(syncedEntity)
            Log.d("PostRepository", "Updated post with serverId: ${syncedEntity.serverId}")

            Log.d("PostRepository", "=== SAVE OPERATION SUCCESS ===")
            serverPost
        } catch (e: Exception) {
            Log.e("PostRepository", "=== SAVE OPERATION FAILED ===")
            Log.e("PostRepository", "Save API error", e)
            _syncState.value = SyncState.FAILED
            post.copy(id = newId) // Возвращаем пост с локальным ID
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

        try {
            val serverPost = PostsApi.retrofitService.shareById(entity.serverId ?: id)
            dao.update(
                updatedEntity.copy(
                    syncState = SyncState.SYNCED,
                    retryCount = 0
                )
            )
            return serverPost
        } catch (e: Exception) {
            Log.e("PostRepository", "Share API error", e)
            _syncState.value = SyncState.FAILED
            return updatedEntity.toDto()
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
                            dao.delete(local)
                            Log.d("PostRepository", "DELETE successful for post: ${local.id}")
                        }
                    }
                    local.serverId == null -> {
                        Log.d("PostRepository", "Processing NEW post: ${local.id}")
                        val created = PostsApi.retrofitService.save(local.toDto())
                        Log.d("PostRepository", "Created post with serverId: ${created.id}")

                        dao.delete(local)
                        dao.insert(PostEntity.fromDto(created, SyncState.SYNCED))
                        Log.d("PostRepository", "NEW post sync completed")
                    }
                    else -> {
                        Log.d("PostRepository", "Processing UPDATE for post: ${local.id}")

                        if (local.likedByMe != local.toDto().likedByMe) {
                            if (local.likedByMe) {
                                PostsApi.retrofitService.likeById(local.serverId)
                            } else {
                                PostsApi.retrofitService.dislikeById(local.serverId)
                            }
                        }
                        if (local.shares != local.toDto().shares) {
                            PostsApi.retrofitService.shareById(local.serverId)
                        }

                        val updated = PostsApi.retrofitService.update(local.serverId, local.toDto())
                        dao.update(PostEntity.fromDto(updated, SyncState.SYNCED))
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
        val lastSync = dao.getLastSyncTime() ?: 0L
        Log.d("PostRepository", "Fetching changes since: $lastSync")

        val changes = PostsApi.retrofitService.getChanges(lastSync)
        Log.d("PostRepository", "Received ${changes.posts.size} changed posts, ${changes.deletedIds.size} deleted IDs")

        changes.posts.forEach { serverPost ->
            val local = dao.getPostByServerId(serverPost.id)
            when {
                local == null -> {
                    Log.d("PostRepository", "New post from server: ${serverPost.id}")
                    dao.insert(PostEntity.fromDto(serverPost, SyncState.SYNCED))
                }
                serverPost.id > local.lastModified && local.syncState == SyncState.SYNCED -> {
                    Log.d("PostRepository", "Updating post from server: ${serverPost.id}")
                    dao.update(PostEntity.fromDto(serverPost, SyncState.SYNCED))
                }
            }
        }

        changes.deletedIds.forEach { serverId ->
            Log.d("PostRepository", "Deleting post with serverId: $serverId")
            dao.deleteByServerId(serverId)
        }
    }

    private suspend fun handleSyncError(entity: PostEntity) {
        val newRetryCount = entity.retryCount + 1
        Log.d("PostRepository", "Handling sync error for post ${entity.id}, retry count: $newRetryCount")

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