package ru.netology.nmedia.viewmodel

import androidx.lifecycle.*
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.repository.PostRepository
import ru.netology.nmedia.repository.PostRepositoryInMemoryImpl
import ru.netology.nmedia.util.AndroidUtils

val emptyTemplate: Post = Post(
    id = 0,
    author = "",
    content = "",
    published = "",
    likes = 0,
    shares = 0,
    likedByMe = false
)

class PostViewModel(private val repository: PostRepository = PostRepositoryInMemoryImpl()) : ViewModel() {

    // Данные из репозитория
    val data: LiveData<List<Post>> = repository.getAll()

    // Переменная для редактирования поста
    private val edited = MutableLiveData<Post?>(emptyTemplate) // Используем emptyTemplate вместо empty

    // Наблюдательная переменная для результата редактирования
    val editedPost: LiveData<Post?> = edited

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

    // Отмена редактирования
    fun onCancelEdit() {
        edited.value = emptyTemplate
    }
}