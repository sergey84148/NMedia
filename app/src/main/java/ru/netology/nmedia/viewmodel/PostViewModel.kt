package ru.netology.nmedia.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.netology.nmedia.db.AppDb
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.enumeration.SyncState
import ru.netology.nmedia.model.FeedModel
import ru.netology.nmedia.model.FeedModelState
import ru.netology.nmedia.repository.PostRepository
import ru.netology.nmedia.repository.PostRepositoryImpl
import ru.netology.nmedia.util.SingleLiveEvent
import ru.netology.nmedia.utils.RetryPolicy

val emptyTemplate: Post = Post(
    id = 0L,
    author = "",
    authorAvatar = "",
    content = "",
    published = "",
    likedByMe = false,
    shares = 0,
    video = null,
    likes = 0,
    attachment = null
)

class PostViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: PostRepository = PostRepositoryImpl(
        AppDb.getInstance(application).postDao()
    )

    private val _state = MutableLiveData(FeedModelState())
    val state: LiveData<FeedModelState>
        get() = _state

    val data: LiveData<FeedModel> = repository.data
        .map { posts -> FeedModel(posts) }
        .asLiveData(Dispatchers.Default)

    val edited = MutableLiveData(emptyTemplate)

    private val _postCreated = SingleLiveEvent<Unit>()
    val postCreated: LiveData<Unit>
        get() = _postCreated

    private val _syncState = MutableLiveData<SyncState>()
    val syncState: LiveData<SyncState>
        get() = _syncState

    // Отслеживание состояния сети
    private val _isNetworkAvailable = MutableLiveData(true)
    val isNetworkAvailable: LiveData<Boolean> = _isNetworkAvailable

    private val _showNoConnectionMessage = MutableLiveData(false)
    val showNoConnectionMessage: LiveData<Boolean> = _showNoConnectionMessage

    // 👇 НОВЫЕ ПОЛЯ ДЛЯ ПЛАШКИ "СВЕЖИЕ ЗАПИСИ"
    private val _showNewPostsBanner = MutableLiveData(false)
    val showNewPostsBanner: LiveData<Boolean> = _showNewPostsBanner

    private val _newPostsCount = MutableLiveData(0)
    val newPostsCount: LiveData<Int> = _newPostsCount

    private var lastSeenTimestamp = System.currentTimeMillis()

    init {
        loadPosts()

        viewModelScope.launch {
            repository.getSyncState().collect { syncState ->
                _syncState.postValue(syncState)
                updateState()
            }
        }

        // Периодическая синхронизация
        viewModelScope.launch {
            while (true) {
                delay(1 * 60 * 1000) // Каждые 5 минут
                if (_syncState.value != SyncState.SYNCING) {
                    syncWithServer()
                }
            }
        }

        // Отслеживание состояния сети
        viewModelScope.launch {
            RetryPolicy.observeNetwork(getApplication()).collect { isConnected ->
                _isNetworkAvailable.postValue(isConnected)

                if (!isConnected) {
                    _showNoConnectionMessage.postValue(true)
                } else {
                    _showNoConnectionMessage.postValue(false)
                    // При появлении сети проверяем, нужно ли синхронизироваться
                    if (_syncState.value == SyncState.FAILED || getPendingPostsCount() > 0) {
                        retryFailedSync()
                    }
                }
            }
        }

        // Периодическая проверка новых постов (каждые 5 секунд)
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                if (_syncState.value != SyncState.SYNCING) {
                    checkForNewPosts()
                }
            }
        }
    }

    private suspend fun getPendingPostsCount(): Int {
        return repository.getPendingPostsCount()
    }

    private fun updateState() {
        viewModelScope.launch {
            val pendingCount = repository.getPendingPostsCount()
            _state.postValue(
                FeedModelState(
                    loading = _state.value?.loading ?: false,
                    error = _state.value?.error ?: false,
                    refreshing = _state.value?.refreshing ?: false,
                    syncing = _syncState.value == SyncState.SYNCING,
                    syncError = _syncState.value == SyncState.FAILED,
                    pendingPostsCount = pendingCount
                )
            )
        }
    }

    fun loadPosts() {
        _state.value = _state.value?.copy(loading = true)
        viewModelScope.launch {
            try {
                repository.getAllAsync()
                _state.value = _state.value?.copy(loading = false, error = false)
                lastSeenTimestamp = System.currentTimeMillis()
            } catch (e: Exception) {
                _state.value = _state.value?.copy(loading = false, error = true)
                Log.e("PostViewModel", "Load posts error: ${e.message}", e)
            }
        }
    }

    fun refreshPosts() {
        _state.value = _state.value?.copy(refreshing = true)
        viewModelScope.launch {
            try {
                repository.getAllAsync()
                _state.value = _state.value?.copy(refreshing = false, error = false)
            } catch (e: Exception) {
                _state.value = _state.value?.copy(refreshing = false, error = true)
                Log.e("PostViewModel", "Refresh posts error: ${e.message}", e)
            }
        }
    }

    fun save(content: String) {
        edited.value?.let { post ->
            val trimmedContent = content.trim()
            if (trimmedContent.isNotBlank()) {
                viewModelScope.launch {
                    try {
                        val savedPost = repository.save(post.copy(content = trimmedContent))
                        _postCreated.postValue(Unit)
                        Log.d("PostViewModel", "Post saved successfully: $savedPost")

                        // Если есть проблемы с сетью, показываем сообщение
                        if (_syncState.value == SyncState.FAILED) {
                            _state.value = _state.value?.copy(syncError = true)
                        }
                    } catch (e: Exception) {
                        Log.e("PostViewModel", "Save post error:", e)
                        _state.value = _state.value?.copy(error = true)
                    }
                }
            }
        }
        edited.postValue(emptyTemplate)
    }

    fun edit(post: Post) {
        edited.value = post
    }

    fun likeById(id: Long) {
        viewModelScope.launch {
            try {
                repository.likeById(id)
            } catch (e: Exception) {
                Log.e("PostViewModel", "Like error:", e)
                _state.value = _state.value?.copy(error = true)
            }
        }
    }

    fun removeById(id: Long) {
        viewModelScope.launch {
            try {
                repository.removeById(id)
            } catch (e: Exception) {
                Log.e("PostViewModel", "Remove error:", e)
                _state.value = _state.value?.copy(error = true)
            }
        }
    }

    fun toggleLike(post: Post) {
        likeById(post.id)
    }

    fun shareById(id: Long) {
        viewModelScope.launch {
            try {
                repository.shareById(id)
            } catch (e: Exception) {
                Log.e("PostViewModel", "Share error:", e)
                _state.value = _state.value?.copy(error = true)
            }
        }
    }

    fun syncWithServer() {
        viewModelScope.launch {
            try {
                repository.syncWithServer()
            } catch (e: Exception) {
                Log.e("PostViewModel", "Sync error:", e)
                _state.value = _state.value?.copy(error = true)
            }
        }
    }

    fun retryFailedSync() {
        viewModelScope.launch {
            _state.value = _state.value?.copy(syncError = false)
            repository.retryFailedSync()
        }
    }

    fun clearError() {
        _state.value = _state.value?.copy(error = false, syncError = false)
    }

    // 👇 НОВЫЕ МЕТОДЫ ДЛЯ ПЛАШКИ "СВЕЖИЕ ЗАПИСИ"

    fun checkForNewPosts() {
        viewModelScope.launch {
            try {
                val newCount = repository.getNewerPostsCount(lastSeenTimestamp)
                if (newCount > 0) {
                    _newPostsCount.postValue(newCount)
                    _showNewPostsBanner.postValue(true)
                }
            } catch (e: Exception) {
                Log.e("PostViewModel", "Error checking new posts", e)
            }
        }
    }

    fun onNewPostsBannerClicked() {
        _showNewPostsBanner.postValue(false)
        lastSeenTimestamp = System.currentTimeMillis()
        loadPosts()
    }
}