package ru.netology.nmedia.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.model.FeedModel
import ru.netology.nmedia.repository.PostRepository
import ru.netology.nmedia.repository.PostRepositoryImpl
import ru.netology.nmedia.util.SingleLiveEvent
import kotlin.concurrent.thread

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
    private val repository: PostRepository = PostRepositoryImpl()
    private val _data = MutableLiveData(FeedModel())
    val data: LiveData<FeedModel>
        get() = _data
    val edited = MutableLiveData(emptyTemplate)

    private val _postCreated = SingleLiveEvent<Unit>()
    val postCreated: LiveData<Unit>
        get( )= _postCreated

    init {
        load()
    }

    fun load() {
        thread {
            _data.postValue(FeedModel(loading = true))

           val result: FeedModel = try {
                val posts: List<Post> = repository.getAll()

                FeedModel(posts = posts, empty = posts.isEmpty())
            } catch (_: Exception) {
                FeedModel(error = true)
            }

            _data.postValue(result)



        }
    }

    // Метод для сохранения изменений
    fun save(content: String) {
        thread {
            edited.value?.let {
                val text: String = content.trim()
                if (it.content != text) {
                    repository.save(it.copy(content = text))
                    _postCreated.postValue(Unit)
                }
            }
            edited.postValue( emptyTemplate)
        }


    }

    // Запускает режим редактирования выбранного поста
    fun edit(post: Post) {
        edited.value = post
    }

    // Лайкает выбранный пост
    fun likeById(id: Long) = viewModelScope.launch {
        try {
            val updatedPost = repository.likeById(id)
            _data.postValue(_data.value?.copy(
                posts = _data.value?.posts?.map { post ->
                    if (post.id == id) post.copy(likedByMe = true) else post
                } ?: _data.value?.posts ?: emptyList()
            ))
        } catch (e: Exception) {
            Log.e("PostViewModel", "Like failed: ${e.message}")
        }
    }

    // Удаляет выбранный пост
    fun removeById(id: Long) {
        repository.removeById(id)
    }

        fun shareById(id: Long) {
            repository.shareById(id)
        }


}