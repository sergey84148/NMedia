package ru.netology.nmedia.viewmodel

import android.app.Application
import androidx.lifecycle.*
import ru.netology.nmedia.db.AppDb
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.repository.PostRepository
import ru.netology.nmedia.repository.PostRepositoryRoomImpl

val emptyTemplate: Post = Post(
    id = 0,
    author = "",
    content = "",
    published = "",
    likes = 0,
    shares = 0,
    video = "" ,
    link = "" ,
    likedByMe = false

)

class PostViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: PostRepository = PostRepositoryRoomImpl(
        AppDb.getInstance(application).postDao
    )
    val data: LiveData<List<Post>> = repository.getAll()
    private val edited = MutableLiveData<Post?>(emptyTemplate)
    /*
        // Наблюдательная переменная для результата редактирования
        val editedPost: LiveData<Post?> = edited

        fun updateEditedPost(newContent: String) {
            edited.value?.let {
                val text = newContent.trim()
                if (it.content != text) {
                    repository.save(it.copy(content = text))
                }
            }
            edited.value = emptyTemplate
        }
    */
    // Метод для сохранения изменений
    fun save(content: String) {
        edited.value?.let {
            val text = content.trim()
            if (it.content != text) {
                repository.save(it.copy(content = text))
            }
        }
        edited.value = emptyTemplate
    }

    // Запускает режим редактирования выбранного поста
    fun edit(post: Post) {
        edited.value = post
    }

    // Лайкает выбранный пост
    fun likeById(id: Long) {
        repository.likeById(id)
    }

    // Удаляет выбранный пост
    fun removeById(id: Long) {
        repository.removeById(id)
    }

        fun shareById(id: Long) {
            repository.shareById(id)
        }


}