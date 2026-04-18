package ru.netology.nmedia.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.netology.nmedia.auth.AppAuth
import ru.netology.nmedia.dto.MediaUpload
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.enumeration.SyncState
import ru.netology.nmedia.model.FeedModel
import ru.netology.nmedia.model.FeedModelState
import ru.netology.nmedia.model.PhotoModel
import ru.netology.nmedia.repository.PostRepository
import ru.netology.nmedia.util.SingleLiveEvent
import ru.netology.nmedia.utils.RetryPolicy
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PostViewModel @Inject constructor(
    private val repository: PostRepository,
    private val appAuth: AppAuth,
    private val application: Application
) : ViewModel() {

    private val _state = MutableLiveData(FeedModelState())
    val state: LiveData<FeedModelState> = _state

    private val _data = MutableLiveData<FeedModel>()
    val data: LiveData<FeedModel> = _data

    val edited = MutableLiveData(emptyPost)
    private val _photo = MutableLiveData<PhotoModel?>(null)
    val photo: LiveData<PhotoModel?> = _photo

    private val _postCreated = SingleLiveEvent<Unit>()
    val postCreated: LiveData<Unit> = _postCreated

    private val _syncState = MutableLiveData<SyncState>()
    val syncState: LiveData<SyncState> = _syncState

    private val _isNetworkAvailable = MutableLiveData(true)
    val isNetworkAvailable: LiveData<Boolean> = _isNetworkAvailable

    private val _showNoConnectionMessage = MutableLiveData(false)
    val showNoConnectionMessage: LiveData<Boolean> = _showNoConnectionMessage

    private val _showNewPostsBanner = MutableLiveData(false)
    val showNewPostsBanner: LiveData<Boolean> = _showNewPostsBanner

    private val _newPostsCount = MutableLiveData(0)
    val newPostsCount: LiveData<Int> = _newPostsCount

    private var lastVisiblePostId = 0L

    init {
        viewModelScope.launch {
            delay(100)

            appAuth.authStateFlow.collect { authState ->
                if (authState.token != null && authState.id != 0L) {
                    loadPosts()
                } else {
                    _data.postValue(FeedModel(emptyList()))
                }
            }
        }

        viewModelScope.launch {
            repository.data.collect { posts ->
                val currentAuth = appAuth.authStateFlow.value
                val feedModel = FeedModel(
                    posts = posts.map { it.copy(ownedByMe = it.authorId == currentAuth.id) }
                )
                _data.postValue(feedModel)
                updateLastVisiblePostId()
            }
        }

        viewModelScope.launch {
            repository.getSyncState().collect { syncState ->
                _syncState.postValue(syncState)
                updateState()
            }
        }

        viewModelScope.launch {
            repository.newPostsCount.collect { count ->
                if (count > 0) {
                    _newPostsCount.postValue(count)
                    _showNewPostsBanner.postValue(true)
                    Log.d("PostViewModel", "New posts count from Flow: $count")
                } else {
                    _showNewPostsBanner.postValue(false)
                }
            }
        }

        // Исправлено: передаем Application context вместо LifecycleOwner
        viewModelScope.launch {
            RetryPolicy.observeNetwork(application).collect { isConnected ->
                _isNetworkAvailable.postValue(isConnected)

                if (!isConnected) {
                    _showNoConnectionMessage.postValue(true)
                } else {
                    _showNoConnectionMessage.postValue(false)
                    if (_syncState.value == SyncState.FAILED || getPendingPostsCount() > 0) {
                        retryFailedSync()
                    }
                }
            }
        }

        viewModelScope.launch {
            delay(1000)
            updateLastVisiblePostId()
        }
    }

    private suspend fun getPendingPostsCount(): Int {
        return repository.getPendingPostsCount()
    }

    fun updateLastVisiblePostId() {
        viewModelScope.launch {
            _data.value?.posts?.firstOrNull()?.let { firstPost ->
                lastVisiblePostId = firstPost.id
                Log.d("PostViewModel", "Last visible post ID set to: $lastVisiblePostId")
            }
        }
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
        if (appAuth.authStateFlow.value.token == null) {
            Log.d("PostViewModel", "Not authorized, skipping load")
            return
        }

        _state.value = _state.value?.copy(loading = true)
        viewModelScope.launch {
            try {
                repository.getAllAsync()
                _state.value = _state.value?.copy(loading = false, error = false)
            } catch (e: Exception) {
                _state.value = _state.value?.copy(loading = false, error = true)
                Log.e("PostViewModel", "Load posts error: ${e.message}", e)
            }
        }
    }

    fun checkForNewPosts() {
        viewModelScope.launch {
            try {
                val currentLastId = lastVisiblePostId
                Log.d("PostViewModel", "Manual check for new posts after ID: $currentLastId")
                repository.checkForNewPosts(currentLastId)
            } catch (e: Exception) {
                Log.e("PostViewModel", "Error checking new posts", e)
            }
        }
    }

    fun onNewPostsBannerClicked() {
        viewModelScope.launch {
            Log.d("PostViewModel", "Showing new posts")
            repository.showNewPosts()
            _showNewPostsBanner.postValue(false)
            _newPostsCount.postValue(0)
            updateLastVisiblePostId()
        }
    }

    @Suppress("unused")
    fun refreshPosts() {
        if (appAuth.authStateFlow.value.token == null) {
            Log.d("PostViewModel", "Not authorized, skipping refresh")
            return
        }

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

    fun save() {
        if (!isAuthenticated()) {
            _state.value = _state.value?.copy(error = true)
            return
        }

        edited.value?.let { post ->
            _postCreated.value = Unit
            viewModelScope.launch {
                try {
                    if (_photo.value != null) {
                        _photo.value?.file?.let { file ->
                            repository.saveWithAttachment(post, MediaUpload(file))
                        }
                    } else {
                        repository.save(post)
                    }
                    clearEditing()
                } catch (e: Exception) {
                    _state.value = _state.value?.copy(error = true)
                    Log.e("PostViewModel", "Save error: ${e.message}", e)
                }
            }
        }
    }

    fun edit(post: Post) {
        if (!isAuthenticated()) {
            _state.value = _state.value?.copy(error = true)
            return
        }
        edited.value = post
    }

    fun changeContent(content: String) {
        val text = content.trim()
        if (edited.value?.content == text) {
            return
        }
        edited.value = edited.value?.copy(content = text)
    }

    fun likeById(id: Long) {
        if (!isAuthenticated()) {
            _state.value = _state.value?.copy(error = true)
            return
        }

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
        if (!isAuthenticated()) {
            _state.value = _state.value?.copy(error = true)
            return
        }

        viewModelScope.launch {
            try {
                repository.removeById(id)
            } catch (e: Exception) {
                Log.e("PostViewModel", "Remove error:", e)
                _state.value = _state.value?.copy(error = true)
            }
        }
    }

    @Suppress("unused")
    fun shareById(id: Long) {
        if (!isAuthenticated()) {
            _state.value = _state.value?.copy(error = true)
            return
        }

        viewModelScope.launch {
            try {
                repository.shareById(id)
            } catch (e: Exception) {
                Log.e("PostViewModel", "Share error:", e)
                _state.value = _state.value?.copy(error = true)
            }
        }
    }

    private fun isAuthenticated(): Boolean {
        return appAuth.authStateFlow.value.token != null
    }

    fun syncWithServer() {
        if (!isAuthenticated()) {
            Log.d("PostViewModel", "Not authorized, skipping sync")
            return
        }

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

    @Suppress("unused")
    fun clearError() {
        _state.value = _state.value?.copy(error = false, syncError = false)
    }

    fun changePhoto(uri: Uri?, file: File?) {
        _photo.value = PhotoModel(uri, file)
    }

    @Suppress("unused")
    fun removePhoto() {
        _photo.value = null
    }

    fun clearEditing() {
        edited.value = emptyPost
        _photo.value = null
    }

    companion object {
        val emptyPost = Post(
            id = 0L,
            author = "",
            authorId = 0,
            authorAvatar = "",
            content = "",
            published = 0,
            likedByMe = false,
            likes = 0,
            shares = 0,
            video = null,
            attachment = null
        )
    }
}