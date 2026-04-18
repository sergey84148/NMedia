package ru.netology.nmedia.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.netology.nmedia.api.ApiService
import ru.netology.nmedia.auth.AppAuth
import ru.netology.nmedia.repository.PostRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HiltModules {

    @Provides
    @Singleton
    fun provideDependencyContainer(@ApplicationContext context: Context): DependencyContainer {
        // Инициализируем DependencyContainer, если еще не инициализирован
        if (!DependencyContainer.isInitialized()) {
            DependencyContainer.initApp(context)
        }
        return DependencyContainer.getInstance()
    }

    @Provides
    @Singleton
    fun provideAppAuth(dependencyContainer: DependencyContainer): AppAuth {
        return dependencyContainer.appAuth
    }

    @Provides
    @Singleton
    fun provideApiService(dependencyContainer: DependencyContainer): ApiService {
        return dependencyContainer.apiService
    }

    @Provides
    @Singleton
    fun providePostRepository(dependencyContainer: DependencyContainer): PostRepository {
        return dependencyContainer.repository
    }
}