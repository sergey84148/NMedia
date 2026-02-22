package ru.netology.nmedia.repository

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import ru.netology.nmedia.api.PostsApi
import ru.netology.nmedia.dto.Post
import java.lang.RuntimeException

class PostRepositoryImpl : PostRepository {
    override fun getAll(): List<Post> =
        PostsApi.service.getAll()
            .execute()
            .body()
            .orEmpty()

    override fun getAllAsync(callback: PostRepository.GetAllCallback) {
        PostsApi.service.getAll()
            .enqueue(object : Callback<List<Post>> {
                override fun onResponse(
                    call: retrofit2.Call<List<Post>>,
                    response: Response<List<Post>>
                ) {
                    if (response.isSuccessful) {
                        callback.onSuccess(response.body().orEmpty())
                    } else {
                        callback.onError(
                            RuntimeException(response.errorBody()?.string().orEmpty())
                        )
                    }
                }

                override fun onFailure(call: Call<List<Post>>?, t: Throwable) {
                    callback.onError(t)
                }
            })
    }

    override fun save(post: Post): Post {
        return PostsApi.service.save(post)
            .execute()
            .body() ?: throw RuntimeException("Failed to save post")
    }

    override fun removeById(id: Long) {
        PostsApi.service.removeById(id)
            .execute()
    }

    override fun likeById(id: Long): Post {
        return PostsApi.service.likeById(id)
            .execute()
            .body() ?: throw RuntimeException("Failed to like post")
    }

    override fun dislikeById(id: Long): Post {
        return PostsApi.service.dislikeById(id)
            .execute()
            .body() ?: throw RuntimeException("Failed to dislike post")
    }

    override fun shareById(id: Long): Post {
        // Для реализации share нужно добавить соответствующий API-метод
        throw NotImplementedError("Share functionality not implemented")
    }
}
