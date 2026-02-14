package ru.netology.nmedia.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import ru.netology.nmedia.dto.Post
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.util.Log
import okhttp3.Call
import okhttp3.Response


class PostRepositoryImpl : PostRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val typeToken = object : TypeToken<List<Post>>() {}.type

    private companion object {
        const val BASE_URL = "http://10.0.2.2:9999"
        val jsonType = "application/json".toMediaType()
    }

    override suspend fun getAll(): List<Post> {
        val request = Request.Builder()
            .url("$BASE_URL/api/slow/posts")
            .build()

        return client.newCall(request)
            .execute()
            .let { it.body?.string() ?: throw RuntimeException("body is null") }
            .let {
                gson.fromJson(it, typeToken)
            }
    }

    override suspend fun getAllAsync(callback: PostRepository.GetAllCallback) {
        val request: Request = Request.Builder()
            .url("$BASE_URL/api/slow/posts")
            .build()

        client.newCall(request)
            .enqueue(object: Callback {
                override fun onFailure(call: Call, e: okio.IOException) {
                    callback.onError(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val posts = response.body?.string() ?: throw RuntimeException("body is null")
                        callback.onSuccess(gson.fromJson(posts, typeToken))
                    } catch (e: Exception) {
                        callback.onError(e)
                    }
                }
            })

    }

    override suspend fun likeById(id: Long): Post {
        val request = Request.Builder()
            .url("$BASE_URL/api/posts/$id/likes")
            .post("{}".toRequestBody(jsonType))
            .build()
        return executeRequest(request) { gson.fromJson(it, Post::class.java) }
    }

    override suspend fun dislikeById(id: Long): Post {
        val request = Request.Builder()
            .url("$BASE_URL/api/posts/$id/likes")
            .delete()
            .build()
        return executeRequest(request) { gson.fromJson(it, Post::class.java) }
    }

    override suspend fun shareById(id: Long): Post {
        val request = Request.Builder()
            .url("$BASE_URL/api/slow/posts/$id/share")
            .post("{}".toRequestBody(jsonType))
            .build()
        return executeRequest(request) { gson.fromJson(it, Post::class.java) }
    }

    override suspend fun save(post: Post): Post {
        val request = Request.Builder()
            .url("$BASE_URL/api/slow/posts")
            .post(gson.toJson(post).toRequestBody(jsonType))
            .build()
        return executeRequest(request) { gson.fromJson(it, Post::class.java) }
    }

    override suspend fun removeById(id: Long) {
        val request = Request.Builder()
            .url("$BASE_URL/api/slow/posts/$id")
            .delete()
            .build()

        executeRequest<Unit>(request) { /* Пустой ответ */ }
    }

    private suspend fun <T> executeRequest(
        request: Request,
        parser: (String) -> T
    ): T {
        client.newCall(request).execute().use { response ->
            // Логируем запрос и ответ для отладки
            Log.d("HTTP", "→ ${request.method} ${request.url}")
            Log.d("HTTP", "← ${response.code} ${response.message}")

            if (!response.isSuccessful) {
                throw IOException("HTTP error: ${response.code} ${response.message}")
            }

            val body = response.body?.string() ?: throw IOException("Пустое тело ответа")
            Log.d("HTTP", "Body: $body")  // Логируем тело ответа

            return parser(body)
        }
    }
}
