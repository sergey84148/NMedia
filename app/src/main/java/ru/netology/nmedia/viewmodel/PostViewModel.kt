package ru.netology.nmedia.viewmodel

import androidx.lifecycle.ViewModel
import ru.netology.nmedia.repository.PostRepository
import ru.netology.nmedia.repository.PostRepositoryInMemoryImpl

class PostViewModel(
    private val repository: PostRepository = PostRepositoryInMemoryImpl()
) : ViewModel() {

    // Публичный поток данных поста
    val data = repository.get()

    // Методы для взаимодействия с репозиторием
    fun like() = repository.like()

    fun share() {
        repository.share()  // Делегируем вызов в репозиторий
    }

    // Дополнительно: методы для других операций (если нужны)
    fun updateContent(content: String) {
        repository.updateContent(content)
    }

    fun increaseViews() {
        repository.increaseViews()
    }
}
