package ru.netology.nmedia.repository

import androidx.lifecycle.*
import ru.netology.nmedia.api.PostsApi
import ru.netology.nmedia.dao.PostDao
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.entity.PostEntity


class PostRepositoryImpl(
    private val dao: PostDao,
) : PostRepository {
    override val data: LiveData<List<Post>> = dao.getAll().map { posts ->
        posts.map { it.toDto() }
    }

    override suspend fun getAllAsync() {
        val posts: List<Post> = PostsApi.retrofitService.getAll()

        dao.insert(posts.map ( PostEntity::fromDto))
    }


    override suspend fun likeById(id: Long): Post =
        PostsApi.retrofitService.likeById(id)


    override suspend fun dislikeById(id: Long): Post =
        PostsApi.retrofitService.dislikeById(id)


    override suspend fun save(post: Post): Post =
        PostsApi.retrofitService.save(post)


    override suspend fun removeById(id: Long) =
        PostsApi.retrofitService.removeById(id)


    override suspend fun shareById(id: Long): Post =
        PostsApi.retrofitService.shareById(id)


}