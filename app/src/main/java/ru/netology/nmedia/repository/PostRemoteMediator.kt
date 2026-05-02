package ru.netology.nmedia.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import ru.netology.nmedia.api.ApiService
import ru.netology.nmedia.dao.PostDao
import ru.netology.nmedia.dao.PostRemoteKeyDao
import ru.netology.nmedia.db.AppDb
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
        try {
            when (loadType) {
                LoadType.REFRESH -> {
                    // REFRESH - добавляем новые посты сверху, а не затираем
                    val topPostKey = postRemoteKeyDao.getTopKey()
                    if (topPostKey == null) {
                        // Если ключа нет, загружаем первые посты
                        val response = service.getLatest(state.config.pageSize)
                        if (!response.isSuccessful) {
                            throw ApiError(response.code(), response.message())
                        }
                        val body = response.body() ?: throw ApiError(
                            response.code(),
                            response.message(),
                        )
                        if (body.isEmpty()) {
                            return MediatorResult.Success(endOfPaginationReached = true)
                        }
                        db.withTransaction {
                            postDao.insert(body.toEntity())
                            postRemoteKeyDao.insert(
                                PostRemoteKeyEntity(
                                    type = PostRemoteKeyEntity.KeyType.TOP,
                                    key = body.first().id,
                                )
                            )
                            postRemoteKeyDao.insert(
                                PostRemoteKeyEntity(
                                    type = PostRemoteKeyEntity.KeyType.BOTTOM,
                                    key = body.last().id,
                                )
                            )
                        }
                        return MediatorResult.Success(endOfPaginationReached = false)
                    }

                    // Загружаем новые посты после текущего верхнего
                    val response = service.getAfter(topPostKey, state.config.pageSize)

                    if (!response.isSuccessful) {
                        throw ApiError(response.code(), response.message())
                    }
                    val body = response.body() ?: throw ApiError(
                        response.code(),
                        response.message(),
                    )

                    if (body.isEmpty()) {
                        return MediatorResult.Success(endOfPaginationReached = true)
                    }

                    db.withTransaction {
                        // Добавляем новые посты, не удаляя старые
                        postDao.insert(body.toEntity())

                        // Обновляем ключ для верхнего поста
                        postRemoteKeyDao.insert(
                            PostRemoteKeyEntity(
                                type = PostRemoteKeyEntity.KeyType.TOP,
                                key = body.first().id,
                            )
                        )
                    }
                    return MediatorResult.Success(endOfPaginationReached = false)
                }

                LoadType.PREPEND -> {
                    // PREPEND отключен - не загружаем данные при скролле вверх
                    return MediatorResult.Success(endOfPaginationReached = true)
                }

                LoadType.APPEND -> {
                    // APPEND работает в обычном режиме
                    val bottomPostKey = postRemoteKeyDao.getBottomKey()
                        ?: return MediatorResult.Success(endOfPaginationReached = true)

                    val response = service.getBefore(bottomPostKey, state.config.pageSize)

                    if (!response.isSuccessful) {
                        throw ApiError(response.code(), response.message())
                    }
                    val body = response.body() ?: throw ApiError(
                        response.code(),
                        response.message(),
                    )

                    if (body.isEmpty()) {
                        return MediatorResult.Success(endOfPaginationReached = true)
                    }

                    db.withTransaction {
                        postDao.insert(body.toEntity())

                        // Обновляем ключ для нижнего поста
                        postRemoteKeyDao.insert(
                            PostRemoteKeyEntity(
                                type = PostRemoteKeyEntity.KeyType.BOTTOM,
                                key = body.last().id,
                            )
                        )
                    }
                    return MediatorResult.Success(endOfPaginationReached = false)
                }
            }
            return MediatorResult.Success(endOfPaginationReached = true)
        } catch (e: Exception) {
            if (e is CancellationException) {
                throw e
            }
            return MediatorResult.Error(e)
        }
    }

    override suspend fun initialize(): InitializeAction {
        return InitializeAction.LAUNCH_INITIAL_REFRESH
    }
}