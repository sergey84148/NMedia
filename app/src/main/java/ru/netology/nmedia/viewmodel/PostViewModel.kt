package ru.netology.nmedia.viewmodel


import androidx.lifecycle.ViewModel
import androidx.lifecycle.MutableLiveData
import ru.netology.nmedia.repository.PostRepository
import ru.netology.nmedia.repository.PostRepositoryInMemoryImpl
import ru.netology.nmedia.dto.Post

class PostViewModel(
    private val repository: PostRepository = PostRepositoryInMemoryImpl()
) : ViewModel() {

    private val _data = MutableLiveData<List<Post>>()
    val data: MutableLiveData<List<Post>> = _data

    init {
        _data.value = repository.getAll().value ?: emptyList()
    }

    fun likeById(id: Long) {
        repository.likeById(id)
        _data.value = repository.getAll().value  // Обновляем данные
    }

    fun shareById(id: Long) {
        repository.shareById(id)
        _data.value = repository.getAll().value  // Обновляем данные
    }
}
