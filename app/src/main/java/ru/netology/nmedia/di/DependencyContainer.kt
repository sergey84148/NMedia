package ru.netology.nmedia.di

import android.content.Context
import androidx.room.Room
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.netology.nmedia.BuildConfig
import ru.netology.nmedia.api.ApiService
import ru.netology.nmedia.auth.AppAuth
import ru.netology.nmedia.db.AppDb
import ru.netology.nmedia.repository.PostRepository
import ru.netology.nmedia.repository.PostRepositoryImpl
import retrofit2.create

class DependencyContainer private constructor(
    context: Context
) {
    private val appContext: Context = context.applicationContext

    private val logging = HttpLoggingInterceptor().apply {
        if (BuildConfig.DEBUG) {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    // AppAuth будет установлен позже
    private var _appAuth: AppAuth? = null
    val appAuth: AppAuth
        get() = _appAuth ?: throw IllegalStateException("AppAuth not initialized yet")

    private val okhttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .addInterceptor { chain ->
            // Используем _appAuth напрямую, чтобы избежать рекурсии
            _appAuth?.authStateFlow?.value?.token?.let { token ->
                val newRequest = chain.request().newBuilder()
                    .addHeader("Authorization", token)
                    .build()
                return@addInterceptor chain.proceed(newRequest)
            }
            chain.proceed(chain.request())
        }
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .addConverterFactory(GsonConverterFactory.create())
        .baseUrl("${BuildConfig.BASE_URL}/api/slow/")
        .client(okhttp)
        .build()

    private val appDb: AppDb = Room.databaseBuilder(
        appContext,
        AppDb::class.java,
        "app.db"
    )
        .fallbackToDestructiveMigration(true)
        .build()

    val apiService: ApiService = retrofit.create()

    private val postDao = appDb.postDao()

    val repository: PostRepository = PostRepositoryImpl(
        postDao,
        apiService
    )

    fun setAppAuth(appAuth: AppAuth) {
        _appAuth = appAuth
    }

    companion object {
        @Volatile
        private var instance: DependencyContainer? = null

        fun initApp(context: Context): DependencyContainer {
            return instance ?: synchronized(this) {
                instance ?: DependencyContainer(context).also {
                    instance = it
                }
            }
        }

        fun getInstance(): DependencyContainer {
            return instance ?: throw IllegalStateException("DependencyContainer not initialized. Call initApp first.")
        }

        fun isInitialized(): Boolean = instance != null
    }
}