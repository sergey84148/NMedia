package ru.netology.nmedia.repository


import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import ru.netology.nmedia.dto.Post
import java.io.IOException
import java.util.concurrent.TimeUnit

class PostRepositoryImpl : PostRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val postType = object : TypeToken<List<Post>>() {}.type

    private companion object {
        const val BASE_URL = "http://10.0.2.2:9999"
        val jsonType = "application/json".toMediaType()
    }

    override fun getAll(): List<Post> {
        val request = Request.Builder()
            .url("$BASE_URL/api/slow/posts")
            .get()
            .build()
        return executeRequest(request) { gson.fromJson(it, postType) }
    }

    override suspend fun likeById(id: Long): Post {
        val request = Request.Builder()
            .url("$BASE_URL/api/slow/posts/$id/like")
            .post(RequestBody.create(jsonType, ""))
            .build()
        return executeRequest(request) { gson.fromJson(it, Post::class.java) }
    }

    override fun shareById(id: Long) {
        // TODO: Реализовать логику шеринга
        throw UnsupportedOperationException("Метод не реализован")
    }

    override fun save(post: Post): Post {
        val request = Request.Builder()
            .url("$BASE_URL/api/slow/posts")
            .post(gson.toJson(post).toRequestBody(jsonType))
            .build()
        return executeRequest(request) { gson.fromJson(it, Post::class.java) }
    }

    override fun removeById(id: Long) {
        val request = Request.Builder()
            .url("$BASE_URL/api/slow/posts/$id")
            .delete()
            .build()

    }

    private fun <T> executeRequest(
        request: Request,
        parser: (String) -> T
    ): T {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP error: ${response.code} ${response.message}")
            }
            val body = response.body?.string() ?: throw IOException("Пустое тело ответа")
            return parser(body)
        }
    }
}
