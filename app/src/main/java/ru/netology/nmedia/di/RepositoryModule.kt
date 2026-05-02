package ru.netology.nmedia.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.netology.nmedia.api.ApiService
import ru.netology.nmedia.dao.PostDao
import ru.netology.nmedia.dao.PostRemoteKeyDao
import ru.netology.nmedia.db.AppDb
import ru.netology.nmedia.repository.PostRepository
import ru.netology.nmedia.repository.PostRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun providePostRepository(
        postDao: PostDao,
        postRemoteKeyDao: PostRemoteKeyDao,
        appDb: AppDb,
        apiService: ApiService
    ): PostRepository {
        return PostRepositoryImpl(
            dao = postDao,
            postRemoteKeyDao = postRemoteKeyDao,
            appDb = appDb,
            apiService = apiService
        )
    }
}