package ru.netology.nmedia.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import ru.netology.nmedia.dto.Post
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import android.util.Log

class PostRepositoryImpl : PostRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val gson = Gson()
    private val typeToken = object : TypeToken<List<Post>>() {}.type

    override suspend fun getAll(): List<Post> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/posts")
            .get()
            .build()
        executeRequest(request) { gson.fromJson(it, typeToken) }
    }

    override suspend fun getAllAsync(callback: PostRepository.GetAllCallback) {
        try {
            val posts = getAll()
            callback.onSuccess(posts)
        } catch (e: Exception) {
            callback.onError(e)
        }
    }

    override suspend fun likeById(id: Long): Post = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/posts/$id/likes")
            .post("{}".toRequestBody(jsonType))
            .build()
        executeRequest(request) { gson.fromJson(it, Post::class.java) }
    }

    override suspend fun dislikeById(id: Long): Post = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/posts/$id/likes")
            .delete()
            .build()
        executeRequest(request) { gson.fromJson(it, Post::class.java) }
    }

    override suspend fun shareById(id: Long): Post = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/posts/$id/share")
            .post("{}".toRequestBody(jsonType))
            .build()
        executeRequest(request) { gson.fromJson(it, Post::class.java) }
    }

    override suspend fun save(post: Post): Post = withContext(Dispatchers.IO) {
        require(post != null) { "Post не может быть null" }
        require(!post.content.isBlank()) { "Содержание поста не может быть пустым" }

        val request = Request.Builder()
            .url("$BASE_URL/api/posts")
            .post(gson.toJson(post).toRequestBody(jsonType))
            .build()
        executeRequest(request) { gson.fromJson(it, Post::class.java) }
    }

    override suspend fun removeById(id: Long) = withContext(Dispatchers.IO) {
        if (id <= 0) {
            throw IllegalArgumentException("Invalid post ID: $id")
        }

        val request = Request.Builder()
            .url("$BASE_URL/api/posts/$id")
            .delete()
            .build()
        executeRequest<Unit>(request) { /* Пустой ответ */ }
    }

    private companion object {
        const val BASE_URL = "http://10.0.2.2:9999"
        val jsonType = "application/json".toMediaType()
    }

    private suspend fun <T> executeRequest(
        request: Request,
        parser: (String) -> T
    ): T {
        var attempt = 0
        val maxAttempts = 3

        while (attempt < maxAttempts) {
            try {
                client.newCall(request).execute().use { response ->
                    Log.d("HTTP", "→ ${request.method} ${request.url}")
                    Log.d("HTTP", "← ${response.code} ${response.message}")

                    if (!response.isSuccessful) {
                        throw IOException("HTTP error: ${response.code} ${response.message}")
                    }

                    val body = response.body?.string() ?: throw IOException("Пустое тело ответа")
                    Log.d("HTTP", "Body: $body")

                    return parser(body)
                }
            } catch (e: IOException) {
                attempt++
                if (attempt == maxAttempts) throw e
                delay(100L) // миллисекунды задержки между попытками
            }
        }
        throw IOException("Max retry attempts exceeded")
    }
}
