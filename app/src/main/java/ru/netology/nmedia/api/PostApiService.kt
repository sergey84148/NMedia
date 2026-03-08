package ru.netology.nmedia.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import ru.netology.nmedia.BuildConfig
import ru.netology.nmedia.dto.Post

const val BASE_URL = "http://10.0.2.2:9999/api/slow/"
private val logging = HttpLoggingInterceptor().apply {
    if (BuildConfig.DEBUG) {
        level = HttpLoggingInterceptor.Level.BODY
    }
}

private val okhttp = OkHttpClient.Builder()
    .addInterceptor(logging)
    .build()

private val retrofit = Retrofit.Builder()
    .addConverterFactory(GsonConverterFactory.create())
    .baseUrl(BASE_URL)
    .client(okhttp)
    .build()

// Добавляем data class для ответа с изменениями
data class PostsChangesResponse(
    val posts: List<Post>,
    val deletedIds: List<Long>,
    val timestamp: Long
)

interface PostsApiService {
    @GET("posts")
    suspend fun getAll(): List<Post>

    @GET("posts/{id}")
    suspend fun getById(@Path("id") id: Long): Post

    @POST("posts")
    suspend fun save(@Body post: Post): Post

    @PUT("posts/{id}")  // Добавляем PUT для обновления
    suspend fun update(@Path("id") id: Long, @Body post: Post): Post

    @DELETE("posts/{id}")
    suspend fun removeById(@Path("id") id: Long)

    @POST("posts/{id}/likes")
    suspend fun likeById(@Path("id") id: Long): Post

    @DELETE("posts/{id}/likes")
    suspend fun dislikeById(@Path("id") id: Long): Post

    @POST("posts/{id}")
    suspend fun shareById(id: Long): Post

    // НОВЫЙ МЕТОД для синхронизации
    @GET("posts/changes")
    suspend fun getChanges(
        @Query("after") after: Long,
        @Query("limit") limit: Int = 100
    ): PostsChangesResponse
}

object PostsApi {
    val retrofitService: PostsApiService by lazy {
        retrofit.create(PostsApiService::class.java)
    }
}