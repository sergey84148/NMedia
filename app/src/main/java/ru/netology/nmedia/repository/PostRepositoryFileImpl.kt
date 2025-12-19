package ru.netology.nmedia.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ru.netology.nmedia.dto.Post
import java.io.File
import java.lang.reflect.Type

class PostRepositoryFileImpl(private val context: Context) : PostRepository {

    companion object {
        const val POSTS_FILENAME = "posts.json"
    }

    private val gson = Gson()
    private val postsType: Type = object : TypeToken<List<Post>>() {}.type

    // Текущие посты
    private var posts: List<Post> = loadFromFile()
        set(value) {
            field = value
            saveToFile(value)
        }

    private val data = MutableLiveData(posts)

    override fun getAll(): LiveData<List<Post>> = data

    override fun save(post: Post) {
        if (post.id == 0L) {
            posts = listOf(
                post.copy(
                    id = System.currentTimeMillis(),
                    author = "Нетология. Университет интернет-профессий будущего",
                    likedByMe = false,
                    published = "9 мая в 8:36",
                )
            ) + posts
        } else {
            posts = posts.map { oldPost ->
                if (oldPost.id == post.id) post else oldPost
            }
        }
        data.value = posts
    }

    override fun likeById(id: Long) {
        posts = posts.map { post ->
            if (post.id == id) {
                post.copy(likes = post.likes + 1, likedByMe = true)
            } else post
        }
        data.value = posts
    }

    override fun removeById(id: Long) {
        posts = posts.filter { it.id != id }
        data.value = posts
    }

    override fun shareById(id: Long) {
        posts = posts.map { post ->
            if (post.id == id) {
                post.copy(shares = post.shares + 1)
            } else post
        }
        data.value = posts
    }

    // Получаем последний редактированный пост
    override fun findLastEditedPost(): Post? {
        return posts.firstOrNull()
    }

    // Чтение данных из файла
    private fun loadFromFile(): List<Post> {
        val file = context.getFileStreamPath(POSTS_FILENAME)
        return if (file.exists()) {
            gson.fromJson(file.readText(), postsType)
        } else {
            emptyList()
        }
    }

    // Сохранение данных в файл
    private fun saveToFile(posts: List<Post>) {
        val file = context.getFileStreamPath(POSTS_FILENAME)
        file.writeText(gson.toJson(posts))
    }
}