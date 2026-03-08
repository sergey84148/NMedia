package ru.netology.nmedia.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.netology.nmedia.db.AppDb
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.model.FeedModel
import ru.netology.nmedia.model.FeedModelState
import ru.netology.nmedia.repository.PostRepository
import ru.netology.nmedia.repository.PostRepositoryImpl
import ru.netology.nmedia.util.SingleLiveEvent

val emptyTemplate: Post = Post(
    id = 0L,
    author = "",
    authorAvatar = "",
    content = "",
    published = "",
    likedByMe = false,
    link = "",
    shares = 0,
    video = null,
)

class PostViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: PostRepository = PostRepositoryImpl(
        AppDb.getInstance(application).postDao()
    )

    private val _state = MutableLiveData<FeedModelState>()
    val state: LiveData<FeedModelState>
        get() = _state

    val data: LiveData<FeedModel> = repository.data.map {
        FeedModel(it, it.isEmpty())
    }

    // Текущий редактируемый пост
    val edited = MutableLiveData<Post>(emptyTemplate)

    // Событие: пост успешно создан/обновлен
    private val _postCreated = SingleLiveEvent<Unit>()
    val postCreated: LiveData<Unit>
        get() = _postCreated

    init {
        loadPosts()
    }

    fun loadPosts() {
        _state.value = FeedModelState(loading = true)
        viewModelScope.launch {
            try {
                repository.getAllAsync()
                _state.value = FeedModelState()
            } catch (_: Exception) {
                _state.value = FeedModelState(error = true)
                Log.e("PostViewModel", "Load posts error:")
            }
        }
    }

    fun save(content: String) =
        viewModelScope.launch(Dispatchers.IO) {
        edited.value?.let { post ->
            val trimmedContent = content.trim()
            if (trimmedContent.isNotBlank()) {
                try {
                    val updatedPost = repository.save(post.copy(content = trimmedContent))
                    _postCreated.postValue(Unit)
                } catch (e: Exception) {
                    Log.e("PostViewModel", "Save post error:", e)
                }
            }
        }
        // Сбрасываем редактируемый пост
        edited.postValue(emptyTemplate)
    }

    fun edit(post: Post) {
        edited.value = post
    }

    fun toggleLike(post: Post) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val updatedPost = if (post.likedByMe) {
                repository.dislikeById(post.id)
            } else {
                repository.likeById(post.id)
            }
        } catch (e: Exception) {
            Log.e("PostViewModel", "Toggle like error:", e)
        }
    }

    fun removeById(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        try {
            repository.removeById(id)
        } catch (e: Exception) {
            Log.e("PostViewModel", "Remove post error:", e)
        }
    }




}