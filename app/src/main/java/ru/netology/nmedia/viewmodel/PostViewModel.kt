package ru.netology.nmedia.viewmodel

import androidx.lifecycle.*
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.repository.PostRepository
import ru.netology.nmedia.repository.PostRepositoryInMemoryImpl

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

    // Метод для сохранения нового поста
    fun save(post: Post) {
        repository.save(post)
    }

    // Метод для обновления содержания поста
    fun updateEditedPost(newContent: String) {
        // Здесь реализуется логика нахождения оригинального поста и обновления его содержимого
        // Например, ищем последний редактируемый пост и применяем к нему новые данные
        val originalPost = repository.findLastEditedPost()
        if (originalPost != null) {
            val updatedPost = originalPost.copy(content = newContent)
            repository.save(updatedPost)
        }
    }

    // Другие методы остаются без изменений
    fun likeById(id: Long) {
        repository.likeById(id)
    }

    fun removeById(id: Long) {
        repository.removeById(id)
    }

    fun shareById(id: Long) {
        repository.shareById(id)
    }


}