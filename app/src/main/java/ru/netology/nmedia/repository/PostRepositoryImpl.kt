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
            val posts = PostsApi.retrofitService.getAll()
            dao.insert(posts.map { PostEntity.fromDto(it, SyncState.SYNCED) })
            _syncState.value = SyncState.SYNCED
        } catch (e: Exception) {
            _syncState.value = SyncState.FAILED
            Log.e("PostRepository", "Error loading posts", e)
        }
    }

    override suspend fun likeById(id: Long): Post {
        return try {
            val serverPost = PostsApi.retrofitService.likeById(id)
            val entity = dao.getPostByServerId(id)
            if (entity != null) {
                dao.update(
                    entity.copy(
                        likedByMe = true,
                        likes = entity.likes + 1,
                        syncState = SyncState.SYNCED
                    )
                )
            }
            serverPost
        } catch (e: Exception) {
            val entity = dao.getPostByServerId(id)
            if (entity != null) {
                dao.update(
                    entity.copy(
                        likedByMe = true,
                        likes = entity.likes + 1,
                        syncState = SyncState.PENDING,
                        lastModified = System.currentTimeMillis()
                    )
                )
                entity.toDto().copy(likedByMe = true, likes = entity.likes + 1)
            } else {
                throw e
            }
        }
    }

    override suspend fun dislikeById(id: Long): Post {
        return try {
            val serverPost = PostsApi.retrofitService.dislikeById(id)
            val entity = dao.getPostByServerId(id)
            if (entity != null) {
                dao.update(
                    entity.copy(
                        likedByMe = false,
                        likes = entity.likes - 1,
                        syncState = SyncState.SYNCED
                    )
                )
            }
            serverPost
        } catch (e: Exception) {
            val entity = dao.getPostByServerId(id)
            if (entity != null) {
                dao.update(
                    entity.copy(
                        likedByMe = false,
                        likes = entity.likes - 1,
                        syncState = SyncState.PENDING,
                        lastModified = System.currentTimeMillis()
                    )
                )
                entity.toDto().copy(likedByMe = false, likes = entity.likes - 1)
            } else {
                throw e
            }
        }
    }

    override suspend fun save(post: Post): Post {
        return try {
            val serverPost = PostsApi.retrofitService.save(post)
            val entity = PostEntity.fromDto(serverPost, SyncState.SYNCED)
            if (post.id == 0L) {
                dao.insert(entity)
            } else {
                dao.update(entity)
            }
            serverPost
        } catch (e: Exception) {
            val entity = PostEntity.fromDto(post, SyncState.PENDING)
            val newId = if (post.id == 0L) {
                dao.insert(entity.copy(serverId = null))
            } else {
                dao.update(entity)
                post.id
            }
            // ИСПРАВЛЕНО: убран as Long
            Post(
                id = newId as Long,
                author = post.author,
                authorAvatar = post.authorAvatar,
                content = post.content,
                published = post.published,
                likedByMe = post.likedByMe,
                likes = post.likes,
                shares = post.shares,
                video = post.video
            )
        }
    }

    override suspend fun removeById(id: Long) {
        Log.d("PostRepository", "removeById called with id: $id")

        try {
            // Ищем пост сначала по serverId, потом по локальному id
            var entity = dao.getPostByServerId(id)

            if (entity == null) {
                // Если не нашли по serverId, ищем по локальному id
                entity = dao.getById(id)
                Log.d("PostRepository", "Searching by local id, found: ${entity != null}")
            } else {
                Log.d("PostRepository", "Found by serverId")
            }

            if (entity == null) {
                Log.e("PostRepository", "Post with id $id not found in database")
                return
            }

            Log.d("PostRepository", "Found post: localId=${entity.id}, serverId=${entity.serverId}, state=${entity.syncState}")

            // Пробуем удалить на сервере
            try {
                if (entity.serverId != null) {
                    PostsApi.retrofitService.removeById(entity.serverId)
                    Log.d("PostRepository", "Deleted from server with serverId: ${entity.serverId}")
                } else {
                    Log.d("PostRepository", "Post has no serverId, deleting locally only")
                }

                // Удаляем из БД
                dao.delete(entity)
                Log.d("PostRepository", "Deleted from database")

            } catch (e: Exception) {
                Log.e("PostRepository", "Server delete failed, marking as PENDING_DELETE", e)
                // Если сервер недоступен - помечаем на удаление
                dao.update(
                    entity.copy(
                        syncState = SyncState.PENDING_DELETE,
                        lastModified = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("PostRepository", "Error in removeById", e)
        }
    }

    override suspend fun shareById(id: Long): Post {
        return try {
            val serverPost = PostsApi.retrofitService.shareById(id)
            val entity = dao.getPostByServerId(id)
            if (entity != null) {
                dao.update(
                    entity.copy(
                        shares = entity.shares + 1,
                        syncState = SyncState.SYNCED
                    )
                )
            }
            serverPost
        } catch (e: Exception) {
            val entity = dao.getPostByServerId(id)
            if (entity != null) {
                dao.update(
                    entity.copy(
                        shares = entity.shares + 1,
                        syncState = SyncState.PENDING,
                        lastModified = System.currentTimeMillis()
                    )
                )
                entity.toDto().copy(shares = entity.shares + 1)
            } else {
                throw e
            }
        }
    }

    override suspend fun syncWithServer() {
        if (_syncState.value == SyncState.SYNCING) return
        _syncState.value = SyncState.SYNCING
        try {
            sendLocalChanges()
            fetchServerChanges()
            _syncState.value = SyncState.SYNCED
        } catch (e: Exception) {
            _syncState.value = SyncState.FAILED
            Log.e("PostRepository", "Sync error", e)
        }
    }

    private suspend fun sendLocalChanges() {
        val pendingPosts = dao.getPostsBySyncStates(
            listOf(SyncState.PENDING, SyncState.FAILED, SyncState.PENDING_DELETE)
        )
        pendingPosts.forEach { local ->
            try {
                when {
                    local.syncState == SyncState.PENDING_DELETE -> {
                        if (local.serverId != null) {
                            PostsApi.retrofitService.removeById(local.serverId)
                        }
                        dao.delete(local)
                    }
                    local.serverId == null -> {
                        val created = PostsApi.retrofitService.save(local.toDto())
                        dao.update(
                            local.copy(
                                serverId = created.id,
                                syncState = SyncState.SYNCED,
                                retryCount = 0
                            )
                        )
                    }
                    else -> {
                        PostsApi.retrofitService.update(local.serverId, local.toDto())
                        dao.update(
                            local.copy(
                                syncState = SyncState.SYNCED,
                                retryCount = 0,
                                lastModified = System.currentTimeMillis()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                handleSyncError(local)
            }
        }
    }

    private suspend fun fetchServerChanges() {
        val lastSync = dao.getLastSyncTime() ?: 0L
        val changes = PostsApi.retrofitService.getChanges(lastSync)
        changes.posts.forEach { serverPost ->
            val local = dao.getPostByServerId(serverPost.id)
            when {
                local == null -> {
                    dao.insert(PostEntity.fromDto(serverPost, SyncState.SYNCED))
                }
                serverPost.id > local.lastModified && local.syncState != SyncState.PENDING -> {
                    dao.update(PostEntity.fromDto(serverPost, SyncState.SYNCED))
                }
            }
        }
        changes.deletedIds.forEach { serverId ->
            dao.deleteByServerId(serverId)
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
        return dao.getPostsBySyncState(SyncState.PENDING).size
    }

    override suspend fun retryFailedSync() {
        val failedPosts = dao.getPostsBySyncState(SyncState.FAILED)
        failedPosts.forEach {
            dao.update(it.copy(syncState = SyncState.PENDING, retryCount = 0))
        }
        syncWithServer()
    }
}