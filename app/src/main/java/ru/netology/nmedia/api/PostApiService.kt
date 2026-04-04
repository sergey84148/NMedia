package ru.netology.nmedia.api

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import ru.netology.nmedia.BuildConfig
import ru.netology.nmedia.auth.AppAuth
import ru.netology.nmedia.dto.*

const val BASE_URL = "http://10.0.2.2:9999/api/slow/"

private val logging = HttpLoggingInterceptor().apply {
    if (BuildConfig.DEBUG) {
        level = HttpLoggingInterceptor.Level.BODY
    }
}

private val okhttp = OkHttpClient.Builder()
    .addInterceptor(logging)
    .addInterceptor { chain ->
        val originalRequest = chain.request()

        try {
            if (AppAuth.isInitialized()) {
                val token = AppAuth.getInstance().getToken()
                println("DEBUG: Token = $token")
                if (!token.isNullOrEmpty()) {
                    val newRequest = originalRequest.newBuilder()
                        .addHeader("Authorization", token)
                        .build()
                    println("DEBUG: Added Authorization header")
                    return@addInterceptor chain.proceed(newRequest)
                } else {
                    println("DEBUG: Token is null or empty")
                }
            } else {
                println("DEBUG: AppAuth not initialized")
            }
        } catch (e: Exception) {
            println("DEBUG: Error adding token: ${e.message}")
        }

        chain.proceed(originalRequest)
    }
    .build()

private val retrofit = Retrofit.Builder()
    .addConverterFactory(GsonConverterFactory.create())
    .baseUrl(BASE_URL)
    .client(okhttp)
    .build()

interface PostsApiService {
    // Посты
    @GET("posts")
    suspend fun getAll(): Response<List<Post>>

    @GET("posts/{id}/newer")
    suspend fun getNewer(@Path("id") id: Long): Response<List<Post>>

    @GET("posts/{id}")
    suspend fun getById(@Path("id") id: Long): Response<Post>

    @POST("posts")
    suspend fun save(@Body post: Post): Response<Post>

    @DELETE("posts/{id}")
    suspend fun removeById(@Path("id") id: Long): Response<Unit>

    @POST("posts/{id}/likes")
    suspend fun likeById(@Path("id") id: Long): Response<Post>

    @DELETE("posts/{id}/likes")
    suspend fun dislikeById(@Path("id") id: Long): Response<Post>

    @POST("posts/{id}/shares")
    suspend fun shareById(@Path("id") id: Long): Response<Post>

    @Multipart
    @POST("media")
    suspend fun upload(@Part media: MultipartBody.Part): Response<Media>

    @POST("users/push-tokens")
    suspend fun sendPushToken(@Body pushToken: PushToken): Response<Unit>

    @FormUrlEncoded
    @POST("users/authentication")
    suspend fun login(
        @Field("login") login: String,
        @Field("pass") pass: String
    ): Response<AuthResponse>

    @Multipart
    @POST("users/registration")
    suspend fun register(
        @Part("login") login: okhttp3.RequestBody,
        @Part("pass") pass: okhttp3.RequestBody,
        @Part("name") name: okhttp3.RequestBody,
        @Part file: MultipartBody.Part? = null
    ): Response<AuthResponse>
}

object PostsApi {
    val service: PostsApiService by lazy {
        retrofit.create(PostsApiService::class.java)
    }
}