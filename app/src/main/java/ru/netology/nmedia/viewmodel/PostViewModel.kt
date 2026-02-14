package ru.netology.nmedia.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.model.FeedModel
import ru.netology.nmedia.repository.PostRepository
import ru.netology.nmedia.repository.PostRepositoryImpl
import ru.netology.nmedia.util.SingleLiveEvent

// Шаблон пустого поста для редактирования
val emptyTemplate: Post = Post(
    id = 0,
    author = "",
    content = "",
    published = "",
    likes = 0,
    shares = 0,
    video = "",
    link = "",
    likedByMe = false
)

class PostViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PostRepository = PostRepositoryImpl()

    // Основное состояние ленты постов
    private val _data = MutableLiveData<FeedModel>()
    val data: LiveData<FeedModel> get() = _data

    // Текущий редактируемый пост
    val edited = MutableLiveData<Post>(emptyTemplate)

    // Событие: пост успешно создан/обновлён
    private val _postCreated = SingleLiveEvent<Unit>()
    val postCreated: LiveData<Unit> get() = _postCreated

    init {
        load()
    }


     // Загружает список постов с сервера.
     // Устанавливает состояние loading = true до завершения запроса.

    fun load() = viewModelScope.launch(Dispatchers.IO) {
        _data.postValue(FeedModel(loading = true))
        repository.getAllAsync(object : PostRepository.GetAllCallback {
            override fun onSuccess(posts: List<Post>) {
                _data.postValue(FeedModel(posts, posts.isEmpty()))
            }

            override fun onError(e: Exception) {
                _data.postValue(FeedModel(error = true))
            }
        })

    }

    /**
     * Сохраняет изменения поста (создание или редактирование).
     * Если контент не изменился — действие не выполняется.
     */
    fun save(content: String) = viewModelScope.launch(Dispatchers.IO) {
        edited.value?.let { post ->
            val trimmedContent = content.trim()

            if (post.content != trimmedContent) {
                try {
                    val updatedPost = repository.save(post.copy(content = trimmedContent))
                    _postCreated.postValue(Unit)

                    // Обновляем пост в локальном списке
                    updateLocalPost(updatedPost)
                } catch (e: Exception) {
                    Log.e("PostViewModel", "Save failed", e)
                }
            }
        }

        // Сбрасываем редактируемый пост
        edited.postValue(emptyTemplate)
    }

    /**
     * Переводит пост в режим редактирования.
     */
    fun edit(post: Post) {
        edited.value = post
    }

    /**
     * Ставит или снимает лайк на посте.
     * Отправляет запрос на сервер и обновляет локальный кэш.
     */
    fun toggleLike(post: Post) = viewModelScope.launch(Dispatchers.IO) {
        try {
            // Отправляем запрос на сервер
            val updatedPost = if (post.likedByMe) {
                repository.dislikeById(post.id)
            } else {
                repository.likeById(post.id)
            }

            // Обновляем локальный список только после успешного ответа
            updateLocalPost(updatedPost)
        } catch (e: Exception) {
            Log.e("PostViewModel", "Toggle like failed", e)
            // При ошибке можно перезагрузить данные
            load()
        }
    }

    /**
     * Удаляет пост по ID.
     * Обновляет локальный список и состояние empty.
     */
    fun removeById(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        try {
            repository.removeById(id)

            // Удаляем пост из локального списка
            _data.postValue(_data.value?.copy(
                posts = _data.value?.posts?.filter { it.id != id } ?: emptyList(),
                empty = (_data.value?.posts?.isEmpty() ?: true)
            ))
        } catch (e: Exception) {
            Log.e("PostViewModel", "Remove failed", e)
            _data.postValue(_data.value?.copy(error = true))
            load() // Перезагрузка при ошибке
        }
    }

    /**
     * Увеличивает счётчик shares у поста.
     * Обновляет локальный кэш после успешного запроса.
     */
    fun shareById(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        try {
            repository.shareById(id)

            // Получаем актуальный пост после увеличения shares
            val updatedPosts = repository.getAll()
            _data.postValue(FeedModel(posts = updatedPosts, empty = updatedPosts.isEmpty()))
        } catch (e: Exception) {
            Log.e("PostViewModel", "Share failed", e)
            load() // Перезагрузка при ошибке
        }
    }

    // Вспомогательный метод для обновления локального поста в списке
    private fun updateLocalPost(updatedPost: Post) {
        _data.postValue(_data.value?.copy(
            posts = _data.value?.posts?.map { p ->
                if (p.id == updatedPost.id) updatedPost else p
            } ?: emptyList()
        ))
    }
}
