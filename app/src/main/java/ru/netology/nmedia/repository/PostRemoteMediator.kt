package ru.netology.nmedia.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import ru.netology.nmedia.api.ApiService
import ru.netology.nmedia.dao.PostDao
import ru.netology.nmedia.dao.PostRemoteKeyDao
import ru.netology.nmedia.db.AppDb
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.entity.PostEntity
import ru.netology.nmedia.entity.PostRemoteKeyEntity
import ru.netology.nmedia.entity.toEntity
import ru.netology.nmedia.error.ApiError

@OptIn(ExperimentalPagingApi::class)
class PostRemoteMediator(
    private val service: ApiService,
    private val db: AppDb,
    private val postDao: PostDao,
    private val postRemoteKeyDao: PostRemoteKeyDao,
) : RemoteMediator<Int, PostEntity>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, PostEntity>
    ): MediatorResult {
        return try {
            when (loadType) {
                LoadType.REFRESH -> handleRefresh(state)
                LoadType.PREPEND -> handlePrepend()
                LoadType.APPEND -> handleAppend(state)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            MediatorResult.Error(e)
        }
    }

    private suspend fun handleRefresh(state: PagingState<Int, PostEntity>): MediatorResult {
        val topPostKey = postRemoteKeyDao.getTopKey()

        return if (topPostKey == null) {
            loadInitialPosts(state.config.pageSize)
        } else {
            loadNewPostsAfter(topPostKey, state.config.pageSize)
        }
    }

    private suspend fun loadInitialPosts(pageSize: Int): MediatorResult {
        val response = service.getLatest(pageSize)
        val posts = validateAndConvertResponse(response)

        if (posts.isEmpty()) {
            return MediatorResult.Success(endOfPaginationReached = true)
        }

        db.withTransaction {
            savePostsToDatabase(posts)
            saveTopAndBottomKeys(posts.first().id, posts.last().id)
        }

        return MediatorResult.Success(endOfPaginationReached = false)
    }

    private suspend fun loadNewPostsAfter(topPostKey: Long, pageSize: Int): MediatorResult {
        val response = service.getAfter(topPostKey, pageSize)
        val posts = validateAndConvertResponse(response)

        if (posts.isEmpty()) {
            return MediatorResult.Success(endOfPaginationReached = true)
        }

        db.withTransaction {
            savePostsToDatabase(posts)
            updateTopKey(posts.first().id)
        }

        return MediatorResult.Success(endOfPaginationReached = false)
    }

    private fun handlePrepend(): MediatorResult {
        // PREPEND отключен - не загружаем данные при скролле вверх
        return MediatorResult.Success(endOfPaginationReached = true)
    }

    private suspend fun handleAppend(state: PagingState<Int, PostEntity>): MediatorResult {
        val bottomPostKey = postRemoteKeyDao.getBottomKey()
            ?: return MediatorResult.Success(endOfPaginationReached = true)

        val response = service.getBefore(bottomPostKey, state.config.pageSize)
        val posts = validateAndConvertResponse(response)

        if (posts.isEmpty()) {
            return MediatorResult.Success(endOfPaginationReached = true)
        }

        db.withTransaction {
            savePostsToDatabase(posts)
            updateBottomKey(posts.last().id)
        }

        return MediatorResult.Success(endOfPaginationReached = false)
    }

    // MARK: - Database Operations

    private suspend fun savePostsToDatabase(posts: List<PostEntity>) {
        postDao.insert(posts)
    }

    private suspend fun saveTopAndBottomKeys(topKey: Long, bottomKey: Long) {
        postRemoteKeyDao.insert(
            PostRemoteKeyEntity(
                type = PostRemoteKeyEntity.KeyType.TOP,
                key = topKey,
            )
        )
        postRemoteKeyDao.insert(
            PostRemoteKeyEntity(
                type = PostRemoteKeyEntity.KeyType.BOTTOM,
                key = bottomKey,
            )
        )
    }

    private suspend fun updateTopKey(topKey: Long) {
        postRemoteKeyDao.insert(
            PostRemoteKeyEntity(
                type = PostRemoteKeyEntity.KeyType.TOP,
                key = topKey,
            )
        )
    }

    private suspend fun updateBottomKey(bottomKey: Long) {
        postRemoteKeyDao.insert(
            PostRemoteKeyEntity(
                type = PostRemoteKeyEntity.KeyType.BOTTOM,
                key = bottomKey,
            )
        )
    }

    // MARK: - Response Validation

    private suspend fun validateAndConvertResponse(response: Response<List<Post>>): List<PostEntity> {
        if (!response.isSuccessful) {
            throw ApiError(response.code(), response.message())
        }

        val posts = response.body() ?: throw ApiError(
            response.code(),
            response.message(),
        )

        return posts.toEntity()
    }

    override suspend fun initialize(): InitializeAction {
        return InitializeAction.LAUNCH_INITIAL_REFRESH
    }
}