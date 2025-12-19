package ru.netology.nmedia.repository

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ru.netology.nmedia.dto.Post
import java.lang.reflect.Type

class PostRepositoryInMemoryImpl(
    context: Context
) : PostRepository {

    private val prefs = context.getSharedPreferences("data" , Context.MODE_PRIVATE)

    private var nextId = getId()

    // Список постов
    private var posts: List<Post> = getPosts()
        set(value) {
            field = value
            sync()
        }

    private val data = MutableLiveData(posts)

    override fun getAll(): LiveData<List<Post>> = data

    override fun save(post: Post) {
        if (post.id == 0L) {
            posts = listOf(
                post.copy(
                    id = nextId++,
                    author = "Нетология. Университет интернет-профессий будущего",
                    likedByMe = false,
                    published = "9 мая в 8:36",
                )
            ) + posts
            data.value = posts

        }

        posts = posts.map {
            if (it.id != post.id) it else it.copy(content = post.content)
        }
        data.value = posts
    }

    override fun likeById(id: Long) {
        posts = posts.map {
            if (it.id != id) it else it.copy(likedByMe = !it.likedByMe, likes = if (it.likedByMe) it.likes - 1 else it.likes + 1)
        }
        data.value = posts
    }

    override fun removeById(id: Long) {
        posts = posts.filter { it.id != id }
        data.value = posts
    }

    override fun shareById(id: Long) {
        posts = posts.map {
            if (it.id != id) it else it.copy(shares = it.shares + 1)
        }
        data.value = posts
    }

    // Новый метод для поиска последнего редактируемого поста
    override fun findLastEditedPost(): Post? {
        return posts.firstOrNull()
    }

    private fun getPosts(): List<Post> = prefs.getString(POSTS_KEY, null)?.let {
        gson.fromJson(it, postsType)
    } ?: emptyList()

    private fun getId() = prefs.getLong(ID_KEY, 1L)

    private fun sync() {
        prefs.edit {
            putString(POSTS_KEY, gson.toJson(posts))
            putLong(ID_KEY, nextId)
        }
    }


    private companion object{
        const val POSTS_KEY = "posts"
        const val ID_KEY = "nextId"

        val gson = Gson()
        val postsType: Type = object : TypeToken<List<Post>>() {}.type
    }

}