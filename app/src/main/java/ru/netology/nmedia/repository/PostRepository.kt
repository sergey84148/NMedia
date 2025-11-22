package ru.netology.nmedia.repository


import androidx.lifecycle.LiveData
import ru.netology.nmedia.Post

interface PostRepository {
    fun get(): LiveData<Post>
    val data: LiveData<Post>

    fun like()

    fun share()

    fun updateContent(content: String)

    fun increaseViews()
}
